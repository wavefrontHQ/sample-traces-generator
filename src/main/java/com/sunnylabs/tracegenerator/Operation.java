package com.sunnylabs.tracegenerator;

import com.wavefront.java_sdk.com.google.common.collect.ImmutableList;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@lombok.Data
/**
 * An individual operation for tracing
 **/
public class Operation implements TraceGenerator {
    private String service;
    private String name;
    private String application;
    private String source;
    private List<Operation> calls = new ArrayList<>();
    private Map<String, String> tags = new HashMap<>();
    @Value("${generator.error_percentage:5}")
    private float errorChance;

    /**
     * default constructor used by YAML creator
     */
    @SuppressWarnings("unused")
    public Operation() {
    }

    /**
     * @param name the operation name
     */
    public Operation(String name) {
        this.name = name;
    }

    /**
     * Add a dependent operation which will be called by this operation in traces
     *
     * @param op2 the next operation to call
     */
    public void addCall(Operation op2) {
        calls.add(op2);
    }

    /**
     * Set the percentage chance of generating an error when tracing this operation
     *
     * @param errorChance the percentage likelihood of generating an error
     * @throws IllegalArgumentException if errorChance is not a valid percentage
     */
    @SuppressWarnings("unused")
    public void setErrorChance(float errorChance) {
        if (errorChance < 0 || errorChance > 100) {
            throw new IllegalArgumentException("errorChance must be between 0 and 100");
        }
        this.errorChance = errorChance;
    }

    /**
     * Generate a trace for the operation and its dependent operations
     *
     * @param traceId UUID to add as the traceId for generated spans
     * @return a List of {@link Span}s in the trace
     */
    @Override
    public List<Span> generateTrace(UUID traceId) {
        return generateTrace(traceId, Optional.empty(), 0, getRandomDuration(1200));
    }

    private int getRandomDuration(int max) {
        if (max < 10) {
            return max;
        }
        return new Random().nextInt(max / 2) + max / 2;
    }

    /**
     * Generate a child trace for the operation and its dependent operations
     *
     * @param traceId UUID to add as the traceId for generated spans
     * @param parentId UUID to add as the parentId for generated spans
     * @param offsetMillis ms to add to start time for dependent operations
     * @param durationMillis total milliseconds for the trace
     * @return a List of {@link Span}s in the trace
     */
    public List<Span> generateTrace(UUID traceId, Optional<UUID> parentId,
                                    int offsetMillis, int durationMillis) {
        source = "trace-generator";

        List<Span> trace = new ArrayList<>();
        int duration = getRandomDuration(durationMillis);
        int offset = getRandomDuration(durationMillis - duration) + offsetMillis;
        Span span = getSpan(traceId, parentId, offset, duration);
        trace.add(span);
        if (calls != null) {
            calls.forEach(c -> trace.addAll(c.generateTrace(traceId,
                    Optional.ofNullable(span.spanId), offset, duration)));
        }
        return trace;
    }

    private Span getSpan(UUID traceId, Optional<UUID> parentId, int offset, int durationMillis) {
        long startMillis = System.currentTimeMillis() + offset;
        Span.Builder builder = new Span.Builder(name, startMillis, durationMillis, source);
        parentId.ifPresent(uuid -> builder.setParents(ImmutableList.of(uuid)));
        builder.errorChance(errorChance);
        builder.setIdentityTags(application, "cluster", service, "shard");
        tags.forEach(builder::addTag);
        Span span = builder.build();
        span.traceId = traceId;
        return span;
    }
}
