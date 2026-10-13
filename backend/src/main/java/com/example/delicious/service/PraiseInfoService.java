package com.example.delicious.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.entity.PraiseInfo;
import com.example.delicious.mapper.PraiseInfoMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class PraiseInfoService {

    @Resource
    private PraiseInfoMapper praiseInfoMapper;

    public PraiseInfo add(PraiseInfo info) {
        praiseInfoMapper.insert(info);
        return info;
    }

    public void delete(Long id) {
        praiseInfoMapper.deleteById(id);
    }

    public void update(PraiseInfo info) {
        praiseInfoMapper.updateById(info);
    }

    public PraiseInfo findById(Long id) {
        return praiseInfoMapper.selectById(id);
    }

    public List<PraiseInfo> findAll() {
        return praiseInfoMapper.selectList(null);
    }

    public Page<PraiseInfo> findPage(String name, Long userId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<PraiseInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(name) && !"null".equals(name)) {
            wrapper.like(PraiseInfo::getName, name);
        }
        if (userId != null) {
            wrapper.eq(PraiseInfo::getUserId, userId);
        }
        return praiseInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public boolean hasUserPraised(Long foodsId, Long userId) {
        return praiseInfoMapper.selectCount(
                new LambdaQueryWrapper<PraiseInfo>()
                        .eq(PraiseInfo::getFoodsId, foodsId)
                        .eq(PraiseInfo::getUserId, userId)
        ) > 0;
    }
}