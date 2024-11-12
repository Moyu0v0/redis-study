package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2. 校验验证码
        Object cacheCode = session.getAttribute("code");
        // 3. 如果不一致，返回错误信息
        String code = loginForm.getCode();
        if (null == cacheCode || !cacheCode.toString().equals(code)) {
            return Result.fail("验证码错误！");
        }
        // 4. 如果一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        // 5. 如果用户不存在，创建新用户并保存到数据库中
        if (null == user) {
            user = createUserWithPhone(phone);
        }
        // 6. 保存用户到session
        session.setAttribute("user", user);
        return Result.ok();
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
