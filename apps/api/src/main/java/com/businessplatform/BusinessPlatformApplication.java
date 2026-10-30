package com.businessplatform;

import java.util.TimeZone;

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

    // The JVM runs in UTC everywhere. Hibernate turns a time of day (TIME columns: schedules, timetables) into
    // UTC through the JVM's zone, so on a machine set to another zone 06:00 would be stored as 23:00. Local dates
    // and times use the organisation's zone explicitly (CalendarService.ZONE), never the JVM's.
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(BusinessPlatformApplication.class, args);
    }
}
