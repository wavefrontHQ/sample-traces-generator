package com.sunnylabs.tracegenerator;

import com.wavefront.java_sdk.com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@lombok.Data
public class Operation implements TraceGenerator {
    private String service;
    private String name;
    private String application;
    private String source;
    private List<Operation> calls = new ArrayList<>();
    private Map<String, String> tags = new HashMap<>();
    private float errorChance = Integer.parseInt(System.getProperty("generator.error_percentage", "5"));

    @SuppressWarnings("unused")
    public Operation() {
    }

    public Operation(String name) {
        this.name = name;
    }

    public void addCall(Operation op2) {
        calls.add(op2);
    }

    @SuppressWarnings("unused")
    public void setErrorChance(float errorChance) {
        if (errorChance < 0 || errorChance > 100) {
            throw new IllegalArgumentException("errorChance must be between 0 and 100");
        }
        this.errorChance = errorChance;
    }

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
