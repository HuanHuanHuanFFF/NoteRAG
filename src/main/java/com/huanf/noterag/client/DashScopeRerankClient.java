package com.huanf.noterag.client;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.RerankProperties;

@Component
public class DashScopeRerankClient implements RerankClient {

    private static final String RERANKS_PATH = "/reranks";
    private static final String RERANK_FAILED_MESSAGE = "Rerank 服务调用失败";

    private final RestClient restClient;
    private final RerankProperties rerankProperties;

    public DashScopeRerankClient(RestClient.Builder restClientBuilder, RerankProperties rerankProperties) {
        this.restClient = Objects.requireNonNull(restClientBuilder, "restClientBuilder must not be null").build();
        this.rerankProperties = Objects.requireNonNull(rerankProperties, "rerankProperties must not be null");
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topK, String instruct) {
        validateRequest(query, documents, topK);

        RerankRequest request = new RerankRequest(
                rerankProperties.getModel(),
                documents,
                query,
                topK,
                instruct);

        try {
            RerankResponse response = restClient.post()
                    .uri(normalizedReranksUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + rerankProperties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RerankResponse.class);
            return toResults(response);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(CodeStatus.RERANK_FAILED, RERANK_FAILED_MESSAGE, ex);
        }
    }

    private void validateRequest(String query, List<String> documents, int topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be null or blank");
        }
        if (documents == null) {
            throw new IllegalArgumentException("documents must not be null");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0");
        }
    }

    private List<RerankResult> toResults(RerankResponse response) {
        if (response == null || response.results() == null) {
            throw new BusinessException(CodeStatus.RERANK_RESULT_INVALID, "Rerank response results must not be null");
        }
        return response.results()
                .stream()
                .map(result -> new RerankResult(result.index(), result.relevanceScore()))
                .toList();
    }

    private String normalizedReranksUrl() {
        String baseUrl = rerankProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(CodeStatus.RERANK_CONFIG_INVALID, "noterag.rerank.base-url must not be blank");
        }
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) + RERANKS_PATH
                : baseUrl + RERANKS_PATH;
    }

    private record RerankRequest(
            String model,
            List<String> documents,
            String query,
            @JsonProperty("top_n") int topN,
            String instruct
    ) {
    }

    private record RerankResponse(List<RerankResponseItem> results) {
    }

    private record RerankResponseItem(
            int index,
            @JsonProperty("relevance_score") double relevanceScore
    ) {
    }
}
