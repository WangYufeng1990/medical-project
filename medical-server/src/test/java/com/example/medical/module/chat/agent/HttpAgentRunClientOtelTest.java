package com.example.medical.module.chat.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.medical.module.chat.dto.agent.AgentRunStartRequest;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round 59.2: the dispatch span comes from a real OTel SDK tracer, is exported through a real
 * exporter pipeline, and the header it sends is that span's context.
 */
class HttpAgentRunClientOtelTest {

    private static final Pattern TRACEPARENT =
            Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-01$");

    private final List<String> traceparents = new CopyOnWriteArrayList<>();
    private final List<SpanData> exported = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private SdkTracerProvider provider;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/runs", exchange -> {
            traceparents.add(exchange.getRequestHeaders().getFirst("traceparent"));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();

        SpanExporter collector = new SpanExporter() {
            @Override
            public CompletableResultCode export(Collection<SpanData> spans) {
                exported.addAll(spans);
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };
        provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(collector))
                .build();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        provider.close();
    }

    @Test
    void the_exported_span_is_the_parent_of_the_agent_headers() {
        AgentDispatchProperties properties = new AgentDispatchProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setOutboundToken("outbound");
        properties.setRequestTimeout(Duration.ofSeconds(2));
        HttpAgentRunClient client = new HttpAgentRunClient(properties);
        client.setTracer(new OtelTracer(provider.get("test"),
                new OtelCurrentTraceContext(), null));

        client.start(new AgentRunStartRequest("v1", 160L, 1600L, 100L, 1L, "PATIENT", 1L,
                "Can I move my appointment?", "en", "req-otel-160"), "cap");

        assertThat(traceparents).hasSize(1);
        var match = TRACEPARENT.matcher(traceparents.get(0));
        assertThat(match.matches()).isTrue();
        assertThat(exported).anySatisfy(span -> {
            assertThat(span.getName()).isEqualTo("agent.dispatch");
            assertThat(span.getTraceId()).isEqualTo(match.group(1));
            assertThat(span.getSpanId()).isEqualTo(match.group(2));
            assertThat(span.hasEnded()).isTrue();
        });
    }
}
