package com.example.delicious.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.entity.FoodsMaterialInfo;
import com.example.delicious.mapper.FoodsMaterialInfoMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class FoodsMaterialInfoService {

    @Resource
    private FoodsMaterialInfoMapper foodsMaterialInfoMapper;

    public FoodsMaterialInfo add(FoodsMaterialInfo info) {
        foodsMaterialInfoMapper.insert(info);
        return info;
    }

    public void delete(Long id) {
        foodsMaterialInfoMapper.deleteById(id);
    }

    public void update(FoodsMaterialInfo info) {
        foodsMaterialInfoMapper.updateById(info);
    }

    public FoodsMaterialInfo findById(Long id) {
        return foodsMaterialInfoMapper.selectById(id);
    }

    public List<FoodsMaterialInfo> findAll() {
        return foodsMaterialInfoMapper.selectList(null);
    }

    public Page<FoodsMaterialInfo> findPage(String name, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<FoodsMaterialInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(name) && !"null".equals(name)) {
            wrapper.like(FoodsMaterialInfo::getName, name);
        }
        return foodsMaterialInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}