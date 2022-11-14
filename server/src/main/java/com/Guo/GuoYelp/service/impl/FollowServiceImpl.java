package com.Guo.GuoYelp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.dto.UserDTO;
import com.Guo.GuoYelp.entity.Follow;
import com.Guo.GuoYelp.entity.User;
import com.Guo.GuoYelp.mapper.FollowMapper;
import com.Guo.GuoYelp.service.IFollowService;
import com.Guo.GuoYelp.service.IUserService;
import com.Guo.GuoYelp.utils.UserHolder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.Wrapper;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 好友关注模块
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    /**
     * 关注和取关
     *
     * @param id       要关注/要取关的用户id
     * @param isFollow true：关注 false：取关
     * @return 标准返回
     */
    @Override
    public Result follow(Long id, boolean isFollow) {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //判断是关注还是取关
        if (isFollow) {
            //关注则新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean isSuccess = this.save(follow);
            //关注成功，将关注人放到Redis中
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }
        } else {
            //取关则删除数据
            QueryWrapper<Follow> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId).eq("follow_user_id", id);
            boolean isSuccess = this.remove(queryWrapper);
            //取关成功，从Redis中移除关注者
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key);
            }
        }
        return Result.ok();
    }

    /**
     * 判断是否已经关注当前用户
     *
     * @param id 要关注的用户id
     * @return
     */
    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        return Result.ok(count > 0);
    }

    /**
     * 查看共同关注
     *
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + userId;
        //获取关注用户id
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        //没有共同关注
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok();
        }
        //解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户
        List<User> users = userService.listByIds(ids);
        List<UserDTO> userDTOS = users.stream().map(user ->
                BeanUtil.copyProperties(user, UserDTO.class)
        ).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
