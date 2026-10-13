package com.example.delicious.controller;

import com.example.delicious.common.Result;
import com.example.delicious.common.config.JwtUtil;
import com.example.delicious.common.exception.CustomException;
import com.example.delicious.entity.Account;
import com.example.delicious.entity.AdminInfo;
import com.example.delicious.entity.UserInfo;
import com.example.delicious.service.AdminInfoService;
import com.example.delicious.service.UserInfoService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AccountController {

    @Resource
    private AdminInfoService adminInfoService;
    @Resource
    private UserInfoService userInfoService;
    @Resource
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Account account) {
        if (account.getName() == null || account.getPassword() == null || account.getLevel() == null) {
            throw new CustomException("400", "参数缺失");
        }
        Map<String, Object> claims = new HashMap<>();
        if (account.getLevel() == 1) {
            AdminInfo info = adminInfoService.login(account.getName(), account.getPassword());
            claims.put("id", info.getId());
            claims.put("name", info.getName());
            claims.put("nickName", info.getNickName());
            claims.put("level", info.getLevel());
        } else if (account.getLevel() == 2) {
            UserInfo info = userInfoService.login(account.getName(), account.getPassword());
            claims.put("id", info.getId());
            claims.put("name", info.getName());
            claims.put("nickName", info.getNickName());
            claims.put("level", info.getLevel());
        } else {
            throw new CustomException("400", "角色类型错误");
        }
        String token = jwtUtil.generateToken(claims);
        Map<String, Object> result = new HashMap<>(claims);
        result.put("token", token);
        return Result.success(result);
    }

    @PostMapping("/register")
    public Result<?> register(@RequestBody Account account) {
        if (account.getName() == null || account.getPassword() == null || account.getLevel() == null) {
            throw new CustomException("400", "参数缺失");
        }
        if (account.getLevel() == 1) {
            AdminInfo info = new AdminInfo();
            info.setName(account.getName());
            info.setPassword(account.getPassword());
            info.setNickName(account.getNickName());
            return Result.success(adminInfoService.add(info));
        } else {
            UserInfo info = new UserInfo();
            info.setName(account.getName());
            info.setPassword(account.getPassword());
            info.setNickName(account.getNickName());
            return Result.success(userInfoService.add(info));
        }
    }

    @PutMapping("/updatePassword")
    public Result<?> updatePassword(@RequestBody Account account, HttpServletRequest request) {
        Integer level = (Integer) request.getAttribute("userLevel");
        Long id = Long.valueOf(request.getAttribute("userId").toString());
        if (level == 1) {
            AdminInfo info = adminInfoService.findById(id);
            if (!account.getPassword().equals(info.getPassword())) {
                throw new CustomException("1001", "原密码错误");
            }
            info.setPassword(account.getNewPassword());
            adminInfoService.update(info);
        } else {
            UserInfo info = userInfoService.findById(id);
            if (!account.getPassword().equals(info.getPassword())) {
                throw new CustomException("1001", "原密码错误");
            }
            info.setPassword(account.getNewPassword());
            userInfoService.update(info);
        }
        return Result.success();
    }

    @PostMapping("/resetPassword")
    public Result<?> resetPassword(@RequestBody Account account) {
        if (account.getLevel() == 1) {
            AdminInfo info = adminInfoService.findByName(account.getName());
            if (info == null) throw new CustomException("1001", "用户不存在");
            info.setPassword("123456");
            adminInfoService.update(info);
        } else {
            UserInfo info = userInfoService.findByName(account.getName());
            if (info == null) throw new CustomException("1001", "用户不存在");
            info.setPassword("123456");
            userInfoService.update(info);
        }
        return Result.success();
    }

    @GetMapping("/getAccountInfo")
    public Result<?> getAccountInfo(HttpServletRequest request) {
        Integer level = (Integer) request.getAttribute("userLevel");
        Long id = Long.valueOf(request.getAttribute("userId").toString());
        if (level == 1) {
            return Result.success(adminInfoService.findById(id));
        } else {
            return Result.success(userInfoService.findById(id));
        }
    }
}