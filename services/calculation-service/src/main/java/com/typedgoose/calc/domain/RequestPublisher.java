package com.typedgoose.calc.domain;

/**
 * Seam wired in Step 08 by a Kafka-backed implementation.
 * Step 07 ships a no-op default so the service can persist requests without a broker.
 */
public interface RequestPublisher {

    void publish(SummarizationFile file);
}
