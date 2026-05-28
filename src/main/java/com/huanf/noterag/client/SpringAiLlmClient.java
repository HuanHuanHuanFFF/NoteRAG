package com.huanf.noterag.client;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.rag.RagPrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 基于 Spring AI ChatModel 的 LLM 客户端适配器。
 *
 * <p>该类只负责把 NoteRAG 内部的 RagPrompt 转成 Spring AI Prompt，并统一处理同步/流式模型调用异常。
 * prompt 构造、citation 协议和 answer sources 过滤仍由 NoteRAG 的 rag/service 层显式控制。</p>
 */
@Slf4j
public class SpringAiLlmClient implements LlmClient {

    private static final String LLM_FAILED_MESSAGE = "LLM 服务调用失败";

    private final ChatModel chatModel;

    public SpringAiLlmClient(ChatModel chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel must not be null");
    }

    /**
     * 执行非流式 LLM 调用，返回完整 answer 文本。
     */
    @Override
    public String chat(RagPrompt prompt) {
        validatePrompt(prompt);

        Prompt springPrompt = toSpringPrompt(prompt);

        long startNanos = System.nanoTime();
        log.info("LLM 调用开始, systemPromptLength={}, userPromptLength={}",
                prompt.system().length(), prompt.user().length());
        try {
            ChatResponse response = chatModel.call(springPrompt);
            String answer = extractFullAnswer(response);
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info("LLM 调用完成, answerLength={}, elapsedMs={}", answer.length(), elapsedMs);
            return answer;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("LLM 调用失败, systemPromptLength={}, userPromptLength={}", prompt.system().length(), prompt.user().length(), ex);
            throw new BusinessException(CodeStatus.LLM_FAILED, LLM_FAILED_MESSAGE, ex);
        }
    }

    /**
     * 执行流式 LLM 调用，把每段非空 delta 交给调用方，同时拼出完整 answer 返回。
     *
     * <p>这里使用 Spring AI 通用 ChatModel.stream(Prompt)，避免和具体 OpenAI/DashScope 实现类绑定。</p>
     */
    @Override
    public String streamChat(RagPrompt prompt, Consumer<String> onDelta) {
        validatePrompt(prompt);
        Objects.requireNonNull(onDelta, "onDelta must not be null");

        Prompt springPrompt = toSpringPrompt(prompt);
        StringBuilder answer = new StringBuilder();
        AtomicBoolean deltaCallbackEnabled = new AtomicBoolean(true);
        long startNanos = System.nanoTime();
        log.info("LLM 流式调用开始, systemPromptLength={}, userPromptLength={}",
                prompt.system().length(), prompt.user().length());
        try {
            chatModel.stream(springPrompt)
                    .doOnNext(response -> {
                        String delta = extractDeltaText(response);
                        if (!delta.isEmpty()) {
                            answer.append(delta);
                            notifyDelta(delta, onDelta, deltaCallbackEnabled);
                        }
                    })
                    .blockLast();
            String finalAnswer = answer.toString().strip();
            if (finalAnswer.isEmpty()) {
                throw new BusinessException(CodeStatus.LLM_RESULT_INVALID, "LLM streamed answer must not be blank");
            }
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info("LLM 流式调用完成, answerLength={}, elapsedMs={}", finalAnswer.length(), elapsedMs);
            return finalAnswer;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("LLM 流式调用失败, systemPromptLength={}, userPromptLength={}",
                    prompt.system().length(), prompt.user().length(), ex);
            throw new BusinessException(CodeStatus.LLM_FAILED, LLM_FAILED_MESSAGE, ex);
        }
    }

    /**
     * 通知调用方本次 delta。回调失败只停止后续通知，不能中断模型流消费和完整 answer 拼接。
     */
    private void notifyDelta(String delta, Consumer<String> onDelta, AtomicBoolean deltaCallbackEnabled) {
        if (!deltaCallbackEnabled.get()) {
            return;
        }
        try {
            onDelta.accept(delta);
        } catch (RuntimeException ex) {
            if (deltaCallbackEnabled.compareAndSet(true, false)) {
                log.warn("LLM delta 回调失败，后续 delta 将不再通知调用方", ex);
            }
        }
    }

    /**
     * 将 NoteRAG 的 system/user prompt 转成 Spring AI 消息结构。
     */
    private Prompt toSpringPrompt(RagPrompt prompt) {
        return new Prompt(List.of(
                new SystemMessage(prompt.system()),
                new UserMessage(prompt.user())));
    }

    /**
     * 校验内部生成的 prompt，避免把空 prompt 传给外部模型。
     */
    private void validatePrompt(RagPrompt prompt) {
        if (prompt == null) {
            throw new IllegalArgumentException("prompt must not be null");
        }
        if (prompt.system() == null || prompt.system().isBlank()) {
            throw new IllegalArgumentException("prompt.system must not be null or blank");
        }
        if (prompt.user() == null || prompt.user().isBlank()) {
            throw new IllegalArgumentException("prompt.user must not be null or blank");
        }
    }

    /**
     * 从非流式 Spring AI 响应中取出完整回答；结构缺失属于上游返回异常。
     */
    private String extractFullAnswer(ChatResponse response) {
        if (response == null) {
            throw new BusinessException(CodeStatus.LLM_RESULT_INVALID, "LLM response must not be null");
        }
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null) {
            throw new BusinessException(CodeStatus.LLM_RESULT_INVALID, "LLM response result must not be null");
        }
        String answer = generation.getOutput().getText();
        if (answer == null) {
            throw new BusinessException(CodeStatus.LLM_RESULT_INVALID, "LLM answer must not be null");
        }
        return answer.strip();
    }

    /**
     * 从流式 Spring AI chunk 中取出文本增量；metadata-only chunk 没有文本是正常情况。
     */
    private String extractDeltaText(ChatResponse response) {
        if (response == null) {
            return "";
        }
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null) {
            return "";
        }
        String delta = generation.getOutput().getText();
        return delta == null ? "" : delta;
    }
}
