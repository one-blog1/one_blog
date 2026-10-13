package com.oneblog.post;

import java.util.Optional;

/**
 * 영상 링크의 썸네일 사진 바이트를 영상 사이트에서 받는다 (D-128).
 * key는 VideoEmbeds.Embed.key()(youtube:번호, chzzk-clip:번호, chzzk-video:번호, soop:번호).
 * 받지 못하면(사이트 오류, 시간 초과, 허용하지 않은 주소) 빈 값. 형식 검사와 저장은 VideoThumbnailService가 한다.
 */
public interface VideoThumbnailFetcher {

    Optional<byte[]> fetch(String key);
}
