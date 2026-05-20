package com.typedgoose.gateway.observability;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Mints (or reads) the request-scope correlation id at the edge and propagates
 * it downstream as a `correlationId` header. Boot 4 with
 * `management.tracing.baggage.remote-fields: correlationId` accepts the
 * per-field header on the inbound side and Boot's baggage→MDC bridge surfaces
 * `correlationId` in every log line of the request.
 *
 * <p>Note: do <em>not</em> open a baggage scope around {@code chain.filter(...)}
 * with try-with-resources — {@code chain.filter} returns the Mono synchronously
 * and the scope closes before the chain actually subscribes (classic reactive
 * trap). Propagating via header is the reliable path here.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";
    private static final String BAGGAGE_FIELD = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString()
                : incoming;

        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r
                        .header(HEADER, correlationId)
                        .header(BAGGAGE_FIELD, correlationId))
                .build();
        mutated.getResponse().getHeaders().add(HEADER, correlationId);
        return chain.filter(mutated);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
