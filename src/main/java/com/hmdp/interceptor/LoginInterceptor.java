package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 获取 session
        HttpSession session = request.getSession();
        // 2. 从 session 获取用户
        Object userDTO = session.getAttribute("user");
        // 3. 判断用户是否存在
        if (null == userDTO) {
            // 4. 如果用户不存在，拦截，返回 401 状态码
            response.setStatus(401);
            return false;
        }
        // 5. 如果用户存在，保存用户到 ThreadLocal，登录的时候保存的是 User 对象，这里要转换成 UserDTO 对象
        UserHolder.saveUser((UserDTO) userDTO);
        // 6. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        // 移除用户
        UserHolder.removeUser();
    }
}
