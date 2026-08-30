package com.featureflagplatform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Talks to a local model through Docker Model Runner's OpenAI-compatible
 * chat completions endpoint. Uses {@link HttpClient} from the JDK standard
 * library rather than adding a WebClient/RestTemplate/OkHttp dependency —
 * one HTTP call, no streaming, no connection pooling needs beyond what the
 * JDK already provides.
 *
 * <p>Runs entirely on the developer's machine: no API key, no per-request
 * cost, no data leaving the host. See .claude/decisions/ADR-003 for the
 * model choice and why {@link MockAiProvider} stays the default rather than
 * this.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "docker-model-runner")
public class DockerModelRunnerAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(DockerModelRunnerAiProvider.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;

    public DockerModelRunnerAiProvider(
            ObjectMapper objectMapper,
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.model}") String model,
            @Value("${app.ai.timeout-ms}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String requestBody = buildRequestBody(systemPrompt, userPrompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replaceAll("/$", "") + "/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new AiProviderException(AiFailureReason.TIMEOUT, "Docker Model Runner did not respond in time", e);
        } catch (ConnectException e) {
            throw new AiProviderException(AiFailureReason.CONNECTION_REFUSED,
                    "Could not connect to Docker Model Runner at " + baseUrl, e);
        } catch (IOException e) {
            throw new AiProviderException(AiFailureReason.PROVIDER_ERROR, "I/O error calling Docker Model Runner", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(AiFailureReason.PROVIDER_ERROR, "Interrupted while calling Docker Model Runner", e);
        }

        if (response.statusCode() == 429) {
            throw new AiProviderException(AiFailureReason.RATE_LIMITED, "Docker Model Runner reported rate limiting");
        }
        if (response.statusCode() / 100 != 2) {
            log.warn("Docker Model Runner returned HTTP {}: {}", response.statusCode(), truncate(response.body()));
            throw new AiProviderException(AiFailureReason.PROVIDER_ERROR,
                    "Docker Model Runner returned HTTP " + response.statusCode());
        }

        String content = extractContent(response.body());
        if (content == null || content.isBlank()) {
            throw new AiProviderException(AiFailureReason.EMPTY_RESPONSE, "Docker Model Runner returned an empty completion");
        }
        return content;
    }

    private String buildRequestBody(String systemPrompt, String userPrompt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", 0);
        var messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);
        // Best-effort structured output: honored by models/backends that support it,
        // harmlessly ignored otherwise (the caller still defensively extracts JSON
        // from prose either way — see AiRuleAssistantService).
        root.putObject("response_format").put("type", "json_object");
        return root.toString();
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("choices").path(0).path("message").path("content");
            return message.isMissingNode() ? null : message.asText();
        } catch (IOException e) {
            throw new AiProviderException(AiFailureReason.PROVIDER_ERROR,
                    "Docker Model Runner response was not valid JSON", e);
        }
    }

    private static String truncate(String text) {
        return text == null ? "" : text.substring(0, Math.min(text.length(), 500));
    }
}
