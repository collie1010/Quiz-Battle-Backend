package com.example.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class SchedulerConfig {

    // @Bean
    // @Primary 
    // public TaskScheduler taskScheduler() {
    //     ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    //     scheduler.setPoolSize(100); // ⭐ 擴大排程器執行緒池到 100
    //     scheduler.setThreadNamePrefix("game-task-");
    //     scheduler.initialize();
    //     return scheduler;
    // }
}
