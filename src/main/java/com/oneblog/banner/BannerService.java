package com.oneblog.banner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.file.StoredFile;
import com.oneblog.file.StoredFileRepository;
import com.oneblog.post.dto.PostCard;
import com.oneblog.search.FeedService;

/**
 * 메인 배너에 보여 줄 장 (BRD-12, 6.8, D-132). 인기 글(자동)과 광고(관리자)를 번갈아 놓는다:
 * 인기 1 → 광고 1 → 인기 2 → 광고 2 … 남은 것은 뒤에.
 */
@Service
public class BannerService {

    private final BannerRepository bannerRepository;
    private final StoredFileRepository fileRepository;
    private final SiteSettings siteSettings;
    private final FeedService feedService;

    public BannerService(BannerRepository bannerRepository, StoredFileRepository fileRepository,
            SiteSettings siteSettings, FeedService feedService) {
        this.bannerRepository = bannerRepository;
        this.fileRepository = fileRepository;
        this.siteSettings = siteSettings;
        this.feedService = feedService;
    }

    @Transactional(readOnly = true)
    public List<BannerSlide> slides(Long viewerId) {
        BannerSettings settings = BannerSettings.read(siteSettings);
        List<BannerSlide> popular = popular(settings, viewerId);
        List<BannerSlide> ads = ads();
        List<BannerSlide> result = new ArrayList<>(popular.size() + ads.size());
        for (int i = 0; i < Math.max(popular.size(), ads.size()); i++) {
            if (i < popular.size()) {
                result.add(popular.get(i));
            }
            if (i < ads.size()) {
                result.add(ads.get(i));
            }
        }
        return result;
    }

    List<BannerSlide> popular(BannerSettings settings, Long viewerId) {
        List<PostCard> cards = feedService.popularWithThumbnail(settings.popularCount(), settings.popularDays(), viewerId);
        String label = settings.popularLabel();
        return cards.stream().map(c -> new BannerSlide("POPULAR", label, c.title(),
                c.blogName() + " · 좋아요 " + c.likeCount(), c.thumbnailUrl(),
                "/blog/" + c.blogSlug() + "/posts/" + c.id(), false)).toList();
    }

    private List<BannerSlide> ads() {
        List<Banner> banners = bannerRepository.findVisible(PageRequest.of(0, BannerSettings.MAX_ADS));
        Map<Long, StoredFile> files = images(banners);
        List<BannerSlide> result = new ArrayList<>();
        for (Banner b : banners) {
            StoredFile image = files.get(b.getImageFileId());
            if (image == null || image.isDeleted()) {
                continue;
            }
            result.add(new BannerSlide("AD", "광고", b.getTitle(), b.getCaption(), image.url(), b.getLinkUrl(),
                    BannerLinks.isExternal(b.getLinkUrl())));
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
