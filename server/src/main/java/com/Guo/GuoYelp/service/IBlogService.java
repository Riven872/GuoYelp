package com.Guo.GuoYelp.service;

import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 探店模块
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询探店笔记详情
     *
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 分页查询blog
     *
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 点赞
     *
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 查看选中blog下的点赞排行榜
     *
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 查询用户主页中的笔记
     *
     * @param current
     * @param id
     * @return
     */
    Result queryUserBlogByUserId(Integer current, Long id);

    /**
     * 保存笔记
     *
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);

    /**
     * 滚动分页显示收件箱中的所有笔记
     *
     * @param max    当前时间戳或上一次查询的最小时间戳作为下一次查询的最大值
     * @param offset 偏移量，跳过的元素个数，第一次查询时没有偏移量
     * @return
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
