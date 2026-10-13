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

/** 관리자 배너 관리 (ADM-08, 6.8, D-132). 모든 조치는 활동 기록에 남는다 (ADM-06). */
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

    /** imageFileId는 POST /api/admin/banners/image로 올린 사진. visible을 빼면 켬. */
    public record BannerRequest(String title, String caption, String linkUrl, Long imageFileId, Boolean visible) {
    }

    public record BannerRow(Long id, String title, String caption, String linkUrl, Long imageFileId, String imageUrl,
            boolean visible, int sortOrder) {
    }

    /** 관리자 배너 탭 한 번에: 광고 목록, 인기 글 설정, 지금 나갈 인기 글 미리보기, 광고가 몇 개까지 나가는지. */
    public record Overview(List<BannerRow> ads, BannerSettings settings, List<BannerSlide> popularPreview,
            int maxVisibleAds) {
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        List<Banner> banners = bannerRepository.findAllOrdered();
        Map<Long, StoredFile> files = bannerService.images(banners);
        List<BannerRow> rows = banners.stream().map(b -> {
            StoredFile image = files.get(b.getImageFileId());
            return new BannerRow(b.getId(), b.getTitle(), b.getCaption(), b.getLinkUrl(), b.getImageFileId(),
                    image == null || image.isDeleted() ? null : image.url(), b.isVisible(), b.getSortOrder());
        }).toList();
        BannerSettings settings = BannerSettings.read(siteSettings);
        return new Overview(rows, settings, bannerService.popular(settings, null), BannerSettings.MAX_ADS);
    }

    @Transactional
    public Banner create(BannerRequest body, AuthenticatedUser admin, HttpServletRequest request) {
        Clean clean = validate(body, null);
        Banner banner = bannerRepository.saveAndFlush(Banner.create(admin.id(), clean.imageFileId, clean.title,
                clean.caption, clean.linkUrl, clean.visible, nextSortOrder()));
        actionLogger.log(admin, "BANNER_CREATE", "BANNER", banner.getId(), clean.title, request);
        return banner;
    }

    @Transactional
    public void update(Long id, BannerRequest body, AuthenticatedUser admin, HttpServletRequest request) {
        Banner banner = find(id);
        Clean clean = validate(body, banner.getImageFileId());
        Long oldImage = banner.getImageFileId();
        banner.change(clean.imageFileId, clean.title, clean.caption, clean.linkUrl, clean.visible);
        if (!oldImage.equals(clean.imageFileId)) {
            fileRepository.findById(oldImage).ifPresent(StoredFile::markDeleted);
        }
        actionLogger.log(admin, "BANNER_UPDATE", "BANNER", id,
                clean.title + (clean.visible ? "" : " (노출 끔)"), request);
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

    @Transactional
    public BannerSettings updateSettings(Integer popularCount, Integer popularDays, AuthenticatedUser admin,
            HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        if (popularCount == null || popularCount < 0 || popularCount > BannerSettings.MAX_COUNT) {
            errors.add(new ErrorResponse.FieldError("popularCount", "인기 글 수는 0~" + BannerSettings.MAX_COUNT + "개로 골라 주세요."));
        }
        if (popularDays == null || !BannerSettings.DAYS.contains(popularDays)) {
            errors.add(new ErrorResponse.FieldError("popularDays", "기간은 1일, 7일, 30일 중에서 골라 주세요."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        siteSettings.put(BannerSettings.COUNT_KEY, String.valueOf(popularCount));
        siteSettings.put(BannerSettings.DAYS_KEY, String.valueOf(popularDays));
        actionLogger.log(admin, "BANNER_SETTINGS", "BANNER", 0L,
                "인기 글 " + popularCount + "개, 최근 " + popularDays + "일", request);
        return new BannerSettings(popularCount, popularDays);
    }

    private record Clean(String title, String caption, String linkUrl, Long imageFileId, boolean visible) {
    }

    /** currentImage: 고칠 때 지금 사진(그대로 두면 다시 검사하지 않아도 됨). */
    private Clean validate(BannerRequest body, Long currentImage) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
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
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return new Clean(title, caption, link, imageFileId, body.visible() == null || body.visible());
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
