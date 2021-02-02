package com.sunnylabs.tracegenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@lombok.Data
public class Application implements TraceGenerator {
    private String name;
    private Map<String, Service> services;

    @Override
    public List<Span> generateTrace(UUID traceId) {
        return generateTrace(traceId, getRandomService());
    }

    public List<Span> generateTrace(UUID traceId, String service) {
        for (Service svc : services.values()) {
            if (svc.getName().equals(service)) {
                return svc.generateTrace(traceId);
            }
        }
        return Collections.emptyList();
    }

    private String getRandomService() {
        List<String> keys = new ArrayList<>(services.keySet());
        return keys.get(new Random().nextInt(keys.size()));
    }

    public Service getService(String name) {
        return services.get(name);
    }
}
