package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
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
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * Service implementation
 * </p>
 *
 * @author Hu Ge
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.Validate the phone number
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.Invalid input; return an error message
            return Result.fail("手机号格式错误！");
        }
        // 3.Valid input; generate a verification code
        String code = RandomUtil.randomNumbers(6);

        // 4.Save the verification code in the session
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5.Send the verification code
        log.debug("发送短信验证码成功，验证码：{}", code);
        // Return success
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.Validate the phone number
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.Invalid input; return an error message
            return Result.fail("手机号格式错误！");
        }
        // 3.Retrieve and validate the verification code from Redis
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // Code mismatch; report an error
            return Result.fail("验证码错误");
        }

        // 4.Code matches; query the user by phone: select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        // 5.Check whether the user exists
        if (user == null) {
            // 6.User does not exist; create and save a new user
            user = createUserWithPhone(phone);
        }

        // 7.Save user information in Redis
        // 7.1.Generate a random token for authentication
        String token = UUID.randomUUID().toString(true);
        // 7.2.Convert the User object to a HashMap for storage
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 7.3.Store
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.Set the token TTL
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8.Return the token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // 1.Get the currently logged-in user
        Long userId = UserHolder.getUser().getId();
        // 2.Get the date
        LocalDateTime now = LocalDateTime.now();
        // 3.Build the key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.Get today's day of the month
        int dayOfMonth = now.getDayOfMonth();
        // 5.Write to Redis: SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.Get the currently logged-in user
        Long userId = UserHolder.getUser().getId();
        // 2.Get the date
        LocalDateTime now = LocalDateTime.now();
        // 3.Build the key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.Get today's day of the month
        int dayOfMonth = now.getDayOfMonth();
        // 5.Get this month's check-in bits through today as a decimal number: BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            // No check-in results
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.Iterate
        int count = 0;
        while (true) {
            // 6.1.AND the number with 1 to extract its last bit; check whether that bit is zero
            if ((num & 1) == 0) {
                // A zero bit indicates no check-in; stop
                break;
            }else {
                // A nonzero bit indicates a check-in; increment the counter
                count++;
            }
            // Shift right by one bit, discard the last bit, and continue with the next bit
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        // 1.Create a user
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.Save the user
        save(user);
        return user;
    }
}
