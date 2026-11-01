package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.Check whether interception is needed (whether ThreadLocal contains a user)
        if (UserHolder.getUser() == null) {
            // No user found; intercept and set the status code
            response.setStatus(401);
            // Intercept the request
            return false;
        }
        // User exists; allow the request
        return true;
    }
}
