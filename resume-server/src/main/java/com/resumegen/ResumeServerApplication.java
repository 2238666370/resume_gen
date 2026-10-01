package com.resumegen;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.resumegen.mapper")
public class ResumeServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeServerApplication.class, args);
    }
}