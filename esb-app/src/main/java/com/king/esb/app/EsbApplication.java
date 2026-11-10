package com.king.esb.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Entry point for the King ESB Platform. Scans every {@code com.king.esb} module so the
 * core engine, transports, transformers, governance listeners and admin API are all wired
 * into a single deployable modular monolith.
 */
@SpringBootApplication(scanBasePackages = "com.king.esb")
@EntityScan(basePackages = "com.king.esb.admin.persistence.entity")
@EnableJpaRepositories(basePackages = "com.king.esb.admin.persistence.repository")
public class EsbApplication {

    public static void main(String[] args) {
        SpringApplication.run(EsbApplication.class, args);
    }
}
