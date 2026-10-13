package com.example.delicious.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.common.Result;
import com.example.delicious.entity.UserInfo;
import com.example.delicious.service.UserInfoService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/userInfo")
public class UserInfoController {

    @Resource
    private UserInfoService userInfoService;

    @PostMapping
    public Result<UserInfo> add(@RequestBody UserInfo info) {
        return Result.success(userInfoService.add(info));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        userInfoService.delete(id);
        return Result.success();
    }

    @PutMapping
    public Result<?> update(@RequestBody UserInfo info) {
        userInfoService.update(info);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<UserInfo> detail(@PathVariable Long id) {
        return Result.success(userInfoService.findById(id));
    }

    @GetMapping
    public Result<List<UserInfo>> all() {
        return Result.success(userInfoService.findAll());
    }

    @GetMapping("/page/{name}")
    public Result<Page<UserInfo>> page(@PathVariable String name,
                                       @RequestParam(defaultValue = "1") Integer pageNum,
                                       @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(userInfoService.findPage(name, pageNum, pageSize));
    }
}