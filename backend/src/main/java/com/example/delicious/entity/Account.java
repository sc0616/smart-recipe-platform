package com.example.delicious.entity;

import lombok.Data;

@Data
public class Account {
    private Long id;
    private String name;
    private String password;
    private Integer level;
    private String nickName;
    private String newPassword;
}