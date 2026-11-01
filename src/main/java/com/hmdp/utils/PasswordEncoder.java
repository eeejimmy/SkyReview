package com.hmdp.utils;


import cn.hutool.core.util.RandomUtil;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

public class PasswordEncoder {

    public static String encode(String password) {
        // Generate a salt
        String salt = RandomUtil.randomString(20);
        // Encrypt
        return encode(password,salt);
    }
    private static String encode(String password, String salt) {
        // Encrypt
        return salt + "@" + DigestUtils.md5DigestAsHex((password + salt).getBytes(StandardCharsets.UTF_8));
    }
    public static Boolean matches(String encodedPassword, String rawPassword) {
        if (encodedPassword == null || rawPassword == null) {
            return false;
        }
        if(!encodedPassword.contains("@")){
            throw new RuntimeException("密码格式不正确！");
        }
        String[] arr = encodedPassword.split("@");
        // Get the salt
        String salt = arr[0];
        // Compare
        return encodedPassword.equals(encode(rawPassword, salt));
    }
}
