package com.huanf.noterag.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Chat SSE 后台任务线程池配置。
 *
 * <p>SSE 请求会先返回 SseEmitter，再把真正的 chat 主链路投递到该线程池执行。
 * 这样 Servlet 请求线程不会被 LLM 流式生成过程长期占用。</p>
 */
@Configuration
public class ChatSseConfig {

    /**
     * Chat SSE 专用线程池，参数来自 noterag.chat.sse-executor.*。
     */
    @Bean(name = "chatSseTaskExecutor")
    public TaskExecutor chatSseTaskExecutor(ChatSseProperties chatSseProperties) {
        ChatSseProperties.SseExecutor properties = chatSseProperties.getSseExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("chat-sse-");
        // 队列满时直接拒绝，由 controller 返回 SSE error；不要回退到请求线程执行 LLM stream。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
