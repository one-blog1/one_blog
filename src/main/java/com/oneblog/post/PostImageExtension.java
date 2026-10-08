package com.oneblog.post;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.oneblog.blog.Blog;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.file.FilePurpose;
import com.oneblog.file.StoredFile;
import com.oneblog.file.StoredFileRepository;
import com.oneblog.member.ValidationFailedException;
import com.oneblog.post.dto.PostRequest;

/**
 * 글 이미지 연결 (BRD-05, 6.3, specs/005-post-images).
 * 본문 마크다운에 들어 있는 /files/{저장 이름}을 읽어, 작성자가 올린 글 이미지를 그 글에 연결한다.
 * - 글 하나에 10장까지 (6.3)
 * - 남이 올린 이미지나 다른 글에 이미 연결된 이미지는 연결하지 않는다 (본문에 주소가 남아 있어도 이 글의 것이 아님)
 * - 고칠 때 본문에서 빠진 이미지는 지운 것으로 표시한다 (4.5 30일 보관, 012 배치가 완전 삭제)
 * - 목록 썸네일은 글의 첫 이미지 (6.3)
 */
@Component
public class PostImageExtension implements PostExtension {

    public static final int MAX_IMAGES = 10;
    private static final Pattern IMAGE_URL = Pattern.compile(
            "/files/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:jpg|jpeg|png|gif|webp))");

    private final StoredFileRepository fileRepository;

    public PostImageExtension(StoredFileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    /** 본문에 나온 순서대로, 중복 없이. */
    static List<String> storedNames(String markdown) {
        Set<String> names = new LinkedHashSet<>();
        if (markdown != null) {
            Matcher m = IMAGE_URL.matcher(markdown);
            while (m.find()) {
                names.add(m.group(1));
            }
        }
        return new ArrayList<>(names);
    }

    @Override
    public void validate(Blog blog, PostRequest request) {
        if (storedNames(request.content()).size() > MAX_IMAGES) {
            throw new ValidationFailedException(List.of(
                    new ErrorResponse.FieldError("content", "이미지는 글 하나에 " + MAX_IMAGES + "장까지 넣을 수 있습니다.")));
        }
    }

    @Override
    public void afterSave(Blog blog, Post post, PostRequest request, AuthenticatedUser principal) {
        List<String> names = storedNames(post.getContent());
        Map<String, StoredFile> files = new HashMap<>();
        for (StoredFile f : fileRepository.findByStoredNameIn(names)) {
            files.put(f.getStoredName(), f);
        }
        Set<Long> kept = new HashSet<>();
        int order = 0;
        for (String name : names) {
            StoredFile f = files.get(name);
            if (f == null || f.isDeleted() || f.getPurpose() != FilePurpose.POST || !post.getUserId().equals(f.getUserId())
                    || (f.getPostId() != null && !f.getPostId().equals(post.getId()))) {
                continue;
            }
            f.attachTo(post.getId(), order++);
            kept.add(f.getId());
        }
        for (StoredFile f : fileRepository.findAttached(post.getId())) {
            if (!kept.contains(f.getId())) {
                f.markDeleted();
            }
        }
    }

    @Override
    public void afterDelete(Blog blog, Post post, AuthenticatedUser principal) {
        // 글을 지우면 이미지도 30일 보관 대상이 된다 (4.5)
        fileRepository.findAttached(post.getId()).forEach(StoredFile::markDeleted);
    }

    @Override
    public void describeList(Blog blog, PostListContext context) {
        if (context.posts.isEmpty()) {
            return;
        }
        for (Object[] row : fileRepository.findImageRows(context.ids(), FilePurpose.POST)) {
            context.thumbnails.putIfAbsent((Long) row[0], "/files/" + row[1]);
        }
    }
}
