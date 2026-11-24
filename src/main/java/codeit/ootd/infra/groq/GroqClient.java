package codeit.ootd.infra.groq;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@Slf4j
public class GroqClient {

    private static final int MAX_COMPLETION_TOKENS = 768;
    private static final int MAX_EXPLANATION_LENGTH = 500;
    private static final int RATE_LIMIT_STATUS = 429;
    private static final double TEMPERATURE = 0.2;
    private static final Pattern KOREAN = Pattern.compile("[가-힣]");
    private static final Pattern RATE_LIMIT = Pattern.compile("Limit\\s+(\\d+)");
    private static final Map<String, Object> RESPONSE_FORMAT = Map.of(
        "type", "json_schema", "json_schema", Map.of(
            "name", "outfit_explanation", "strict", true,
            "schema", Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of("explanation", Map.of("type", "string"),
                    "documentKey", Map.of("type", "string"),
                    "matchedClothesIds", Map.of("type", "array", "items", Map.of("type", "string"))),
                "required", List.of("explanation", "documentKey", "matchedClothesIds"))));

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final boolean enabled;
    private final String apiKey;
    private final String model;

    public GroqClient(@Qualifier("groqRestClient") RestClient restClient, JsonMapper jsonMapper,
        @Value("${ootd.groq.enabled:false}") boolean enabled,
        @Value("${ootd.groq.api-key:}") String apiKey,
        @Value("${ootd.groq.model:openai/gpt-oss-20b}") String model) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
        this.enabled = enabled;
        this.apiKey = apiKey.strip();
        this.model = model;
    }

    /**
     * 기능이 활성화되고 API 키가 설정된 경우에만 외부 호출이 가능하다.
     */
    public boolean isConfigured() {
        return enabled && !apiKey.isBlank();
    }

    /**
     * 한 번의 비스트리밍 요청으로 JSON 스키마 응답을 받고 완료 여부·필수 값·한국어 설명을 확인한다.
     * 재시도하지 않아 한도 초과 시 추가 요청을 발생시키지 않으며 HTTP 오류는 호출 서비스에 전달한다.
     */
    public GroqExplanation generate(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("Groq가 설정되지 않았습니다.");
        }
        ResponseEntity<JsonNode> entity;
        try {
            entity = restClient.post().uri("/chat/completions")
            .headers(headers -> headers.setBearerAuth(apiKey))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("model", model, "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)),
                "response_format", RESPONSE_FORMAT, "reasoning_effort", "low",
                "max_completion_tokens", MAX_COMPLETION_TOKENS, "temperature", TEMPERATURE,
                "stream", false))
            .retrieve().toEntity(JsonNode.class);
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().value() == RATE_LIMIT_STATUS) {
                logRateLimit(exception);
            }
            throw exception;
        }
        log.info("Groq quota model={} tpm={} rpd={} remainingTokens={} remainingRequests={}", model,
            entity.getHeaders().getFirst("x-ratelimit-limit-tokens"),
            entity.getHeaders().getFirst("x-ratelimit-limit-requests"),
            entity.getHeaders().getFirst("x-ratelimit-remaining-tokens"),
            entity.getHeaders().getFirst("x-ratelimit-remaining-requests"));
        JsonNode response = entity.getBody();

        JsonNode choices = response == null ? null : response.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()
            || !"stop".equals(choices.get(0).path("finish_reason").asString(""))
            || response.hasNonNull("error") || choices.get(0).hasNonNull("error")
            || choices.get(0).path("message").hasNonNull("refusal")) {
            throw new IllegalStateException("Groq 응답이 정상적으로 완료되지 않았습니다.");
        }
        JsonNode content = choices.get(0).path("message").path("content");
        if (!content.isString()) {
            throw new IllegalStateException("Groq 설명이 없습니다.");
        }
        GroqExplanation result;
        try {
            result = jsonMapper.readValue(content.stringValue(), GroqExplanation.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Groq 설명 형식이 올바르지 않습니다.");
        }
        if (result == null || result.explanation() == null || result.explanation().isBlank()
            || result.explanation().length() > MAX_EXPLANATION_LENGTH
            || !KOREAN.matcher(result.explanation()).find()
            || result.documentKey() == null || result.documentKey().isBlank()
            || result.matchedClothesIds() == null || result.matchedClothesIds().isEmpty()
            || result.matchedClothesIds().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalStateException("Groq 설명의 필수 값이 올바르지 않습니다.");
        }
        return result;
    }

    /** 한도 오류 원문·조직 ID·키를 출력하지 않고 제한 종류와 숫자만 기록한다. */
    private void logRateLimit(HttpClientErrorException exception) {
        try {
            String message = jsonMapper.readTree(exception.getResponseBodyAsString()).path("error").path("message").asString("");
            String dimension = message.contains("tokens per minute") ? "TPM"
                : message.contains("requests per minute") ? "RPM"
                : message.contains("tokens per day") ? "TPD"
                : message.contains("requests per day") ? "RPD" : "UNKNOWN";
            Matcher limit = RATE_LIMIT.matcher(message);
            log.warn("Groq rate limit model={} dimension={} limit={}", model, dimension,
                limit.find() ? limit.group(1) : "unknown");
        } catch (JacksonException exceptionBody) {
            log.warn("Groq rate limit model={} dimension=UNKNOWN", model);
        }
    }
}
