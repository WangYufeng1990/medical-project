package com.example.medical.module.chat.agent;

import com.example.medical.module.chat.dto.agent.AgentRunStartRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Calls {@code POST /internal/v1/runs} on the agent service (Round 55.2).
 * <p>
 * The request carries the configured service token; the directional credentials and
 * per-task capabilities that make that token meaningful are Round 55.3. Timeouts are
 * bounded here so a hung provider cannot hold a dispatcher thread open.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpAgentRunClient implements AgentRunClient {

    private final AgentDispatchProperties properties;

    @Override
    public void start(AgentRunStartRequest request) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.agent.base-url is not configured");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getRequestTimeout().toMillis());
        factory.setReadTimeout((int) properties.getRequestTimeout().toMillis());
        RestTemplate restTemplate = new RestTemplate(factory);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getServiceToken() != null && !properties.getServiceToken().isBlank()) {
            headers.setBearerAuth(properties.getServiceToken());
        }
        try {
            restTemplate.postForEntity(trimTrailingSlash(baseUrl) + "/internal/v1/runs",
                    new HttpEntity<>(request, headers), Void.class);
        } catch (HttpStatusCodeException answered) {
            // Status only. The body may echo the rejected input, and that input is patient
            // text: it must not reach the queue's error column or the logs.
            throw new AgentDispatchException("AGENT_HTTP_" + answered.getStatusCode().value(),
                    "agent service answered " + answered.getStatusCode().value());
        } catch (ResourceAccessException unreachable) {
            throw new AgentDispatchException("AGENT_UNREACHABLE", "agent service could not be reached");
        } catch (RestClientException failure) {
            throw new AgentDispatchException("AGENT_CLIENT_ERROR", "the agent request could not be completed");
        }
        log.debug("Agent run {} dispatched", request.runId());
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
