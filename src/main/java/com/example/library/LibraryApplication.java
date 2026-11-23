package com.example.library;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LibraryApplication {

    public static void main(String[] args) throws Exception {
        // SQLite 不会自动创建目录，启动前确保 data/ 存在
        Files.createDirectories(Path.of("data"));
        SpringApplication.run(LibraryApplication.class, args);
    }
}
