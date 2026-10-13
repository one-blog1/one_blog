package com.oneblog.banner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.admin.AdminActionLogger;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.file.FilePurpose;
import com.oneblog.file.StoredFile;
import com.oneblog.file.StoredFileRepository;
import com.oneblog.member.ValidationFailedException;

import jakarta.servlet.http.HttpServletRequest;

/** 관리자 배너 관리 (ADM-08, 6.8, D-132, D-133): 광고·공지·이벤트와 자동 종류의 개수·기간. 모든 조치는 활동 기록에 남는다 (ADM-06). */
@Service
public class BannerAdminService {

    static final int TITLE_MAX = 100;
    static final int CAPTION_MAX = 100;

    private final BannerRepository bannerRepository;
    private final StoredFileRepository fileRepository;
    private final SiteSettings siteSettings;
    private final BannerService bannerService;
    private final AdminActionLogger actionLogger;

    public BannerAdminService(BannerRepository bannerRepository, StoredFileRepository fileRepository,
            SiteSettings siteSettings, BannerService bannerService, AdminActionLogger actionLogger) {
        this.bannerRepository = bannerRepository;
        this.fileRepository = fileRepository;
        this.siteSettings = siteSettings;
        this.bannerService = bannerService;
        this.actionLogger = actionLogger;
    }

    /** kind: AD·NOTICE·EVENT(빼면 AD). imageFileId는 POST /api/admin/banners/image로 올린 사진. visible을 빼면 켬. */
    /** imagePosX·imagePosY: 사진에서 보일 자리 0~100(%), 빼면 50 (D-134). */
    public record BannerRequest(String kind, String title, String caption, String linkUrl, Long imageFileId,
            Boolean visible, Integer imagePosX, Integer imagePosY) {
    }

    public record BannerRow(Long id, String kind, String kindLabel, String title, String caption, String linkUrl,
            Long imageFileId, String imageUrl, int imagePosX, int imagePosY, boolean visible, int sortOrder) {
    }

    /**
     * 관리자 배너 탭 한 번에: 관리자 배너 목록, 자동 종류 설정, 지금 나갈 자동 배너 미리보기(종류별),
     * 관리자 배너가 몇 개까지 나가는지.
     */
    public record Overview(List<BannerRow> banners, BannerSettings settings, AutoPreview autoPreview,
            int maxVisibleBanners) {
    }

    public record AutoPreview(List<BannerSlide> popular, List<BannerSlide> newBlog, List<BannerSlide> popularBlog) {
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        List<Banner> banners = bannerRepository.findAllOrdered();
        Map<Long, StoredFile> files = bannerService.images(banners);
        List<BannerRow> rows = banners.stream().map(b -> {
            StoredFile image = files.get(b.getImageFileId());
            return new BannerRow(b.getId(), b.getKind().name(), b.getKind().label(), b.getTitle(), b.getCaption(),
                    b.getLinkUrl(), b.getImageFileId(), image == null || image.isDeleted() ? null : image.url(),
                    b.getImagePosX(), b.getImagePosY(), b.isVisible(), b.getSortOrder());
        }).toList();
        BannerSettings settings = BannerSettings.read(siteSettings);
        BannerService.Auto auto = bannerService.auto(settings, null);
        return new Overview(rows, settings, new AutoPreview(auto.popular(), auto.newBlogs(), auto.popularBlogs()),
                BannerSettings.MAX_MANUAL);
    }

    @Transactional
    public Banner create(BannerRequest body, AuthenticatedUser admin, HttpServletRequest request) {
        Clean clean = validate(body, null);
        Banner banner = bannerRepository.saveAndFlush(Banner.create(admin.id(), clean.kind, clean.imageFileId,
                clean.title, clean.caption, clean.linkUrl, clean.visible, nextSortOrder()));
        banner.positionImage(clean.posX, clean.posY);
        actionLogger.log(admin, "BANNER_CREATE", "BANNER", banner.getId(), clean.kind.label() + ": " + clean.title,
                request);
        return banner;
    }

    @Transactional
    public void update(Long id, BannerRequest body, AuthenticatedUser admin, HttpServletRequest request) {
        Banner banner = find(id);
        Clean clean = validate(body, banner.getImageFileId());
        Long oldImage = banner.getImageFileId();
        banner.change(clean.kind, clean.imageFileId, clean.title, clean.caption, clean.linkUrl, clean.visible);
        banner.positionImage(clean.posX, clean.posY);
        if (!oldImage.equals(clean.imageFileId)) {
            fileRepository.findById(oldImage).ifPresent(StoredFile::markDeleted);
        }
        actionLogger.log(admin, "BANNER_UPDATE", "BANNER", id,
                clean.kind.label() + ": " + clean.title + (clean.visible ? "" : " (노출 끔)"), request);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser admin, HttpServletRequest request) {
        Banner banner = find(id);
        Long image = banner.getImageFileId();
        String title = banner.getTitle();
        bannerRepository.delete(banner);
        bannerRepository.flush();
        fileRepository.findById(image).ifPresent(StoredFile::markDeleted);
        actionLogger.log(admin, "BANNER_DELETE", "BANNER", id, title, request);
    }

    /** direction: up 또는 down. 한 칸 옮기고 순서를 0부터 다시 매긴다. */
    @Transactional
    public void move(Long id, String direction, AuthenticatedUser admin, HttpServletRequest request) {
        int step = switch (direction == null ? "" : direction) {
            case "up" -> -1;
            case "down" -> 1;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DIRECTION", "up 또는 down으로 옮겨 주세요.");
        };
        List<Banner> banners = new ArrayList<>(bannerRepository.findAllOrdered());
        int index = -1;
        for (int i = 0; i < banners.size(); i++) {
            if (banners.get(i).getId().equals(id)) {
                index = i;
            }
        }
        if (index < 0) {
            throw notFound();
        }
        int target = index + step;
        if (target >= 0 && target < banners.size()) {
            banners.add(target, banners.remove(index));
        }
        for (int i = 0; i < banners.size(); i++) {
            banners.get(i).moveTo(i);
        }
        actionLogger.log(admin, "BANNER_MOVE", "BANNER", id, step < 0 ? "위로" : "아래로", request);
    }

