package com.civicworks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Scheduling is provided by Quartz (see QuartzJobConfig) with a JDBC job
// store; no @EnableScheduling here so we don't double-run jobs.
@SpringBootApplication
public class CivicWorksApplication {
    public static void main(String[] args) {
        SpringApplication.run(CivicWorksApplication.class, args);
    }
}
