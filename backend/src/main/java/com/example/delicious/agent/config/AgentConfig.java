package com.example.delicious.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent 基础设施装配：注册配置属性、SSE 流式输出专用线程池。
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

    /**
     * 用于执行 Agent 推理与 SSE 推送的后台线程池。
     * 使用守护线程，避免阻塞应用正常关闭。
     */
    @Bean(name = "agentExecutor")
    public ExecutorService agentExecutor() {
        return Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "agent-worker");
            t.setDaemon(true);
            return t;
        });
    }
}
