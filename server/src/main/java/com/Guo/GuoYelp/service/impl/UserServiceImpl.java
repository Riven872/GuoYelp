package com.Guo.GuoYelp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.Guo.GuoYelp.dto.LoginFormDTO;
import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.dto.UserDTO;
import com.Guo.GuoYelp.utils.RegexUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Guo.GuoYelp.entity.User;
import com.Guo.GuoYelp.mapper.UserMapper;
import com.Guo.GuoYelp.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.Guo.GuoYelp.utils.SystemConstants;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.Guo.GuoYelp.utils.RedisConstants.*;
import static com.Guo.GuoYelp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号码
        if (RegexUtils.isPhoneInvalid(phone)) {
            //如果不符合，返回错误信息
            return Result.fail("手机号码格式错误");
        }

        //符合则生成验证码
        String code = RandomUtil.randomNumbers(6);

        //保存验证码到redis中，且key值加前缀作为业务逻辑的区分
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送验证码
        log.info("验证码为：{}", code);

        return Result.ok();
    }

    /**
     * 账号登录
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        //region 从Redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }
        //endregion

        //通过校验，根据手机号查询用户
        User user = query().eq("phone", phone).one();

        //用户不存在，创建新用户并保存
        if (user == null) {
            user = this.createUserWithPhone(phone);
        }

        //region 保存用户信息到redis中
        //随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //存储User到Redis中
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        //设置token有效期为半小时
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //endregion

        //登录成功，则将验证码从Redis中删除
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        //返回Token
        return Result.ok(token);
    }

    /**
     * 查看用户主页并返回用户信息
     * @param id
     * @return
     */
    @Override
    public Result getUserById(Long id) {
        User user = this.getById(id);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    /**
     * 根据手机号创建用户
     *
     * @param phone
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        this.save(user);
        return user;
    }


}
