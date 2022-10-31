package com.Guo.GuoYelp.utils;

import com.Guo.GuoYelp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 自定义拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

    //region 获取StringRedisTemplate的注入
    //不能通过注解注入，只能通过构造函数注入stringRedisTemplate，因为LoginInterceptor没有交给Spring管理
    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //endregion

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
