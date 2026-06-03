package com.example.delicious.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.entity.FoodsMenuInfo;
import com.example.delicious.mapper.FoodsMenuInfoMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class FoodsMenuInfoService {

    @Resource
    private FoodsMenuInfoMapper foodsMenuInfoMapper;

    public FoodsMenuInfo add(FoodsMenuInfo info) {
        foodsMenuInfoMapper.insert(info);
        return info;
    }

    public void delete(Long id) {
        foodsMenuInfoMapper.deleteById(id);
    }

    public void update(FoodsMenuInfo info) {
        foodsMenuInfoMapper.updateById(info);
    }

    public FoodsMenuInfo findById(Long id) {
        return foodsMenuInfoMapper.selectById(id);
    }

    public List<FoodsMenuInfo> findAll() {
        return foodsMenuInfoMapper.selectList(null);
    }

    public Page<FoodsMenuInfo> findPage(String name, Long classifyId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<FoodsMenuInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(name) && !"null".equals(name)) {
            wrapper.like(FoodsMenuInfo::getName, name);
        }
        if (classifyId != null) {
            wrapper.eq(FoodsMenuInfo::getClassifyId, classifyId);
        }
        return foodsMenuInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public Page<FoodsMenuInfo> findPageByUser(String name, String userName, Integer level, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<FoodsMenuInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(name) && !"null".equals(name)) {
            wrapper.like(FoodsMenuInfo::getName, name);
        }
        if (StringUtils.isNotBlank(userName)) {
            wrapper.eq(FoodsMenuInfo::getUserName, userName);
        }
        if (level != null) {
            wrapper.eq(FoodsMenuInfo::getLevel, level);
        }
        return foodsMenuInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}