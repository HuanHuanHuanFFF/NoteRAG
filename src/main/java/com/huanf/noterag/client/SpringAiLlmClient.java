package com.huanf.noterag.client;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.rag.RagPrompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Objects;

public class SpringAiLlmClient implements LlmClient {

    private static final String LLM_FAILED_MESSAGE = "LLM 服务调用失败";

    private final ChatModel chatModel;

    public SpringAiLlmClient(ChatModel chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel must not be null");
    }

    @Override
    public String chat(RagPrompt prompt) {
        validatePrompt(prompt);

        Prompt springPrompt = new Prompt(List.of(
                new SystemMessage(prompt.system()),
                new UserMessage(prompt.user())));

        try {
            ChatResponse response = chatModel.call(springPrompt);
            return extractAnswer(response);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(CodeStatus.LLM_FAILED, LLM_FAILED_MESSAGE, ex);
        }
    }

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

    private String extractAnswer(ChatResponse response) {
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
}
