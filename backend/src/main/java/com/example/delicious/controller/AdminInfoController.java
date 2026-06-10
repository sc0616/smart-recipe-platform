package com.example.delicious.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.common.Result;
import com.example.delicious.entity.AdminInfo;
import com.example.delicious.service.AdminInfoService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/adminInfo")
public class AdminInfoController {

    @Resource
    private AdminInfoService adminInfoService;

    @PostMapping
    public Result<AdminInfo> add(@RequestBody AdminInfo info) {
        return Result.success(adminInfoService.add(info));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        adminInfoService.delete(id);
        return Result.success();
    }

    @PutMapping
    public Result<?> update(@RequestBody AdminInfo info) {
        adminInfoService.update(info);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<AdminInfo> detail(@PathVariable Long id) {
        return Result.success(adminInfoService.findById(id));
    }

    @GetMapping
    public Result<List<AdminInfo>> all() {
        return Result.success(adminInfoService.findAll());
    }

    @GetMapping("/page/{name}")
    public Result<Page<AdminInfo>> page(@PathVariable String name,
                                        @RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(adminInfoService.findPage(name, pageNum, pageSize));
    }
}