package com.oneblog.post;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.oneblog.common.text.MarkdownRenderer;
import com.oneblog.common.text.VideoEmbeds;
import com.oneblog.file.FilePurpose;
import com.oneblog.file.FileStorage;
import com.oneblog.file.ImageMetadataStripper;
import com.oneblog.file.ImageType;
import com.oneblog.file.ImageTypeDetector;
import com.oneblog.file.StoredFile;
import com.oneblog.file.StoredFileRepository;

/**
 * 글 목록 영상 썸네일 (BRD-02, 6.3, D-128).
 * 본문에서 처음 나오는 사진·영상이 영상 링크(유튜브·치지직·SOOP)면 그 썸네일을 서버가 받아 files(VIDEO_THUMB)로 글에 연결한다.
 * 읽는 사람의 브라우저는 우리 서버의 사진만 받는다(영상 사이트로 IP가 가지 않고, 보안 헤더 img-src 'self' 그대로).
 * - 글을 저장한 뒤(커밋 뒤) 한 번 부른다. 같은 영상이면 다시 받지 않는다(original_name에 영상 구분을 둔다)
 * - 영상이 바뀌거나 빠지면 예전 썸네일은 지운 것으로 표시한다(30일 뒤 04:00 배치가 지움, 4.5)
 * - 받은 사진도 글 사진처럼 앞부분으로 형식을 확인하고(jpg·png·gif·webp) 위치 정보를 지운다
 * - 영상 사이트에 묻는 동안은 DB 트랜잭션을 잡지 않는다
 */
@Service
public class VideoThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(VideoThumbnailService.class);

    private final PostRepository postRepository;
    private final StoredFileRepository fileRepository;
    private final FileStorage storage;
    private final VideoThumbnailFetcher fetcher;
    private final MarkdownRenderer renderer;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    public VideoThumbnailService(PostRepository postRepository, StoredFileRepository fileRepository,
            FileStorage storage, VideoThumbnailFetcher fetcher, MarkdownRenderer renderer,
            PlatformTransactionManager transactionManager) {
        this.postRepository = postRepository;
        this.fileRepository = fileRepository;
        this.storage = storage;
        this.fetcher = fetcher;
        this.renderer = renderer;
        // 글 저장 트랜잭션이 끝난 뒤(afterCommit)에 불리므로 새 트랜잭션으로 연다
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private record Plan(Long userId, String key, boolean keep) {
    }

    /** 이 글의 영상 썸네일을 본문에 맞춘다. 실패해도 예외를 밖으로 내지 않는다 (글 저장은 이미 끝났음). */
    public void refresh(Long postId) {
        try {
            if (!(readTx.execute(status -> plan(postId)) instanceof Plan plan) || plan.keep()) {
                return;
            }
            if (plan.key() == null) {
                writeTx.executeWithoutResult(status -> markOld(postId, null));
                return;
            }
            Optional<Thumbnail> thumbnail = fetcher.fetch(plan.key()).flatMap(VideoThumbnailService::validate);
            writeTx.executeWithoutResult(status -> {
                markOld(postId, null);
                thumbnail.ifPresent(t -> save(postId, plan, t));
            });
        } catch (RuntimeException e) {
            log.warn("글 {}의 영상 썸네일을 맞추지 못했습니다.", postId, e);
        }
    }

    private Plan plan(Long postId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null || post.isDeleted()) {
            return null;
        }
        String key = renderer.leadingVideo(post.getContent()).map(VideoEmbeds.Embed::key).orElse(null);
        List<StoredFile> current = fileRepository.findAttached(postId, FilePurpose.VIDEO_THUMB);
        boolean same = key != null && current.size() == 1 && key.equals(current.get(0).getOriginalName());
        boolean nothing = key == null && current.isEmpty();
        return new Plan(post.getUserId(), key, same || nothing);
    }

    /** keepKey가 아닌 이 글의 영상 썸네일을 지운 것으로 표시한다. */
    private void markOld(Long postId, String keepKey) {
        for (StoredFile f : fileRepository.findAttached(postId, FilePurpose.VIDEO_THUMB)) {
            if (keepKey == null || !keepKey.equals(f.getOriginalName())) {
                f.markDeleted();
            }
        }
    }

    private void save(Long postId, Plan plan, Thumbnail thumbnail) {
        String storedName = UUID.randomUUID() + "." + thumbnail.type().extension();
        try {
            storage.store(storedName, thumbnail.data());
        } catch (IOException e) {
            log.warn("영상 썸네일을 저장하지 못했습니다: 글 {}", postId, e);
            return;
        }
        StoredFile file = StoredFile.create(plan.userId(), FilePurpose.VIDEO_THUMB, storedName, plan.key(),
                thumbnail.type().mimeType(), thumbnail.data().length);
        file.attachTo(postId, 0);
        fileRepository.save(file);
    }

    /** 확인을 마친 썸네일 바이트와 형식. 배열은 내용으로 비교한다 (S6218). */
    record Thumbnail(byte[] data, ImageType type) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Thumbnail(byte[] otherData, ImageType otherType)
                    && type == otherType && Arrays.equals(data, otherData);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(data) + Objects.hashCode(type);
        }

        @Override
        public String toString() {
            return "Thumbnail[type=" + type + ", data=" + Arrays.toString(data) + "]";
        }
    }

    /** 받은 바이트가 jpg·png·gif·webp 사진인지 앞부분으로 확인하고 위치 정보를 지운다. 아니면 빈 값. */
    static Optional<Thumbnail> validate(byte[] data) {
        if (data == null || data.length == 0 || data.length > HttpVideoThumbnailFetcher.MAX_BYTES) {
            return Optional.empty();
        }
        ImageType type = ImageTypeDetector.detect(data);
        if (type == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Thumbnail(ImageMetadataStripper.strip(type, data), type));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
