package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * Web controller
 * </p>
 *
 * @author Hu Ge
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * Send a phone verification code
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // Send and save the SMS verification code
        return userService.sendCode(phone, session);
    }

    /**
     * Login
     * @param loginForm Login parameters: phone and verification code, or phone and password
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // Implement login
        return userService.login(loginForm, session);
    }

    /**
     * Logout
     * @return Nothing
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO Implement logout
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me(){
        // Get and return the currently logged-in user
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // Query details
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // No details found; this may be the first request for details
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // Return
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // Query details
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // Return
        return Result.ok(userDTO);
    }

    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }
}