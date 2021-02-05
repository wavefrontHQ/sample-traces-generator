package com.sunnylabs.tracegenerator;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.entities.tracing.SpanLog;
import com.wavefront.sdk.entities.tracing.WavefrontTracingSpanSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StubWavefrontClient implements WavefrontTracingSpanSender {
  public List<Span> spans = new ArrayList<>();
  public boolean throwOnSend = false;

  @Override
  public void sendSpan(String name, long startMillis, long durationMillis, String source, UUID traceId, UUID spanId, List<UUID> parents, List<UUID> followsFrom, List<Pair<String, String>> tags, List<SpanLog> spanLogs) throws IOException {
    if (throwOnSend) {
      throw new IOException("expected");
    }
    Span.Builder builder = new Span.Builder(name, startMillis, durationMillis, source).
        setIdentityTags("application", "cluster", "service", "shard").
        setParents(parents).
        setFollowsFrom(followsFrom).
        setSpanLogs(spanLogs);
    if (tags != null) {
      tags.forEach(tag -> builder.addTag(tag._1, tag._2));
    }
    Span span = builder.build();
    span.traceId = traceId;
    spans.add(span);
  }

  public void reset() {
    this.throwOnSend = false;
    spans.clear();
  }
}