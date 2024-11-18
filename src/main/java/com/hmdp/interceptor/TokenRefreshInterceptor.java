package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * TokenRefreshInterceptor
 *
 * @author sundae
 * @date 2024/11/13
 * @description
 */
public class TokenRefreshInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;

    public TokenRefreshInterceptor(StringRedisTemplate stringRedisTemplate) {
        // TokenRefreshInterceptor 不是由 Spring 创建的，因此不能使用依赖注入，也可以直接给这个类加上 @Component 注解
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 获取请求头中的 token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // 2. 如果 token 为空白（null/长度为0/全空白字符），放行
            return true;
        }
        // 3. 基于 token 从 redis 获取用户
        String userKey = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(userKey);
        // 4. 判断用户是否存在
        if (userMap.isEmpty()) { // entries()方法返回时就会做null处理，因此不用判断是否为null了
            // 5. 如果用户不存在，放行
            return true;
        }
        // 6. 将查询到的 Hash 数据转为 UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 7. 保存用户到 ThreadLocal （后续的请求处理链路中都可以方便地访问这些信息）
        UserHolder.saveUser((UserDTO) userDTO);
        // 8. 刷新 token 的有效期
        stringRedisTemplate.expire(userKey, Duration.ofMinutes(LOGIN_USER_TTL));
        // 9. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        // 移除用户
        UserHolder.removeUser();
    }
}

