package com.example.library.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 向所有页面提供全局模型属性。
 * <p>版本号来自配置项 {@code library.version}，需与 {@code pom.xml} 中的 version 保持一致，
 * 页面页脚统一显示该版本号。</p>
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final String appVersion;

    public GlobalModelAttributes(@Value("${library.version:0.1.0}") String appVersion) {
        this.appVersion = appVersion;
    }

    @ModelAttribute("appVersion")
    public String appVersion() {
        return appVersion;
    }
}
