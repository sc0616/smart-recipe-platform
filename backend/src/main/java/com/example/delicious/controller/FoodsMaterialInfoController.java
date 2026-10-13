package com.example.delicious.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.common.Result;
import com.example.delicious.common.exception.CustomException;
import com.example.delicious.entity.FoodsMaterialInfo;
import com.example.delicious.entity.NxSystemFileInfo;
import com.example.delicious.service.FoodsMaterialInfoService;
import com.example.delicious.service.NxSystemFileInfoService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/foodsMaterialInfo")
public class FoodsMaterialInfoController {

    @Resource
    private FoodsMaterialInfoService foodsMaterialInfoService;
    @Resource
    private NxSystemFileInfoService nxSystemFileInfoService;

    /** 与文件控制器共用同一上传目录配置，避免路径不一致导致图片删不掉 */
    @org.springframework.beans.factory.annotation.Value("${file.upload-path}")
    private String uploadPath;

    @PostMapping
    public Result<FoodsMaterialInfo> add(@RequestBody FoodsMaterialInfo info, HttpServletRequest request) {
        info.setUserName(request.getAttribute("userName").toString());
        info.setLevel((Integer) request.getAttribute("userLevel"));
        info.setUploadUserId(Long.valueOf(request.getAttribute("userId").toString()));
        return Result.success(foodsMaterialInfoService.add(info));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        Integer level = (Integer) request.getAttribute("userLevel");
        FoodsMaterialInfo info = foodsMaterialInfoService.findById(id);
        if (info == null) throw new CustomException("1001", "记录不存在");
        if (!userId.equals(info.getUploadUserId()) || !level.equals(info.getLevel())) {
            throw new CustomException("1001", "不能删除他人的记录");
        }
        foodsMaterialInfoService.delete(id);
        if (info.getFileId() != null) {
            NxSystemFileInfo fileInfo = nxSystemFileInfoService.findById(info.getFileId());
            if (fileInfo != null) {
                java.io.File file = new java.io.File(uploadPath, fileInfo.getFileName());
                file.delete();
                nxSystemFileInfoService.delete(info.getFileId());
            }
        }
        return Result.success();
    }

    @PutMapping
    public Result<?> update(@RequestBody FoodsMaterialInfo info, HttpServletRequest request) {
        Long userId = Long.valueOf(request.getAttribute("userId").toString());
        Integer level = (Integer) request.getAttribute("userLevel");
        FoodsMaterialInfo exist = foodsMaterialInfoService.findById(info.getId());
        if (exist == null) throw new CustomException("1001", "记录不存在");
        if (!userId.equals(exist.getUploadUserId()) || !level.equals(exist.getLevel())) {
            throw new CustomException("1001", "不能修改他人的记录");
        }
        foodsMaterialInfoService.update(info);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<FoodsMaterialInfo> detail(@PathVariable Long id) {
        return Result.success(foodsMaterialInfoService.findById(id));
    }

    @GetMapping
    public Result<List<FoodsMaterialInfo>> all() {
        return Result.success(foodsMaterialInfoService.findAll());
    }

    @GetMapping("/page/{name}")
    public Result<Page<FoodsMaterialInfo>> page(@PathVariable String name,
                                                @RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(foodsMaterialInfoService.findPage(name, pageNum, pageSize));
    }
}