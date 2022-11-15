package com.Guo.GuoYelp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.dto.ScrollResult;
import com.Guo.GuoYelp.dto.UserDTO;
import com.Guo.GuoYelp.entity.Blog;
import com.Guo.GuoYelp.entity.Follow;
import com.Guo.GuoYelp.entity.User;
import com.Guo.GuoYelp.mapper.BlogMapper;
import com.Guo.GuoYelp.service.IBlogService;
import com.Guo.GuoYelp.service.IFollowService;
import com.Guo.GuoYelp.service.IUserService;
import com.Guo.GuoYelp.utils.SystemConstants;
import com.Guo.GuoYelp.utils.UserHolder;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.Guo.GuoYelp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.Guo.GuoYelp.utils.RedisConstants.FEED_KEY;

/**
 * 探店模块
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询探店笔记详情
     *
     * @param id 探店笔记id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        //查询blog信息
        Blog blog = this.getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        //查询与blog有关的用户并将信息放到Blog中
        queryBlogUser(blog);
        //查询blog是否被点赞
        isBlogLiked(blog);

        return Result.ok(blog);
    }

    /**
     * 查询Blog是否被点赞
     *
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, blog.getUserId().toString());
        blog.setIsLike(score != null);
    }

    /**
     * 查询所有探店笔记
     *
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 点赞
     *
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //判断当前用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //score不存在则说明没有点赞
        if (score == null) {
            //未点赞，则可以进行点赞
            //数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //保存用户到Redis的set集合
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            //已点赞，则取消点赞
            //数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //移除用户
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 查看选中blog下的点赞排行榜
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        //查询top5的点赞用户 zrange key 0 4
        String key = BLOG_LIKED_KEY + id;
        //得到top5的value集合（key、value、score）
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //如果没有点赞则返回空集合，避免流程空指针
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //解析出用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        //根据用户id查询用户
        List<User> users = userService.query().in("id", ids).last("ORDER BY FIELD(id, " + idStr + ")").list();
        //解析成UserDto对象返回
        List<UserDTO> userDTOS = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    /**
     * 分页查询用户主页中的笔记
     *
     * @param current 当前页码
     * @param id      用户id
     * @return
     */
    @Override
    public Result queryUserBlogByUserId(Integer current, Long id) {
        Page<Blog> page = this.query().eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    /**
     * 保存笔记
     *
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        //前端已经提交了商铺id、标题、图片、内容，因此这里只需要再添加用户id即可保存
        blog.setUserId(user.getId());
        // 保存探店笔记
        boolean isSuccess = this.save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败");
        }
        //查询笔记作者所有的粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        //推送笔记id给所有粉丝
        follows.forEach(follow -> {
            //获取粉丝id
            Long userId = follow.getUserId();
            //推送到粉丝收件箱
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        });
        return Result.ok(blog.getId());
    }

    /**
     * 滚动分页显示收件箱中的所有笔记
     *
     * @param max    当前时间戳或上一次查询的最小时间戳作为下一次查询的最大值
     * @param offset 偏移量，跳过的元素个数，第一次查询时没有偏移量
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //查询当前用户
        Long userId = UserHolder.getUser().getId();
        //查询收件箱
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
                .opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 3L);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        //保存ids
        ArrayList<Long> ids = new ArrayList<>(typedTuples.size());
        //保存最小时间
        long minTime = 0L;
        //计算offset
        int os = 1;
        //解析数据：笔记id、minTime(时间戳)、offset
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取id
            ids.add(Long.valueOf(typedTuple.getValue()));
            //获取score（时间戳）,最后取出来的才是最小的时间
            long time = typedTuple.getScore().longValue();
            //计算offset时，只需要计算与最小时间相同的个数
            if (time == minTime) {
                os++;
            } else {
                //如果不为最小时间，则重置重新计算
                minTime = time;
                os = 1;
            }
        }
        //根据id查询blog（不能直接查询，会导致查出来的顺序错乱）
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = this.query().in("id", ids).last("ORDER BY FIELD(id, " + idStr + ")").list();
        blogs.forEach(blog -> {
            //查询与blog有关的用户并将信息放到Blog中
            queryBlogUser(blog);
            //查询blog是否被点赞
            isBlogLiked(blog);
        });
        //封装数据并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setMinTime(minTime);
        r.setOffset(offset);
        return Result.ok(r);
    }

    /**
     * 查询Blog中对应的用户信息
     *
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
