package com.example.delicious.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("classify_info")
public class ClassifyInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
}