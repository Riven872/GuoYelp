package com.Guo.GuoYelp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.dto.UserDTO;
import com.Guo.GuoYelp.entity.Blog;
import com.Guo.GuoYelp.entity.User;
import com.Guo.GuoYelp.service.IBlogService;
import com.Guo.GuoYelp.service.IUserService;
import com.Guo.GuoYelp.utils.SystemConstants;
import com.Guo.GuoYelp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 探店模块
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    /**
     * 发布探店笔记
     *
     * @param blog
     * @return
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 查询探店笔记详情
     *
     * @param id 探店笔记id
     * @return
     */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    /**
     * 对指定的blog点赞
     *
     * @param id
     * @return
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        //// 修改点赞数量
        //blogService.update()
        //        .setSql("liked = liked + 1").eq("id", id).update();
        //return Result.ok();

        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 查看选中blog下的点赞排行榜
     *
     * @param id
     * @return
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }

    /**
     * 分页查询用户主页中的笔记
     *
     * @param current 页码
     * @param id      用户id
     * @return
     */
    @GetMapping("/of/user")
    public Result queryUserBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        return blogService.queryUserBlogByUserId(current, id);
    }

    /**
     * 滚动分页显示收件箱中的所有笔记
     *
     * @param max    当前时间戳或上一次查询的最小时间戳作为下一次查询的最大值
     * @param offset 偏移量，跳过的元素个数，第一次查询时没有偏移量
     * @return
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return blogService.queryBlogOfFollow(max, offset);
    }
}
