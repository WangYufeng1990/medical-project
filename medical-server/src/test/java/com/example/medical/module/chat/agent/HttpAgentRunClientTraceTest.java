package com.example.medical.module.chat.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.medical.module.chat.dto.agent.AgentRunStartRequest;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** The dispatch carries a valid W3C traceparent and a new one per call (Round 59.2). */
class HttpAgentRunClientTraceTest {

    private static final Pattern TRACEPARENT =
            Pattern.compile("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$");

    private final List<String> traceparents = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private HttpAgentRunClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/runs", exchange -> {
            traceparents.add(exchange.getRequestHeaders().getFirst("traceparent"));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();

        AgentDispatchProperties properties = new AgentDispatchProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setOutboundToken("outbound");
        properties.setRequestTimeout(Duration.ofSeconds(2));
        client = new HttpAgentRunClient(properties);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void every_dispatch_sends_a_valid_new_traceparent() {
        client.start(request(151), "cap-1");
        client.start(request(152), "cap-2");

        assertThat(traceparents).hasSize(2);
        assertThat(traceparents).allMatch(value -> TRACEPARENT.matcher(value).matches());
        assertThat(traceparents.get(0)).isNotEqualTo(traceparents.get(1));
    }

    @Test
    void a_failed_dispatch_still_logs_its_trace_id() {
        server.removeContext("/internal/v1/runs");
        server.createContext("/internal/v1/runs", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        Logger logger = (Logger) LoggerFactory.getLogger(HttpAgentRunClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.start(request(153), "cap"))
                    .isInstanceOf(AgentDispatchException.class)
                    .hasMessageContaining("500");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }

        assertThat(appender.list).isNotEmpty();
        String message = appender.list.get(appender.list.size() - 1).getFormattedMessage();
        assertThat(message).contains("outcome=http_500");
        assertThat(message).containsPattern("traceparent=00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(message).doesNotContain("Can I move my appointment?");
    }

    @Test
    void the_header_comes_from_the_dispatch_span_when_tracing_is_configured() {
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(context.traceId()).thenReturn("c".repeat(32));
        when(context.spanId()).thenReturn("d".repeat(16));
        when(span.context()).thenReturn(context);
        when(span.name("agent.dispatch")).thenReturn(span);
        when(span.start()).thenReturn(span);
        Tracer tracer = mock(Tracer.class);
        when(tracer.nextSpan()).thenReturn(span);
        client.setTracer(tracer);

        client.start(request(154), "cap");

        assertThat(traceparents.get(0)).isEqualTo(
                "00-" + "c".repeat(32) + "-" + "d".repeat(16) + "-01");
        verify(span).end();
    }

    @Test
    void a_failed_dispatch_marks_the_span_and_ends_it_once() {
        server.removeContext("/internal/v1/runs");
        server.createContext("/internal/v1/runs", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(context.traceId()).thenReturn("c".repeat(32));
        when(context.spanId()).thenReturn("d".repeat(16));
        when(span.context()).thenReturn(context);
        when(span.name("agent.dispatch")).thenReturn(span);
        when(span.start()).thenReturn(span);
        Tracer tracer = mock(Tracer.class);
        when(tracer.nextSpan()).thenReturn(span);
        client.setTracer(tracer);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.start(request(155), "cap"))
                .isInstanceOf(AgentDispatchException.class);

        verify(span).tag("agent.outcome", "http_500");
        verify(span).error(any(IllegalStateException.class));
        verify(span).end();
    }

    private AgentRunStartRequest request(long runId) {
        return new AgentRunStartRequest("v1", runId, runId * 10, 100L, 1L, "PATIENT", 1L,
                "Can I move my appointment?", "en", "req-trace-" + runId);
    }
}
