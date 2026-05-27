package com.huanf.noterag.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Chat SSE 流式响应配置。
 *
 * <p>这些参数和部署资源、并发流式请求数量直接相关，所以通过环境变量暴露，避免调参时重新打包。</p>
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "noterag.chat")
public class ChatSseProperties {

    /**
     * 单个 SSE 连接的超时时间。超时只关闭事件发送侧，不取消后端 chat 生成和落库。
     */
    @Min(value = 1000, message = "noterag.chat.sse-timeout-ms must be at least 1000")
    private long sseTimeoutMs = 600_000L;

    @Valid
    private SseExecutor sseExecutor = new SseExecutor();

    /**
     * Chat SSE 后台任务线程池参数。
     */
    @Getter
    @Setter
    public static class SseExecutor {

        /**
         * 常驻线程数，用于承载基础并发的流式 chat 任务。
         */
        @Min(value = 1, message = "noterag.chat.sse-executor.core-pool-size must be at least 1")
        private int corePoolSize = 2;

        /**
         * 最大线程数，用于短时间并发升高时扩容。
         */
        @Min(value = 1, message = "noterag.chat.sse-executor.max-pool-size must be at least 1")
        private int maxPoolSize = 8;

        /**
         * 等待执行的 SSE chat 任务队列容量。
         */
        @Min(value = 1, message = "noterag.chat.sse-executor.queue-capacity must be at least 1")
        private int queueCapacity = 100;

        /**
         * 防止配置成 maxPoolSize 小于 corePoolSize，避免启动后线程池参数非法。
         */
        @AssertTrue(message = "noterag.chat.sse-executor.max-pool-size must be greater than or equal to core-pool-size")
        public boolean isMaxPoolSizeValid() {
            return maxPoolSize >= corePoolSize;
        }
    }
}
