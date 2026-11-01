package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;

/**
 * @author Hu Ge
 */
public class RegexUtils {
    /**
     * Check whether the phone number format is invalid
     * @param phone Phone number to validate
     * @return true if valid; false otherwise
     */
    public static boolean isPhoneInvalid(String phone){
        return mismatch(phone, RegexPatterns.PHONE_REGEX);
    }
    /**
     * Check whether the email format is invalid
     * @param email Email address to validate
     * @return true if valid; false otherwise
     */
    public static boolean isEmailInvalid(String email){
        return mismatch(email, RegexPatterns.EMAIL_REGEX);
    }

    /**
     * Check whether the verification code format is invalid
     * @param code Verification code to validate
     * @return true if valid; false otherwise
     */
    public static boolean isCodeInvalid(String code){
        return mismatch(code, RegexPatterns.VERIFY_CODE_REGEX);
    }

    // Check whether the value fails to match the pattern
    private static boolean mismatch(String str, String regex){
        if (StrUtil.isBlank(str)) {
            return true;
        }
        return !str.matches(regex);
    }
}
