package com.example.delicious;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.delicious.mapper")
public class DeliciousApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeliciousApplication.class, args);
    }
}