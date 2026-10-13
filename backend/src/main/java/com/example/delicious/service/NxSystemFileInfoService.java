package com.example.delicious.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.entity.NxSystemFileInfo;
import com.example.delicious.mapper.NxSystemFileInfoMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

@Service
public class NxSystemFileInfoService {

    @Resource
    private NxSystemFileInfoMapper nxSystemFileInfoMapper;

    public NxSystemFileInfo add(NxSystemFileInfo info) {
        nxSystemFileInfoMapper.insert(info);
        return info;
    }

    public void delete(Long id) {
        nxSystemFileInfoMapper.deleteById(id);
    }

    public NxSystemFileInfo findById(Long id) {
        return nxSystemFileInfoMapper.selectById(id);
    }

    public Page<NxSystemFileInfo> findPage(Integer pageNum, Integer pageSize) {
        return nxSystemFileInfoMapper.selectPage(new Page<>(pageNum, pageSize), null);
    }
}