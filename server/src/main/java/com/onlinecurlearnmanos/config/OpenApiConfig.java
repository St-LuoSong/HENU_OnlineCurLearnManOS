package com.onlinecurlearnmanos.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI onlineCurLearnManOSOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OnlineCurLearnManOS API")
                        .version("v1")
                        .description("Smart learning platform backend API"));
    }
}