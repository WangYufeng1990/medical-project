package com.example.medical.module.chat.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.medical.module.chat.dto.agent.AgentRunStartRequest;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Round 59.2: one real dispatch to a running agent, with real OTLP export to a local collector.
 * Opt-in because it needs both processes; the assertions on the collected spans are made in Python.
 */
@EnabledIfEnvironmentVariable(named = "AGENT_LIVE_URL", matches = ".+")
class HttpAgentRunClientLiveTest {

    @Test
    void a_real_dispatch_exports_a_span_to_the_collector() throws java.io.IOException {
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(System.getenv("AGENT_COLLECTOR_URL"))
                .build();
        String spanFile = System.getenv().getOrDefault("AGENT_SPAN_FILE", "/tmp/java-span.json");
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(spanFile));
        } catch (java.io.IOException ignored) {
            // the export is best effort in this test as well
        }
        SpanExporter recorder = new SpanExporter() {
            @Override
            public CompletableResultCode export(Collection<SpanData> spans) {
                try (var out = java.nio.file.Files.newBufferedWriter(
                        java.nio.file.Path.of(spanFile),
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND)) {
                    for (SpanData span : spans) {
                        out.write("{\"name\":\"" + span.getName() + "\",\"traceId\":\""
                                + span.getTraceId() + "\",\"spanId\":\"" + span.getSpanId() + "\"}\n");
                    }
                } catch (java.io.IOException ignored) {
                    // best effort, like every other exporter here
                }
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
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .addSpanProcessor(io.opentelemetry.sdk.trace.export.SimpleSpanProcessor.create(recorder))
                .build();
        try {
            AgentDispatchProperties properties = new AgentDispatchProperties();
            properties.setBaseUrl(System.getenv("AGENT_LIVE_URL"));
            properties.setOutboundToken(System.getenv().getOrDefault("AGENT_INBOUND_TOKEN", "x"));
            properties.setRequestTimeout(Duration.ofSeconds(10));
            HttpAgentRunClient client = new HttpAgentRunClient(properties);
            client.setTracer(new OtelTracer(provider.get("live"), new OtelCurrentTraceContext(), null));

            client.start(new AgentRunStartRequest("v1", 180L, 1800L, 100L, 1L, "PATIENT", 1L,
                    "PLEASE ROLL OUT canary-trace-7f3a and move my appointment?", "en", "req-live-180"), "live-cap");
        } finally {
            provider.close();
        }
        var line = java.nio.file.Files.readAllLines(
                java.nio.file.Path.of(spanFile)).get(0);
        assertThat(line).containsPattern("\"traceId\":\"[0-9a-f]{32}\"");
        assertThat(line).containsPattern("\"spanId\":\"[0-9a-f]{16}\"");
        assertThat(line).contains("agent.dispatch");
    }
}
