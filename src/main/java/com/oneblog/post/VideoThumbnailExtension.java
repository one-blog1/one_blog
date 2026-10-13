package com.oneblog.post;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.oneblog.blog.Blog;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.file.StoredFileRepository;
import com.oneblog.post.dto.PostRequest;

/**
 * 글 목록 영상 썸네일 (D-128).
 * - 저장한 뒤: 글 트랜잭션이 끝나면(커밋 뒤) VideoThumbnailService가 썸네일을 맞춘다. 영상 사이트에 묻는 동안 DB를 잡지 않게
 * - 목록: 영상 썸네일이 있는 글(본문 첫 미디어가 영상)은 그것을 썸네일로, 어느 사이트 영상인지(youtube·chzzk·soop)도 준다.
 *   사진 썸네일(PostImageExtension, putIfAbsent)보다 앞선다
 * 지운 글의 썸네일은 PostImageExtension.detachAll이 같이 지운 것으로 표시한다.
 */
@Component
public class VideoThumbnailExtension implements PostExtension {

    private final VideoThumbnailService thumbnailService;
    private final StoredFileRepository fileRepository;

    public VideoThumbnailExtension(VideoThumbnailService thumbnailService, StoredFileRepository fileRepository) {
        this.thumbnailService = thumbnailService;
        this.fileRepository = fileRepository;
    }

    @Override
    public void afterSave(Blog blog, Post post, PostRequest request, AuthenticatedUser principal) {
        Long postId = post.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    thumbnailService.refresh(postId);
                }
            });
        } else {
            thumbnailService.refresh(postId);
        }
    }

    @Override
    public void describeList(Blog blog, PostListContext context) {
        if (context.posts.isEmpty()) {
            return;
        }
        // 최신 썸네일이 먼저 오므로 글마다 처음 것만
        for (Object[] row : fileRepository.findVideoThumbRows(context.ids())) {
            Long postId = (Long) row[0];
            if (!context.videoThumbnails.containsKey(postId)) {
                context.thumbnails.put(postId, "/files/" + row[1]);
                context.videoThumbnails.put(postId, provider((String) row[2]));
            }
        }
    }

    /** 영상 구분(youtube:…, chzzk-clip:…, chzzk-video:…, soop:…)에서 사이트 이름만. */
    static String provider(String key) {
        if (key == null) {
            return "video";
        }
        if (key.startsWith("youtube:")) {
            return "youtube";
        }
        if (key.startsWith("chzzk")) {
            return "chzzk";
        }
        if (key.startsWith("soop:")) {
            return "soop";
        }
        return "video";
    }
}
