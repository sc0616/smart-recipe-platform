package com.example.delicious.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.common.exception.CustomException;
import com.example.delicious.entity.AdminInfo;
import com.example.delicious.mapper.AdminInfoMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class AdminInfoService {

    @Resource
    private AdminInfoMapper adminInfoMapper;

    public AdminInfo add(AdminInfo info) {
        AdminInfo exist = findByName(info.getName());
        if (exist != null) {
            throw new CustomException("1001", "用户名已存在");
        }
        info.setLevel(1);
        adminInfoMapper.insert(info);
        return info;
    }

    public void delete(Long id) {
        adminInfoMapper.deleteById(id);
    }

    public void update(AdminInfo info) {
        adminInfoMapper.updateById(info);
    }

    public AdminInfo findById(Long id) {
        return adminInfoMapper.selectById(id);
    }

    public AdminInfo findByName(String name) {
        return adminInfoMapper.selectOne(new LambdaQueryWrapper<AdminInfo>().eq(AdminInfo::getName, name));
    }

    public List<AdminInfo> findAll() {
        return adminInfoMapper.selectList(null);
    }

    public Page<AdminInfo> findPage(String name, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<AdminInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(name) && !"null".equals(name)) {
            wrapper.like(AdminInfo::getName, name);
        }
        return adminInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public AdminInfo login(String name, String password) {
        AdminInfo info = adminInfoMapper.selectOne(
                new LambdaQueryWrapper<AdminInfo>()
                        .eq(AdminInfo::getName, name)
                        .eq(AdminInfo::getPassword, password)
        );
        if (info == null) {
            throw new CustomException("1001", "用户名或密码错误");
        }
        return info;
    }
}