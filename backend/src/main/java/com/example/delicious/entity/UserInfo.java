package com.example.delicious.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_info")
public class UserInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String password;
    private String nickName;
    private String sex;
    private Integer age;
    private String birthday;
    private String phone;
    private String address;
    private String email;
    private String cardId;
    private Integer level;
}