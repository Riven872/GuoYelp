package com.Guo.GuoYelp.service;

import com.Guo.GuoYelp.dto.LoginFormDTO;
import com.Guo.GuoYelp.dto.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import com.Guo.GuoYelp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IUserService extends IService<User> {

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 账号登录
     * @param loginForm
     * @param session
     * @return
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    Result getUserById(Long id);
}
