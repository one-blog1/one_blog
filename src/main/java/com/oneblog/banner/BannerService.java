package com.oneblog.banner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.BlogQueryService;
import com.oneblog.blog.dto.BlogListItem;
import com.oneblog.file.StoredFile;
import com.oneblog.file.StoredFileRepository;
import com.oneblog.post.dto.PostCard;
import com.oneblog.search.FeedService;

/**
 * 메인 배너에 보여 줄 장 (BRD-12, 6.8, D-132, D-133).
 * 자동: 인기 글·새 블로그·인기 블로그, 관리자: 광고·공지·이벤트(관리자가 정한 순서).
 * 종류를 하나씩 번갈아 놓는다: 인기 글 1 → 관리자 1 → 새 블로그 1 → 인기 블로그 1 → 인기 글 2 → … 남은 것은 뒤에.
 */
@Service
public class BannerService {

    private static final int DESCRIPTION_MAX = 60;

    private final BannerRepository bannerRepository;
    private final StoredFileRepository fileRepository;
    private final SiteSettings siteSettings;
    private final FeedService feedService;
    private final BannerBlogQueries blogQueries;
    private final BlogQueryService blogQueryService;

    public BannerService(BannerRepository bannerRepository, StoredFileRepository fileRepository,
            SiteSettings siteSettings, FeedService feedService, BannerBlogQueries blogQueries,
            BlogQueryService blogQueryService) {
        this.bannerRepository = bannerRepository;
        this.fileRepository = fileRepository;
        this.siteSettings = siteSettings;
        this.feedService = feedService;
        this.blogQueries = blogQueries;
        this.blogQueryService = blogQueryService;
    }

    @Transactional(readOnly = true)
    public List<BannerSlide> slides(Long viewerId) {
        BannerSettings settings = BannerSettings.read(siteSettings);
        Auto auto = auto(settings, viewerId);
        return interleave(List.of(auto.popular, manual(), auto.newBlogs, auto.popularBlogs));
    }

    record Auto(List<BannerSlide> popular, List<BannerSlide> newBlogs, List<BannerSlide> popularBlogs) {
    }

    /** 자동 종류 세 가지. 새 블로그로 나온 블로그는 인기 블로그에서 빼서 같은 블로그가 두 번 나오지 않게 한다. */
    Auto auto(BannerSettings settings, Long viewerId) {
        List<PostCard> cards = feedService.popularWithThumbnail(settings.popular().count(), settings.popular().days(),
                viewerId);
        String label = BannerSettings.popularLabel(settings.popular().days());
        List<BannerSlide> popular = cards.stream().map(c -> new BannerSlide("POPULAR", label, c.title(),
                c.blogName() + " · 좋아요 " + c.likeCount(), c.thumbnailUrl(),
                "/blog/" + c.blogSlug() + "/posts/" + c.id(), false, c.blogSlug(), c.blogName(), 50, 50)).toList();

        List<BannerSlide> newBlogs = blogSlides("NEW_BLOG", "새 블로그",
                blogQueries.newBlogs(settings.newBlog().count(), settings.newBlog().days()), Set.of());
        Set<String> shown = new HashSet<>();
        newBlogs.forEach(s -> shown.add(s.blogSlug()));
        // 겹치는 블로그를 뺀 뒤에도 개수를 채우도록 넉넉히 읽는다
        List<Long> popularIds = blogQueries.popularBlogs(
                settings.popularBlog().count() == 0 ? 0 : settings.popularBlog().count() + newBlogs.size(),
                settings.popularBlog().days());
        List<BannerSlide> popularBlogs = blogSlides("POPULAR_BLOG", "인기 블로그", popularIds, shown).stream()
                .limit(settings.popularBlog().count()).toList();
        return new Auto(popular, newBlogs, popularBlogs);
    }

    private List<BannerSlide> blogSlides(String kind, String label, List<Long> ids, Set<String> skip) {
        List<BannerSlide> result = new ArrayList<>();
        for (BlogListItem blog : blogQueryService.toListItems(ids)) {
            if (blog.slug() == null || skip.contains(blog.slug())) {
                continue;
            }
            String image = blog.coverImageUrl() != null && blog.coverImageUrl().startsWith("/files/")
                    ? blog.coverImageUrl() : null;
            result.add(new BannerSlide(kind, label, blog.name(), blogCaption(blog), image, "/blog/" + blog.slug(),
                    false, blog.slug(), blog.name(), 50, 50));
        }
        return result;
    }

    private static String blogCaption(BlogListItem blog) {
        String members = "멤버 " + blog.memberCount() + "명";
        String description = blog.description() == null ? "" : blog.description().strip().replaceAll("\\s+", " ");
        if (description.isEmpty()) {
            return members;
        }
        if (description.length() > DESCRIPTION_MAX) {
            description = description.substring(0, DESCRIPTION_MAX) + "…";
        }
        return members + " · " + description;
    }

    private List<BannerSlide> manual() {
        List<Banner> banners = bannerRepository.findVisible(PageRequest.of(0, BannerSettings.MAX_MANUAL));
        Map<Long, StoredFile> files = images(banners);
        List<BannerSlide> result = new ArrayList<>();
        for (Banner b : banners) {
            StoredFile image = files.get(b.getImageFileId());
            if (image == null || image.isDeleted()) {
                continue;
            }
            result.add(new BannerSlide(b.getKind().name(), b.getKind().label(), b.getTitle(), b.getCaption(),
                    image.url(), b.getLinkUrl(), BannerLinks.isExternal(b.getLinkUrl()), null, null,
                    b.getImagePosX(), b.getImagePosY()));
        }
        return result;
    }

    /** 목록들을 하나씩 번갈아 이어 붙인다. */
    static List<BannerSlide> interleave(List<List<BannerSlide>> groups) {
        List<BannerSlide> result = new ArrayList<>();
        int longest = groups.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < longest; i++) {
            for (List<BannerSlide> group : groups) {
                if (i < group.size()) {
                    result.add(group.get(i));
                }
            }
        }
        return result;
    }

    Map<Long, StoredFile> images(List<Banner> banners) {
        Map<Long, StoredFile> files = new HashMap<>();
        fileRepository.findAllById(banners.stream().map(Banner::getImageFileId).toList())
                .forEach(f -> files.put(f.getId(), f));
        return files;
    }
}
