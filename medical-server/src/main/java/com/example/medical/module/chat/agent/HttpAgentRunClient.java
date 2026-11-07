package com.example.medical.module.chat.agent;

import com.example.medical.module.chat.dto.agent.AgentRunStartRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
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

    /**
     * Optional: the OTel bridge provides it in the application. Without it (unit tests, a build
     * without tracing configured) the client still sends a valid header, just without a span.
     */
    @Nullable
    private Tracer tracer;

    @Autowired(required = false)
    void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void start(AgentRunStartRequest request, String capability) {
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
        if (properties.getOutboundToken() != null && !properties.getOutboundToken().isBlank()) {
            headers.setBearerAuth(properties.getOutboundToken());
        }
        headers.set("X-Agent-Capability", capability);
        // The header comes from the dispatch span when tracing is configured, so the agent's node
        // spans are children of this one (Round 59.2). Without a span the id is still valid.
        Span span = tracer == null ? null : tracer.nextSpan().name("agent.dispatch").start();
        String traceparent = span == null ? newTraceparent() : traceparentFor(span);
        headers.set("traceparent", traceparent);
        long startedAt = System.nanoTime();
        try {
            restTemplate.postForEntity(trimTrailingSlash(baseUrl) + "/internal/v1/runs",
                    new HttpEntity<>(request, headers), Void.class);
        } catch (HttpStatusCodeException answered) {
            // Status only. The body may echo the rejected input, and that input is patient
            // text: it must not reach the queue's error column or the logs.
            logDispatch(request, traceparent, startedAt, "http_" + answered.getStatusCode().value());
            endSpan(span);
            throw new AgentDispatchException("AGENT_HTTP_" + answered.getStatusCode().value(),
                    "agent service answered " + answered.getStatusCode().value());
        } catch (ResourceAccessException unreachable) {
            logDispatch(request, traceparent, startedAt, "unreachable");
            endSpan(span);
            throw new AgentDispatchException("AGENT_UNREACHABLE", "agent service could not be reached");
        } catch (RestClientException failure) {
            logDispatch(request, traceparent, startedAt, "client_error");
            endSpan(span);
            throw new AgentDispatchException("AGENT_CLIENT_ERROR", "the agent request could not be completed");
        }
        logDispatch(request, traceparent, startedAt, "ok");
        endSpan(span);
    }

    /**
     * Success and failure both carry the trace id, its duration and an outcome class, so a dispatch
     * that the agent received but Java never saw answered can still be joined to its spans. Nothing
     * from the request or the response body is logged (Round 59.2 review).
     */
    private static void endSpan(@Nullable Span span) {
        if (span != null) {
            span.end();
        }
    }

    private static String traceparentFor(Span span) {
        return "00-" + span.context().traceId() + "-" + span.context().spanId() + "-01";
    }

    private void logDispatch(AgentRunStartRequest request, String traceparent, long startedAt,
                             String outcome) {
        log.debug("Agent dispatch run={} traceparent={} durationMs={} outcome={}", request.runId(),
                traceparent, (System.nanoTime() - startedAt) / 1_000_000, outcome);
    }

    private static String newTraceparent() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] trace = new byte[16];
        byte[] span = new byte[8];
        random.nextBytes(trace);
        random.nextBytes(span);
        return "00-" + java.util.HexFormat.of().formatHex(trace) + "-"
                + java.util.HexFormat.of().formatHex(span) + "-01";
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
