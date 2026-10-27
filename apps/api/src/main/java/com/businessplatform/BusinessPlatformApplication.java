package com.businessplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/** Business Platform API: shared core (identity, organisations, projects, workflow, calendar) and modules. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableJpaRepositories(considerNestedRepositories = true)
public class BusinessPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessPlatformApplication.class, args);
    }
}
