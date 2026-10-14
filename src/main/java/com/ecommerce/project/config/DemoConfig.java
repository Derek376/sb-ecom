package com.ecommerce.project.config;

import com.ecommerce.project.service.DemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoConfig {
    @Bean
    CommandLineRunner initializePortfolioDemo(DemoService demoService) {
        return args -> demoService.initialize();
    }
}
