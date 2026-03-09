package com.ragadmin.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RagAdminServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagAdminServerApplication.class, args);
    }
}
