package com.sunnylabs.tracegenerator;

import com.wavefront.sdk.common.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * An application service for tracing
 */
@lombok.Data
public class Service implements TraceGenerator {
    private String name;
    private String application;
    private Map<String, Operation> operations;
    private Map<String, String> tags = new HashMap<>();
    private int baseLatency = 0;

    /**
     * Generate a trace for a random operation in the service
     *
     * @param traceId UUID to add as the traceId for generated spans
     * @return a List of {@link Span}s in the trace
     */
    @Override
    public List<Span> generateTrace(UUID traceId) {
        return generateTrace(traceId, getRandomOperation());
    }

    /**
     * Generate a trace for a specific operation the service
     *
     * @param traceId UUID to add as the traceId for generated spans
     * @param operation the operation name
     * @return a List of {@link Span}s in the trace
     */
    public List<Span> generateTrace(UUID traceId, String operation) {
        Operation op = operations.get(operation);
        if (op == null) {
            return Collections.emptyList();
        }
        List<Span> spans = op.generateTrace(traceId);
        spans.forEach(s -> {
            s.duration += baseLatency;
            tags.forEach((k, v) -> s.tags.add(new Pair<>(k, v)));
        });
        return spans;
    }

    /**
     * Set the operations available in the service
     *
     * @param operations a map of operations by name
     */
    public void setOperations(Map<String, Operation> operations) {
        for (Operation op : operations.values()) {
            op.setService(this.name);
            op.setApplication(this.application);
        }
        this.operations = operations;
    }

    private String getRandomOperation() {
        List<String> names = new ArrayList<>(this.operations.keySet());
        return names.get(new Random().nextInt(names.size()));
    }

    /**
     * Set the name of the application to which this service and its operations belong
     *
     * @param application the application name
     */
    public void setApplication(String application) {
        this.application = application;
        if (operations != null) {
            operations.values().forEach(op -> op.setApplication(application));
        }
    }

    /**
     * Get an operation by name
     *
     * @param name the operation name
     * @return the named operation or null
     */
    public Operation getOperation(String name) {
        return operations.get(name);
    }

    public static class Builder {
        private Map<String, Operation> operations = Collections.emptyMap();
        private String name;
        private String application;

        public Builder() {
        }

        public Service build() {
            Service service = new Service();
            service.name = name;
            service.operations = operations;
            service.application = application;
            return service;
        }

        public Builder operations(Map<String, Operation> operations) {
            for (Operation op : operations.values()) {
                op.setService(this.name);
                op.setApplication(this.application);
            }
            this.operations = operations;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder application(String application) {
            this.application = application;
            return this;
        }
    }
}
