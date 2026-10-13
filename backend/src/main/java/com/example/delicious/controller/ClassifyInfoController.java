package com.example.delicious.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.common.Result;
import com.example.delicious.entity.ClassifyInfo;
import com.example.delicious.service.ClassifyInfoService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/classifyInfo")
public class ClassifyInfoController {

    @Resource
    private ClassifyInfoService classifyInfoService;

    @PostMapping
    public Result<ClassifyInfo> add(@RequestBody ClassifyInfo info) {
        return Result.success(classifyInfoService.add(info));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        classifyInfoService.delete(id);
        return Result.success();
    }

    @PutMapping
    public Result<?> update(@RequestBody ClassifyInfo info) {
        classifyInfoService.update(info);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<ClassifyInfo> detail(@PathVariable Long id) {
        return Result.success(classifyInfoService.findById(id));
    }

    @GetMapping
    public Result<List<ClassifyInfo>> all() {
        return Result.success(classifyInfoService.findAll());
    }

    @GetMapping("/page/{name}")
    public Result<Page<ClassifyInfo>> page(@PathVariable String name,
                                           @RequestParam(defaultValue = "1") Integer pageNum,
                                           @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(classifyInfoService.findPage(name, pageNum, pageSize));
    }
}