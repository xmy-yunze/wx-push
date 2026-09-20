package com.wxpush;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * wx-push 微信公众号系统启动类。
 *
 * <p>本项目是「用真实项目练 Java 设计模式」的学习载体，
 * P0 阶段目标是打通「微信服务器 ↔ 本服务」的最小链路。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.wxpush.repository.mapper")
public class WxPushApplication {

    public static void main(String[] args) {
        SpringApplication.run(WxPushApplication.class, args);
    }
}
