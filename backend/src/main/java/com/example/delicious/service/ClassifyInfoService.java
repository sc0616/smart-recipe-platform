package com.example.delicious.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.entity.ClassifyInfo;
import com.example.delicious.mapper.ClassifyInfoMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class ClassifyInfoService {

    @Resource
    private ClassifyInfoMapper classifyInfoMapper;

    public ClassifyInfo add(ClassifyInfo info) {
        classifyInfoMapper.insert(info);
        return info;
    }

    public void delete(Long id) {
        classifyInfoMapper.deleteById(id);
    }

    public void update(ClassifyInfo info) {
        classifyInfoMapper.updateById(info);
    }

    public ClassifyInfo findById(Long id) {
        return classifyInfoMapper.selectById(id);
    }

    public List<ClassifyInfo> findAll() {
        return classifyInfoMapper.selectList(null);
    }

    public Page<ClassifyInfo> findPage(String name, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ClassifyInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(name) && !"null".equals(name)) {
            wrapper.like(ClassifyInfo::getName, name);
        }
        return classifyInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}