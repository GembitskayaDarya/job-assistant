package com.darya.jobassistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI jobAssistantOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Job Assistant API")
                        .description("REST API for tracking companies, vacancies, applications, and interviews.")
                        .version("v0.0.1"));
    }
}