    /** 자동 종류 설정. 빠진 종류(null)는 그대로 둔다. */
    @Transactional
    public BannerSettings updateSettings(BannerSettings.Auto popular, BannerSettings.Auto newBlog,
            BannerSettings.Auto popularBlog, AuthenticatedUser admin, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        check("popular", popular, errors);
        check("newBlog", newBlog, errors);
        check("popularBlog", popularBlog, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        BannerSettings current = BannerSettings.read(siteSettings);
        BannerSettings next = new BannerSettings(popular == null ? current.popular() : popular,
                newBlog == null ? current.newBlog() : newBlog, popularBlog == null ? current.popularBlog() : popularBlog);
        BannerSettings.write(siteSettings, BannerSettings.POPULAR, next.popular());
        BannerSettings.write(siteSettings, BannerSettings.NEW_BLOG, next.newBlog());
        BannerSettings.write(siteSettings, BannerSettings.POPULAR_BLOG, next.popularBlog());
        actionLogger.log(admin, "BANNER_SETTINGS", "BANNER", 0L, "인기 글 " + describe(next.popular())
                + ", 새 블로그 " + describe(next.newBlog()) + ", 인기 블로그 " + describe(next.popularBlog()), request);
        return next;
    }

    private static String describe(BannerSettings.Auto auto) {
        return auto.count() + "개/" + auto.days() + "일";
    }

    private static void check(String field, BannerSettings.Auto auto, List<ErrorResponse.FieldError> errors) {
        if (auto == null) {
            return;
        }
        if (!BannerSettings.validCount(auto.count())) {
            errors.add(new ErrorResponse.FieldError(field + ".count", "개수는 0~" + BannerSettings.MAX_COUNT + "개로 골라 주세요."));
        }
        if (!BannerSettings.valid(auto.days())) {
            errors.add(new ErrorResponse.FieldError(field + ".days", "기간은 1일, 7일, 30일 중에서 골라 주세요."));
        }
    }

    private record Clean(BannerKind kind, String title, String caption, String linkUrl, Long imageFileId,
            boolean visible, int posX, int posY) {
    }

    /** currentImage: 고칠 때 지금 사진(그대로 두면 다시 검사하지 않아도 됨). */
    private Clean validate(BannerRequest body, Long currentImage) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        BannerKind kind = BannerKind.parse(body == null ? null : body.kind());
        if (kind == null) {
            errors.add(new ErrorResponse.FieldError("kind", "종류는 광고, 공지, 이벤트 중에서 골라 주세요."));
        }
        String title = body == null || body.title() == null ? "" : body.title().strip();
        if (title.isEmpty() || title.length() > TITLE_MAX) {
            errors.add(new ErrorResponse.FieldError("title", "제목은 1~" + TITLE_MAX + "자로 적어 주세요."));
        }
        String caption = body == null || body.caption() == null ? null : body.caption().strip();
        if (caption != null && caption.isEmpty()) {
            caption = null;
        }
        if (caption != null && caption.length() > CAPTION_MAX) {
            errors.add(new ErrorResponse.FieldError("caption", "작은 글씨는 " + CAPTION_MAX + "자까지 적어 주세요."));
        }
        String link = body == null || body.linkUrl() == null ? "" : body.linkUrl().strip();
        if (!BannerLinks.isValid(link)) {
            errors.add(new ErrorResponse.FieldError("linkUrl",
                    "링크는 https://로 시작하는 주소나 /로 시작하는 우리 사이트 주소로 적어 주세요."));
        }
        Long imageFileId = body == null ? null : body.imageFileId();
        if (imageFileId == null || !(imageFileId.equals(currentImage) || usableImage(imageFileId))) {
            errors.add(new ErrorResponse.FieldError("imageFileId", "배너 사진을 올려 주세요."));
        }
        int posX = position(body == null ? null : body.imagePosX(), "imagePosX", errors);
        int posY = position(body == null ? null : body.imagePosY(), "imagePosY", errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return new Clean(kind, title, caption, link, imageFileId, body.visible() == null || body.visible(), posX, posY);
    }

    /** 사진 위치 0~100(%). 빼면 가운데 50. */
    private static int position(Integer value, String field, List<ErrorResponse.FieldError> errors) {
        if (value == null) {
            return 50;
        }
        if (value < 0 || value > 100) {
            errors.add(new ErrorResponse.FieldError(field, "사진 위치는 0~100 사이여야 합니다."));
            return 50;
        }
        return value;
    }

    /** 배너용으로 올렸고, 지워지지 않았고, 다른 광고가 쓰지 않는 사진. */
    private boolean usableImage(Long fileId) {
        StoredFile file = fileRepository.findById(fileId).orElse(null);
        return file != null && !file.isDeleted() && file.getPurpose() == FilePurpose.BANNER
                && bannerRepository.findAllOrdered().stream().noneMatch(b -> b.getImageFileId().equals(fileId));
    }

    /** 새 광고는 맨 뒤. */
    private int nextSortOrder() {
        return bannerRepository.findAllOrdered().stream().mapToInt(Banner::getSortOrder).max().orElse(-1) + 1;
    }

    private Banner find(Long id) {
        return bannerRepository.findById(id).orElseThrow(BannerAdminService::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "BANNER_NOT_FOUND", "배너를 찾을 수 없습니다.");
    }
}
