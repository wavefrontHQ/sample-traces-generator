package com.sunnylabs.tracegenerator;

import java.util.List;
import java.util.UUID;

public interface TraceGenerator {
    List<Span> generateTrace(UUID traceId);
}
