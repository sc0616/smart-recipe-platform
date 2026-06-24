package com.example.delicious.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.common.Result;
import com.example.delicious.entity.PraiseInfo;
import com.example.delicious.service.PraiseInfoService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/praiseInfo")
public class PraiseInfoController {

    @Resource
    private PraiseInfoService praiseInfoService;

    @PostMapping
    public Result<PraiseInfo> add(@RequestBody PraiseInfo info, HttpServletRequest request) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        String userName = request.getAttribute("userName").toString();
        Integer level = (Integer) request.getAttribute("userLevel");
        info.setUserId(userId);
        info.setName(userName);
        info.setLevel(level);
        info.setTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return Result.success(praiseInfoService.add(info));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        praiseInfoService.delete(id);
        return Result.success();
    }

    @PutMapping
    public Result<?> update(@RequestBody PraiseInfo info) {
        praiseInfoService.update(info);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<PraiseInfo> detail(@PathVariable Long id) {
        return Result.success(praiseInfoService.findById(id));
    }

    @GetMapping
    public Result<List<PraiseInfo>> all() {
        return Result.success(praiseInfoService.findAll());
    }

    @GetMapping("/page/{name}")
    public Result<Page<PraiseInfo>> page(@PathVariable String name,
                                         @RequestParam(required = false) Long userId,
                                         @RequestParam(defaultValue = "1") Integer pageNum,
                                         @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(praiseInfoService.findPage(name, userId, pageNum, pageSize));
    }

    @GetMapping("/check")
    public Result<Boolean> check(@RequestParam Long foodsId, HttpServletRequest request) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        return Result.success(praiseInfoService.hasUserPraised(foodsId, userId));
    }
}