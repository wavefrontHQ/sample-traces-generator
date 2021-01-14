package com.sunnylabs.tracegenerator;

import com.google.gson.GsonBuilder;
import com.wavefront.sdk.entities.tracing.WavefrontTracingSpanSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class TraceSender {
  public WavefrontTracingSpanSender sender;
  public List<Span> spans = new ArrayList<>();
  public Logger log = Logger.getLogger(TraceSender.class.toString());
  public UUID traceId = UUID.randomUUID();

  public TraceSender(WavefrontTracingSpanSender spanSender) {
    sender = spanSender;
  }

  public void addSpan(Span span) {
    spans.add(span);
  }


  public void flush() throws IOException {
    log.info(String.format("Trace %s - %d spans", traceId, spans.size()));
    for (Span span : spans) {
      log.info(String.format("Span %s - parent %s", span.operationName, span.parents));
      log.info(new GsonBuilder().setPrettyPrinting().create().toJson(span));
      sender.sendSpan(span.operationName, span.startTime, span.duration, span.source, traceId,
              span.spanId, span.parents, span.followsFrom, span.tags, span.spanLogs);
    }

    spans.clear();
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
