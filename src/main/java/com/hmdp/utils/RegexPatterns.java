package com.hmdp.utils;

/**
 * @author Hu Ge
 */
public abstract class RegexPatterns {
    /**
     * Phone number pattern
     */
    public static final String PHONE_REGEX = "^1([38][0-9]|4[579]|5[0-3,5-9]|6[6]|7[0135678]|9[89])\\d{8}$";
    /**
     * Email pattern
     */
    public static final String EMAIL_REGEX = "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$";
    /**
     * Password pattern: 4-32 letters, digits, or underscores
     */
    public static final String PASSWORD_REGEX = "^\\w{4,32}$";
    /**
     * Verification code pattern: 6 digits or letters
     */
    public static final String VERIFY_CODE_REGEX = "^[a-zA-Z\\d]{6}$";

}
