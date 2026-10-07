package com.oneblog.block;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.common.security.AuthenticatedUser;

/** 차단 API (SOC-05). 누를 때마다 차단과 해제를 오간다. */
@RestController
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @PostMapping("/api/users/{nickname}/block")
    public BlockService.BlockState toggle(@PathVariable("nickname") String nickname,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return blockService.toggle(principal, nickname);
    }

    @GetMapping("/api/me/blocks")
    public List<BlockService.BlockedUser> mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return blockService.myBlocks(principal.id());
    }
}
