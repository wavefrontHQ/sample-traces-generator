package com.sunnylabs.tracegenerator;

import com.wavefront.java_sdk.com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@lombok.Data
public class Operation implements TraceGenerator {
    private String service;
    private String name;
    private String application;
    private String source;
    private List<Operation> calls = new ArrayList<>();
    private long startMillis;
    private int durationMillis;
    private float errorChance;

    @SuppressWarnings("unused")
    public Operation() {
    }

    public Operation(String name) {
        this.name = name;
    }

    public Operation(String name, String service) {
        this.name = name;
        this.service = service;
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
        return generateTrace(traceId, Optional.empty());
    }

    public List<Span> generateTrace(UUID traceId, Optional<UUID> parentId) {
        // TODO do we ever want to specify spanId?
        startMillis = System.currentTimeMillis();
        durationMillis = 22222;
        source = "source";

        List<Span> trace = new ArrayList<>();
        Span span = getSpan(traceId, parentId);
        trace.add(span);
        if (calls != null) {
            calls.forEach(c -> trace.addAll(c.generateTrace(traceId, Optional.ofNullable(span.spanId))));
        }
        return trace;
    }

    private Span getSpan(UUID traceId, Optional<UUID> parentId) {
        Span.Builder builder = new Span.Builder(name, startMillis, durationMillis, source);
        parentId.ifPresent(uuid -> builder.setParents(ImmutableList.of(uuid)));
        builder.errorChance(errorChance);
        builder.setIdentityTags(application, "cluster", service, "shard");
        Span span = builder.build();
        span.traceId = traceId;
        return span;
    }
}
