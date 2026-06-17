package com.example.delicious.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.common.Result;
import com.example.delicious.common.exception.CustomException;
import com.example.delicious.entity.FoodsMenuInfo;
import com.example.delicious.entity.NxSystemFileInfo;
import com.example.delicious.service.FoodsMenuInfoService;
import com.example.delicious.service.NxSystemFileInfoService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/foodsMenuInfo")
public class FoodsMenuInfoController {

    @Resource
    private FoodsMenuInfoService foodsMenuInfoService;
    @Resource
    private NxSystemFileInfoService nxSystemFileInfoService;

    /** 与文件控制器共用同一上传目录配置，避免路径不一致导致图片删不掉 */
    @org.springframework.beans.factory.annotation.Value("${file.upload-path}")
    private String uploadPath;

    @PostMapping
    public Result<FoodsMenuInfo> add(@RequestBody FoodsMenuInfo info, HttpServletRequest request) {
        info.setUserName(request.getAttribute("userName").toString());
        info.setLevel((Integer) request.getAttribute("userLevel"));
        info.setUploadUserId(Long.valueOf(request.getAttribute("userId").toString()));
        return Result.success(foodsMenuInfoService.add(info));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        Integer level = (Integer) request.getAttribute("userLevel");
        FoodsMenuInfo info = foodsMenuInfoService.findById(id);
        if (info == null) throw new CustomException("1001", "记录不存在");
        if (!userId.equals(info.getUploadUserId()) || !level.equals(info.getLevel())) {
            throw new CustomException("1001", "不能删除他人的记录");
        }
        foodsMenuInfoService.delete(id);
        if (info.getFileId() != null) {
            NxSystemFileInfo fileInfo = nxSystemFileInfoService.findById(info.getFileId());
            if (fileInfo != null) {
                new java.io.File(uploadPath, fileInfo.getFileName()).delete();
                nxSystemFileInfoService.delete(info.getFileId());
            }
        }
        return Result.success();
    }

    @PutMapping
    public Result<?> update(@RequestBody FoodsMenuInfo info, HttpServletRequest request) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        Integer level = (Integer) request.getAttribute("userLevel");
        FoodsMenuInfo exist = foodsMenuInfoService.findById(info.getId());
        if (exist == null) throw new CustomException("1001", "记录不存在");
        if (!userId.equals(exist.getUploadUserId()) || !level.equals(exist.getLevel())) {
            throw new CustomException("1001", "不能修改他人的记录");
        }
        foodsMenuInfoService.update(info);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<FoodsMenuInfo> detail(@PathVariable Long id) {
        return Result.success(foodsMenuInfoService.findById(id));
    }

    @GetMapping
    public Result<List<FoodsMenuInfo>> all() {
        return Result.success(foodsMenuInfoService.findAll());
    }

    @GetMapping("/page/{name}")
    public Result<Page<FoodsMenuInfo>> page(@PathVariable String name,
                                            @RequestParam(required = false) Long classifyId,
                                            @RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(foodsMenuInfoService.findPage(name, classifyId, pageNum, pageSize));
    }

    @GetMapping("/page/user/{name}")
    public Result<Page<FoodsMenuInfo>> userPage(@PathVariable String name,
                                                @RequestParam(required = false) String userName,
                                                @RequestParam(required = false) Integer level,
                                                @RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(foodsMenuInfoService.findPageByUser(name, userName, level, pageNum, pageSize));
    }
}