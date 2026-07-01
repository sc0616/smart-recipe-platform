package com.example.delicious.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.common.Result;
import com.example.delicious.entity.CollectInfo;
import com.example.delicious.service.CollectInfoService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/collectInfo")
public class CollectInfoController {

    @Resource
    private CollectInfoService collectInfoService;

    @PostMapping
    public Result<CollectInfo> add(@RequestBody CollectInfo info, HttpServletRequest request) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        String userName = request.getAttribute("userName").toString();
        Integer level = (Integer) request.getAttribute("userLevel");
        info.setUserId(userId);
        info.setName(userName);
        info.setLevel(level);
        info.setTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return Result.success(collectInfoService.add(info));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        collectInfoService.delete(id);
        return Result.success();
    }

    @PutMapping
    public Result<?> update(@RequestBody CollectInfo info) {
        collectInfoService.update(info);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<CollectInfo> detail(@PathVariable Long id) {
        return Result.success(collectInfoService.findById(id));
    }

    @GetMapping
    public Result<List<CollectInfo>> all() {
        return Result.success(collectInfoService.findAll());
    }

    @GetMapping("/page/{name}")
    public Result<Page<CollectInfo>> page(@PathVariable String name,
                                          @RequestParam(required = false) Long userId,
                                          @RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(collectInfoService.findPage(name, userId, pageNum, pageSize));
    }

    @GetMapping("/check")
    public Result<Boolean> check(@RequestParam Long foodsId, HttpServletRequest request) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        return Result.success(collectInfoService.hasUserCollected(foodsId, userId));
    }
}