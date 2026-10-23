package com.example.medical.module.chat.agent;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;

/**
 * Dispatch settings (Round 55.2). Everything is configuration because the right lease,
 * backoff and deadline depend on how slow the model is, and none of those numbers should
 * be compiled in.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.agent")
public class AgentDispatchProperties {

    /** Off by default: a deployment turns on model-assisted drafts deliberately. */
    private boolean enabled = false;

    /** Scheduling can be switched off in tests that drive the dispatcher themselves. */
    private boolean schedulingEnabled = true;

    private String baseUrl = "http://localhost:8090";

    /**
     * The credential Java presents to the agent service (its inbound side). Distinct from
     * {@link #inboundToken} on purpose: the plan asks for directional credentials, and one
     * shared secret would let a leaked value be replayed in the other direction.
     */
    private String outboundToken = "";

    /** The credential the agent service must present when it calls back (Round 55.3). */
    private String inboundToken = "";
    private int batchSize = 10;
    private Duration lease = Duration.ofSeconds(180);
    private Duration deadline = Duration.ofMinutes(30);
    private int maxAttempts = 3;
    private Duration baseBackoff = Duration.ofSeconds(5);
    private Duration maxBackoff = Duration.ofMinutes(5);
    // Covers the service's model timeout plus the result callback; the lease outlives it.
    private Duration requestTimeout = Duration.ofSeconds(60);
    /** How long a worker may present the capability it was given for one attempt. */
    private Duration capabilityTtl = Duration.ofMinutes(5);

    /** Ties a lease to this process; generated once when it is not configured. */
    private String leaseOwner = "";

    private volatile String generatedOwner;

    @PostConstruct
    public void validate() {
        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalStateException("app.agent.request-timeout must be positive");
        }
        if (lease.minus(requestTimeout).isNegative() || lease.minus(requestTimeout).isZero()) {
            throw new IllegalStateException(
                    "app.agent.lease (" + lease + ") must exceed app.agent.request-timeout ("
                            + requestTimeout + "), or a dispatch still in flight is reclaimed");
        }
        if (deadline.minus(lease).isNegative() || deadline.minus(lease).isZero()) {
            throw new IllegalStateException(
                    "app.agent.deadline (" + deadline + ") must exceed app.agent.lease (" + lease + ")");
        }
    }

    public String ownerId() {
        if (leaseOwner != null && !leaseOwner.isBlank()) return leaseOwner;
        if (generatedOwner == null) {
            String host;
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                host = "unknown";
            }
            generatedOwner = host + "-" + ProcessHandle.current().pid() + "-"
                    + UUID.randomUUID().toString().substring(0, 8);
        }
        return generatedOwner;
    }
}
