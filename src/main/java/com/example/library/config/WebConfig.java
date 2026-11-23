package com.example.library.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.library.service.CoverStorage;

/**
 * 把上传的图书封面目录映射到 {@code /covers/**}，供页面直接访问。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CoverStorage covers;

    public WebConfig(CoverStorage covers) {
        this.covers = covers;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = covers.getRoot().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(CoverStorage.URL_PREFIX + "**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
