package com.sunnylabs.tracegenerator;

import com.wavefront.java_sdk.com.google.common.base.Strings;
import com.wavefront.java_sdk.com.google.common.collect.ImmutableMap;
import com.wavefront.java_sdk.com.google.common.collect.ImmutableSet;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.entities.tracing.SpanLog;

import java.util.*;

public class Span {

  public String operationName;
  public long startTime;
  public long duration;
  public String source;
  public UUID traceId;
  public UUID spanId;
  public List<UUID> parents;
  public List<UUID> followsFrom;
  public List<Pair<String, String>> tags;
  public List<SpanLog> spanLogs;

  public Span(String operationName, long startTime, long duration, String source, UUID traceId, UUID spanId, List<UUID> parents, List<UUID> followsFrom, List<Pair<String, String>> tags, List<SpanLog> spanLogs) {
    this.operationName = operationName;
    this.startTime = startTime;
    this.duration = duration;
    this.source = source;
    this.traceId = traceId;
    this.spanId = spanId;
    this.parents = parents;
    this.followsFrom = followsFrom;
    this.tags = tags;
    this.spanLogs = spanLogs;
  }

  public static class Builder {
    public UUID traceId;
    public UUID spanId;
    private String operationName;
    public long startMillis;
    public long durationMillis;
    private String source;
    private String application;
    private String cluster;
    private String service;
    private String shard;
    List<UUID> parents;
    List<UUID> followsFrom;
    List<Pair<String, String>> tags = new ArrayList<>();
    List<SpanLog> spanLogs = new ArrayList<>();
    private double errorChance;

    public Builder() {
      this("operation",
              System.currentTimeMillis(),
              (long) (Math.random() * 500),
              "source");
    }

    public Builder(String operationName, long startMillis, long durationMillis,
                   String source) {
      this.spanId = UUID.randomUUID();
      this.operationName = operationName;
      this.startMillis = startMillis;
      this.durationMillis = durationMillis;
      this.source = source;
    }

    public Builder setParents(List<UUID> parents) {
      this.parents = parents;
      return this;
    }

    public Builder setFollowsFrom(List<UUID> followsFrom) {
      this.followsFrom = followsFrom;
      return this;
    }

    public Builder addTag(String key, String value) {
      this.tags.add(new Pair<>(key, value));
      return this;
    }

    public Builder setSpanLogs(List<SpanLog> spanLogs) {
      this.spanLogs = spanLogs;
      return this;
    }

    public Builder setIdentityTags(String application,
                                   String cluster,
                                   String service,
                                   String shard) {
      this.application = application;
      this.cluster = cluster;
      this.service = service;
      this.shard = shard;
      return this;
    }

    public Span build() {
      spanId = spanId == null ? UUID.randomUUID() : spanId;
      operationName = Strings.isNullOrEmpty(operationName) ? randomValue("operationName") :
              operationName;
      source = Strings.isNullOrEmpty(source) ? randomValue("source") : source;

      addIdentityTags();

      if (errorChance > Math.random() * 100) {
        addError();
      }

      return new Span(operationName, startMillis, durationMillis, source, traceId, spanId,
              parents, followsFrom, tags, spanLogs);
    }

    private void addIdentityTags() {
      if (!hasTag("application", tags)) {
        addTag("application", Strings.isNullOrEmpty(application) ? randomValue("application") : application);
      }
      if (!hasTag("cluster", tags)) {
        addTag("cluster", Strings.isNullOrEmpty(cluster) ? randomValue("cluster") : cluster);
      }
      if (!hasTag("service", tags)) {
        addTag("service", Strings.isNullOrEmpty(service) ? randomValue("service") : service);
      }
      if (!hasTag("shard", tags)) {
        addTag("shard", Strings.isNullOrEmpty(shard) ? randomValue("shard") : shard);
      }
    }

    private void addError() {
      addTag("error", "true");
      // TODO not sending span logs for the time being
      // https://vmware.slack.com/archives/C0DEYJDFZ/p1607559032116900?thread_ts=1607556470.112800&cid=C0DEYJDFZ
//        spanLogs.add(new SpanLog(1000, ImmutableMap.of("errorChance", String.valueOf(errorChance))));
    }

    private boolean hasTag(String tagName, List<Pair<String, String>> tags) {
      for (Pair<String, String> tag : tags) {
        if (tag._1.equals(tagName)) {
          return true;
        }
      }
      return false;
    }

    public Map<String, Set<String>> randomNames = ImmutableMap.of(
            "source", ImmutableSet.of("coffee", "tea", "mate", "cocoa"),
            "application", ImmutableSet.of("coffee", "tea", "mate", "cocoa"),
            "cluster", ImmutableSet.of("us-east", "us-west", "eu-north", "isolated"),
            "service", ImmutableSet.of("Order", "Purchase", "Queue", "Brew", "Delivery"),
            "shard", ImmutableSet.of("shard-1", "shard-2", "shard-3")
    );

    private String randomValue(String defaultValue) {
      final List<String> names = new ArrayList<>(randomNames.getOrDefault(defaultValue, Collections.singleton(defaultValue)));
      return names.get(new Random().nextInt(names.size()));
    }

    public Builder errorChance(double percentage) {
      this.errorChance = percentage;
      return this;
    }
  }
}
