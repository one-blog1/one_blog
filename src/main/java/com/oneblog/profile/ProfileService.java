package com.oneblog.profile;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogMemberRepository;
import com.oneblog.blog.BlogRole;
import com.oneblog.blog.BlogVisibility;
import com.oneblog.member.User;
import com.oneblog.social.FollowRepository;
import com.oneblog.social.FollowService;

/**
 * 프로필 (SOC-03): 닉네임·사진·소개, 운영하는 블로그, 참여한 블로그, 팔로워 수.
 * 다른 사람에게는 전체 공개이고 숨기지 않은 블로그만 보인다. 일부 공개·비공개 블로그는 이름도 드러내지 않는다.
 */
@Service
public class ProfileService {

    private final FollowService followService;
    private final FollowRepository followRepository;
    private final BlogMemberRepository memberRepository;
    private final com.oneblog.block.BlockRepository blockRepository;

    public ProfileService(FollowService followService, FollowRepository followRepository,
            BlogMemberRepository memberRepository, com.oneblog.block.BlockRepository blockRepository) {
        this.followService = followService;
        this.followRepository = followRepository;
        this.memberRepository = memberRepository;
        this.blockRepository = blockRepository;
    }

    public record BlogItem(String slug, String name, String coverImageUrl, BlogVisibility visibility, BlogRole role,
            int memberCount) {
    }

    /** blogsHidden·followsHidden: 본인이 숨겨서 남에게 안 보이는 것. followAllowed: 새 팔로우를 받는지 (D-114). */
    public record ProfileResponse(String nickname, String profileImageUrl, String bio, long followerCount,
            long followingCount, boolean following, boolean me, boolean blocked, List<BlogItem> ownedBlogs,
            List<BlogItem> joinedBlogs, boolean blogsHidden, boolean followsHidden, boolean followAllowed) {
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile(String nickname, Long viewerId) {
        User user = followService.findMember(nickname);
        boolean me = user.getId().equals(viewerId);
        List<BlogItem> owned = new ArrayList<>();
        List<BlogItem> joined = new ArrayList<>();
        boolean blogsHidden = !me && !user.isShowBlogsOnProfile();
        for (Object[] row : blogsHidden ? List.<Object[]>of() : memberRepository.findMyBlogs(user.getId())) {
            Blog blog = (Blog) row[0];
            BlogRole role = (BlogRole) row[1];
            if (!me && (blog.getVisibility() != BlogVisibility.PUBLIC || blog.isHidden())) {
                continue;
            }
            BlogItem item = new BlogItem(blog.getSlug(), blog.getName(), blog.getCoverImageUrl(), blog.getVisibility(),
                    role, blog.getMemberCount());
            (role == BlogRole.OWNER ? owned : joined).add(item);
        }
        return new ProfileResponse(user.getNickname(), user.getProfileImageUrl(), user.getBio(),
                followRepository.countFollowers(user.getId()), followRepository.countFollowing(user.getId()),
                followService.isFollowing(viewerId, user.getId()), me, blockRepository.exists(viewerId, user.getId()),
                owned, joined, blogsHidden, !me && !user.isShowFollowsOnProfile(), user.isAllowFollow());
    }
}
