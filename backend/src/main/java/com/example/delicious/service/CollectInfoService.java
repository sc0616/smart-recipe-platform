package com.example.delicious.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.entity.CollectInfo;
import com.example.delicious.mapper.CollectInfoMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class CollectInfoService {

    @Resource
    private CollectInfoMapper collectInfoMapper;

    public CollectInfo add(CollectInfo info) {
        collectInfoMapper.insert(info);
        return info;
    }

    public void delete(Long id) {
        collectInfoMapper.deleteById(id);
    }

    public void update(CollectInfo info) {
        collectInfoMapper.updateById(info);
    }

    public CollectInfo findById(Long id) {
        return collectInfoMapper.selectById(id);
    }

    public List<CollectInfo> findAll() {
        return collectInfoMapper.selectList(null);
    }

    public Page<CollectInfo> findPage(String name, Long userId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<CollectInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(name) && !"null".equals(name)) {
            wrapper.like(CollectInfo::getName, name);
        }
        if (userId != null) {
            wrapper.eq(CollectInfo::getUserId, userId);
        }
        return collectInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public boolean hasUserCollected(Long foodsId, Long userId) {
        return collectInfoMapper.selectCount(
                new LambdaQueryWrapper<CollectInfo>()
                        .eq(CollectInfo::getFoodsId, foodsId)
                        .eq(CollectInfo::getUserId, userId)
        ) > 0;
    }
}