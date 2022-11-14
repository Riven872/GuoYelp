package com.Guo.GuoYelp.controller;


import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 好友关注模块
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    /**
     * 关注和取关
     *
     * @param id       要关注/要取关的用户id
     * @param isFollow true：关注 false：取关
     * @return 标准返回
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("isFollow") boolean isFollow) {
        return followService.follow(id, isFollow);
    }

    /**
     * 判断是否已经关注当前用户
     *
     * @param id
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id) {
        return followService.isFollow(id);
    }

    /**
     * 查看共同关注
     * @param id
     * @return
     */
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        return followService.followCommons(id);
    }
}
