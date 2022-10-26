package com.Guo.GuoYelp.utils;

import com.Guo.GuoYelp.dto.UserDTO;
import com.Guo.GuoYelp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 自定义拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {
    /**
     * 从session中校验客户是否登录
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取session
        HttpSession session = request.getSession();

        //从session中获取当前用户信息
        Object user = session.getAttribute("user");

        //用户不存在，则拦截，并返回401未授权
        if (user == null) {
            response.setStatus(401);
            return false;
        }

        //用户存在，放到ThreadLocal中
        UserHolder.saveUser((UserDTO) user);

        return true;
    }
}