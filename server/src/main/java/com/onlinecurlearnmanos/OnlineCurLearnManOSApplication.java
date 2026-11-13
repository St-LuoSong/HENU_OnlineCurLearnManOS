package com.onlinecurlearnmanos;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.onlinecurlearnmanos.**.mapper")
public class OnlineCurLearnManOSApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnlineCurLearnManOSApplication.class, args);
    }
}
