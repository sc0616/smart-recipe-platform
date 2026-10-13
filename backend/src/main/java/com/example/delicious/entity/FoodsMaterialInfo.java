package com.example.delicious.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("foods_material_info")
public class FoodsMaterialInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String fileName;
    private Long fileId;
    private String userName;
    private Integer level;
    private Long uploadUserId;
}