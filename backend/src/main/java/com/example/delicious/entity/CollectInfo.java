package com.example.delicious.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("collect_info")
public class CollectInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String time;
    private Long foodsId;
    private Long userId;
    private Integer level;
}