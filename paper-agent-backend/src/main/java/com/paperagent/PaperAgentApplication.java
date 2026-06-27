package com.paperagent;

import com.paperagent.service.TemplateService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class PaperAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperAgentApplication.class, args);
    }

    @Bean
    CommandLineRunner seedTemplates(TemplateService templateService) {
        return args -> templateService.seedBuiltinTemplates();
    }
}
