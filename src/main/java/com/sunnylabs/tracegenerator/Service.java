package com.sunnylabs.tracegenerator;

import com.wavefront.sdk.common.Pair;

import java.util.*;

@lombok.Data
public class Service implements TraceGenerator {
    private String name;
    private String application;
    private List<Operation> operations;
    private Map<String, String> tags = new HashMap<>();
    private int baseLatency = 0;

    @Override
    public List<Span> generateTrace(UUID traceId) {
        return generateTrace(traceId, getRandomOperation());
    }

    public List<Span> generateTrace(UUID traceId, String operation) {
        for (Operation op : operations) {
            if (op.getName().equals(operation)) {
                List<Span> spans = op.generateTrace(traceId);
                spans.forEach(s -> {
                    s.duration += baseLatency;
                    tags.forEach((k,v) -> s.tags.add(new Pair<>(k,v)));
                });
                return spans;
            }
        }
        return Collections.emptyList();
    }

    public void setOperations(List<Operation> operations) {
        for (Operation op : operations) {
            op.setService(this.name);
            op.setApplication(this.application);
        }
        this.operations = operations;
    }

    private String getRandomOperation() {
        return operations.get(new Random().nextInt(operations.size())).getName();
    }

    public void setApplication(String application) {
        this.application = application;
        if (operations != null) {
            operations.forEach(op -> op.setApplication(application));
        }
    }

    public static class Builder {
        private List<Operation> operations = Collections.emptyList();
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

        public Builder operations(List<Operation> operations) {
            for (Operation op : operations) {
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
