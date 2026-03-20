package com.nona;

import com.nona.inf.persistence.repository.jpa.TenantAwareJpaRepositoryImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(
        basePackages = "com.nona.inf.persistence.repository.jpa",
        repositoryBaseClass = TenantAwareJpaRepositoryImpl.class
)
public class ProjectApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProjectApplication.class, args);
    }
}
