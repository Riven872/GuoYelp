package com.Guo.GuoYelp.service;

import com.Guo.GuoYelp.dto.LoginFormDTO;
import com.Guo.GuoYelp.dto.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import com.Guo.GuoYelp.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * 用户管理
 */
public interface IUserService extends IService<User> {

    /**
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 账号登录
     *
     * @param loginForm
     * @param session
     * @return
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    Result getUserById(Long id);

    Result sign();

    Result signCount();

    Result logout(HttpServletRequest request);

    String bar();
}
