package com.roambupt.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口。
 */
@SpringBootApplication(scanBasePackages = "com.roambupt")
public class RoamApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoamApplication.class, args);
    }
}
