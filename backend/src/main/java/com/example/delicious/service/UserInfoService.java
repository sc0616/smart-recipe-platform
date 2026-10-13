package com.example.delicious.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.common.exception.CustomException;
import com.example.delicious.entity.UserInfo;
import com.example.delicious.mapper.UserInfoMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class UserInfoService {

    @Resource
    private UserInfoMapper userInfoMapper;

    public UserInfo add(UserInfo info) {
        UserInfo exist = findByName(info.getName());
        if (exist != null) {
            throw new CustomException("1001", "用户名已存在");
        }
        info.setLevel(2);
        userInfoMapper.insert(info);
        return info;
    }

    public void delete(Long id) {
        userInfoMapper.deleteById(id);
    }

    public void update(UserInfo info) {
        userInfoMapper.updateById(info);
    }

    public UserInfo findById(Long id) {
        return userInfoMapper.selectById(id);
    }

    public UserInfo findByName(String name) {
        return userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getName, name));
    }

    public List<UserInfo> findAll() {
        return userInfoMapper.selectList(null);
    }

    public Page<UserInfo> findPage(String name, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(name) && !"null".equals(name)) {
            wrapper.like(UserInfo::getName, name);
        }
        return userInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public UserInfo login(String name, String password) {
        UserInfo info = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>()
                        .eq(UserInfo::getName, name)
                        .eq(UserInfo::getPassword, password)
        );
        if (info == null) {
            throw new CustomException("1001", "用户名或密码错误");
        }
        return info;
    }
}