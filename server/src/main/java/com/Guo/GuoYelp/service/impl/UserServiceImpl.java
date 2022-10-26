package com.Guo.GuoYelp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.springframework.stereotype.Service;
import com.Guo.GuoYelp.utils.SystemConstants;

import javax.servlet.http.HttpSession;

import static com.Guo.GuoYelp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

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

        //保存验证码至session中
        session.setAttribute(phone, code);

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

        //校验验证码
        Object cacheCode = session.getAttribute(phone);
        if (cacheCode == null || !cacheCode.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }

        //通过校验，根据手机号查询用户
        User user = query().eq("phone", phone).one();

        //用户不存在，创建新用户并保存
        if (user == null) {
            user = this.createUserWithPhone(phone);
        }
        //将用户信息保存到session中
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        return Result.ok();
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