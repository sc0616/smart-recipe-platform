package com.example.delicious.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("nx_system_file_info")
public class NxSystemFileInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String originName;
    private String fileName;
}