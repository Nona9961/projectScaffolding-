package com.nona;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.nona.annotation.ScaffoldGenerated;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.nona.inf.persistence.repository.jpa")
@ScaffoldGenerated
public class ProjectApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProjectApplication.class, args);
    }
}
