package com.sunnylabs.tracegenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * An application for tracing
 */
@lombok.Data
public class Application implements TraceGenerator {
    private String name;
    private Map<String, Service> services;

    /**
     * Generate a trace for a random service in the application
     *
     * @param traceId UUID to add as the traceId for generated spans
     * @return a List of {@link Span}s in the trace
     */
    @Override
    public List<Span> generateTrace(UUID traceId) {
        return generateTrace(traceId, getRandomService());
    }

    /**
     * Generate a trace for a specific service in the application
     *
     * @param traceId UUID to add as the traceId for generated spans
     * @param service name of the service
     * @return a List of {@link Span}s in the trace
     */
    public List<Span> generateTrace(UUID traceId, String service) {
        if (services.containsKey(service)) {
            return services.get(service).generateTrace(traceId);
        }
        return Collections.emptyList();
    }

    private String getRandomService() {
        List<String> keys = new ArrayList<>(services.keySet());
        return keys.get(new Random().nextInt(keys.size()));
    }


    /**
     * Get a service by name
     *
     * @param name the service name
     * @return the service or null
     */
    public Service getService(String name) {
        return services.get(name);
    }
}
