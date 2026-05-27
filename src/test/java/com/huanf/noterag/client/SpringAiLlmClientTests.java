package com.huanf.noterag.client;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.rag.RagPrompt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiLlmClientTests {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final SpringAiLlmClient client = new SpringAiLlmClient(chatModel);

    @Test
    void chatMapsRagPromptToSpringPromptAndReturnsAnswer() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(" answer ")))));

        String answer = client.chat(new RagPrompt("system instruction", "user content"));

        assertThat(answer).isEqualTo("answer");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        Prompt springPrompt = promptCaptor.getValue();
        assertThat(springPrompt.getSystemMessage().getText()).isEqualTo("system instruction");
        assertThat(springPrompt.getUserMessage().getText()).isEqualTo("user content");
    }

    @Test
    void chatWrapsModelFailureAsBusinessException() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("provider down"));

        assertThatThrownBy(() -> client.chat(new RagPrompt("system", "user")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.LLM_FAILED);
                    assertThat(exception).hasMessage("LLM 服务调用失败");
                });
    }

    @Test
    void streamChatSendsDeltasAndReturnsFullAnswer() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(
                        new ChatResponse(List.of(new Generation(new AssistantMessage(" hello")))),
                        new ChatResponse(List.of(new Generation(new AssistantMessage(" world "))))));
        List<String> deltas = new ArrayList<>();

        String answer = client.streamChat(new RagPrompt("system instruction", "user content"), deltas::add);

        assertThat(answer).isEqualTo("hello world");
        assertThat(deltas).containsExactly(" hello", " world ");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(promptCaptor.capture());
        Prompt springPrompt = promptCaptor.getValue();
        assertThat(springPrompt.getSystemMessage().getText()).isEqualTo("system instruction");
        assertThat(springPrompt.getUserMessage().getText()).isEqualTo("user content");
    }

    @Test
    void streamChatIgnoresChunksWithoutText() {
        ChatResponse nullResultResponse = mockResponseWithResult(null);
        ChatResponse nullOutputResponse = mockResponseWithOutput(null);
        ChatResponse nullTextResponse = mockResponseWithText(null);
        ChatResponse emptyTextResponse = mockResponseWithText("");
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(
                        nullResultResponse,
                        nullOutputResponse,
                        nullTextResponse,
                        new ChatResponse(List.of(new Generation(new AssistantMessage(" hello")))),
                        emptyTextResponse,
                        new ChatResponse(List.of(new Generation(new AssistantMessage(" world "))))));
        List<String> deltas = new ArrayList<>();

        String answer = client.streamChat(new RagPrompt("system instruction", "user content"), deltas::add);

        assertThat(answer).isEqualTo("hello world");
        assertThat(deltas).containsExactly(" hello", " world ");
    }

    @Test
    void streamChatRejectsBlankFinalAnswer() {
        ChatResponse nullResultResponse = mockResponseWithResult(null);
        ChatResponse nullOutputResponse = mockResponseWithOutput(null);
        ChatResponse nullTextResponse = mockResponseWithText(null);
        ChatResponse blankTextResponse = mockResponseWithText("   ");
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(
                        nullResultResponse,
                        nullOutputResponse,
                        nullTextResponse,
                        blankTextResponse));

        assertThatThrownBy(() -> client.streamChat(new RagPrompt("system", "user"), ignored -> {
        }))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.LLM_RESULT_INVALID);
                    assertThat(exception).hasMessage("LLM streamed answer must not be blank");
                });
    }

    @Test
    void streamChatContinuesWhenDeltaCallbackFails() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(
                        new ChatResponse(List.of(new Generation(new AssistantMessage("hello ")))),
                        new ChatResponse(List.of(new Generation(new AssistantMessage("world"))))));
        List<String> deltas = new ArrayList<>();

        String answer = client.streamChat(new RagPrompt("system instruction", "user content"), delta -> {
            deltas.add(delta);
            throw new RuntimeException("client disconnected");
        });

        assertThat(answer).isEqualTo("hello world");
        assertThat(deltas).containsExactly("hello ");
    }

    @Test
    void streamChatWrapsModelFailureAsBusinessException() {
        when(chatModel.stream(any(Prompt.class))).thenThrow(new RuntimeException("provider down"));

        assertThatThrownBy(() -> client.streamChat(new RagPrompt("system", "user"), ignored -> {
        }))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.LLM_FAILED);
                    assertThat(exception).hasMessage("LLM 服务调用失败");
                });
    }

    @Test
    void chatRejectsNullResponse() {
        when(chatModel.call(any(Prompt.class))).thenReturn(null);

        assertThatThrownBy(() -> client.chat(new RagPrompt("system", "user")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.LLM_RESULT_INVALID);
                    assertThat(exception).hasMessage("LLM response must not be null");
                });
    }

    @Test
    void chatRejectsNullPrompt() {
        assertThatThrownBy(() -> client.chat(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prompt must not be null");
    }

    private static ChatResponse mockResponseWithResult(Generation generation) {
        ChatResponse response = mock(ChatResponse.class);
        when(response.getResult()).thenReturn(generation);
        return response;
    }

    private static ChatResponse mockResponseWithOutput(AssistantMessage output) {
        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(output);
        return mockResponseWithResult(generation);
    }

    private static ChatResponse mockResponseWithText(String text) {
        AssistantMessage output = mock(AssistantMessage.class);
        when(output.getText()).thenReturn(text);
        return mockResponseWithOutput(output);
    }
}
