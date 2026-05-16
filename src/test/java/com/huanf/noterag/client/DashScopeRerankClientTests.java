package com.huanf.noterag.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.RerankProperties;

class DashScopeRerankClientTests {

    @Test
    void rerankPostsDashScopeRequestAndMapsResults() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeRerankClient client = new DashScopeRerankClient(builder, properties("test-key"));
        server.expect(once(), requestTo("https://dashscope.aliyuncs.com/compatible-api/v1/reranks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("qwen3-rerank"))
                .andExpect(jsonPath("$.documents[0]").value("first"))
                .andExpect(jsonPath("$.documents[1]").value("second"))
                .andExpect(jsonPath("$.query").value("what is JVM?"))
                .andExpect(jsonPath("$.top_n").value(2))
                .andExpect(jsonPath("$.instruct").value("instruction"))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {"index": 1, "relevance_score": 0.98},
                            {"index": 0, "relevance_score": 0.76}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RerankResult> results = client.rerank(
                "what is JVM?",
                List.of("first", "second"),
                2,
                "instruction");

        assertThat(results).containsExactly(
                new RerankResult(1, 0.98),
                new RerankResult(0, 0.76));
        server.verify();
    }

    @Test
    void rerankWrapsProviderFailureAsBusinessException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeRerankClient client = new DashScopeRerankClient(builder, properties("test-key"));
        server.expect(once(), requestTo("https://dashscope.aliyuncs.com/compatible-api/v1/reranks"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.rerank("question", List.of("document"), 1, "instruction"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.RERANK_FAILED);
                    assertThat(exception).hasMessage("Rerank 服务调用失败");
                });
        server.verify();
    }

    private static RerankProperties properties(String apiKey) {
        RerankProperties properties = new RerankProperties();
        properties.setApiKey(apiKey);
        properties.setBaseUrl("https://dashscope.aliyuncs.com/compatible-api/v1");
        properties.setModel("qwen3-rerank");
        return properties;
    }
}
