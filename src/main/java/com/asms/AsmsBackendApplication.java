package com.asms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the ASMS (Assignment Management System) REST API.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AsmsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsmsBackendApplication.class, args);
    }
}
