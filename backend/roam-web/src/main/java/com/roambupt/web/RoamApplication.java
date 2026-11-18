package com.roambupt.web;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口。
 */
@SpringBootApplication(scanBasePackages = "com.roambupt")
@MapperScan(basePackages = "com.roambupt.service", annotationClass = Mapper.class)
public class RoamApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoamApplication.class, args);
    }
}
