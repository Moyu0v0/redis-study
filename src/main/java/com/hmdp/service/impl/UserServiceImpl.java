package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.Duration;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3. 如果符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 4. 保存验证码到 redis 并设置有效期为2分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, Duration.ofMinutes(LOGIN_CODE_TTL));
        // 5. 发送验证码（模拟）
        log.debug("发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2. 从 Redis 中取出验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        // 3. 如果不一致，返回错误信息
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误！");
        }
        // 4. 如果一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        // 5. 判断用户是否存在
        if (user == null) {
            // 6. 如果用户不存在，创建新用户并保存到 MySQL 中
            user = createUserWithPhone(phone);
        }
        // 7. 保存用户到 Redis
        // 7.1 生成随机 token 作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2 将 User 转换为 Hash
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);// 这里使用 MapStruct 映射属性性能会更好
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        // 7.3 将 Hash 存储到 Redis 并设置有效期为30分钟
        String userKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(userKey, userMap);
        stringRedisTemplate.expire(userKey, Duration.ofMinutes(LOGIN_USER_TTL));
        // 8. 给客户端返回 token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        // 1. 创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2, 保存用户
        save(user);
        return user;
    }
}
