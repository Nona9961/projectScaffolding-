package com.nona;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.nona.annotation.ScaffoldGenerated;

/**
 * 应用入口：Spring Boot 启动类。
 * <p>
 * JPA 仓库限定扫描 {@code com.nona.inf.persistence.repository.jpa} 包。
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.nona.inf.persistence.repository.jpa")
@ScaffoldGenerated
public class ProjectApplication {

    /**
     * 启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ProjectApplication.class, args);
    }
}
