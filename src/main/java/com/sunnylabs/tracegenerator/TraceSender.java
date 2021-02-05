package com.sunnylabs.tracegenerator;

import com.wavefront.sdk.entities.tracing.WavefrontTracingSpanSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TraceSender {
  public WavefrontTracingSpanSender sender;
  public List<Span> spans = new ArrayList<>();
  public UUID traceId = UUID.randomUUID();

  public TraceSender(WavefrontTracingSpanSender spanSender) {
    sender = spanSender;
  }

  public void addSpan(Span span) {
    spans.add(span);
  }


  public void flush() throws IOException {
    List<Span> iterable = new ArrayList<>(spans);
    for (Span span : iterable) {
      sender.sendSpan(span.operationName, span.startTime, span.duration, span.source, traceId,
          span.spanId, span.parents, span.followsFrom, span.tags, span.spanLogs);
      spans.remove(span);
    }

    traceId = UUID.randomUUID();
  }

  public void send(List<Span> spans) throws IOException {
    spans.forEach(this::addSpan);
    flush();
  }

  public void send(TraceGenerator tracer) throws IOException {
    tracer.generateTrace(traceId).forEach(this::addSpan);
    flush();
  }
}
