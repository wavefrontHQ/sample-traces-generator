package com.sunnylabs.tracegenerator;

import com.wavefront.java_sdk.com.google.common.collect.ImmutableList;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.entities.tracing.SpanLog;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TraceSenderTest {
    StubWavefrontClient mockSender = new StubWavefrontClient();
    TraceSender subject = new TraceSender(mockSender);

    @Test
    public void sendsSpansOnFlush() throws IOException {
        subject.addSpan(new Span.Builder().build());
        subject.addSpan(new Span.Builder().build());
        subject.addSpan(new Span.Builder().build());

        UUID expectedTraceId = subject.traceId;
        subject.flush();

        assertThat(mockSender.spans, hasSize(3));
        assertEquals(expectedTraceId, mockSender.spans.get(0).traceId);
        assertEquals(expectedTraceId, mockSender.spans.get(1).traceId);
        assertEquals(expectedTraceId, mockSender.spans.get(2).traceId);
    }

    @Test
    public void sendsSpans() throws IOException {
        UUID expectedTraceId = subject.traceId;
        subject.send(ImmutableList.of(
                new Span.Builder().build(),
                new Span.Builder().build(),
                new Span.Builder().build()));

        assertThat(mockSender.spans, hasSize(3));
        assertEquals(expectedTraceId, mockSender.spans.get(0).traceId);
        assertEquals(expectedTraceId, mockSender.spans.get(1).traceId);
        assertEquals(expectedTraceId, mockSender.spans.get(2).traceId);
    }

    @Test
    public void sendsApplications() throws IOException {
        UUID expectedTraceId = subject.traceId;
        Application app = new Application();
        app.setName("testApp");
        Service svc = new Service();
        svc.setName("testService");
        Operation op1 = new Operation("one");
        svc.setOperations(Collections.singletonList(op1));
        app.setServices(Collections.singletonMap(svc.getName(), svc));

        subject.send(app);
        assertThat(mockSender.spans, is(not(empty())));
        mockSender.spans.forEach(span -> assertEquals(expectedTraceId, span.traceId));
    }

    @Test
    public void sendsServices() throws IOException {
        UUID expectedTraceId = subject.traceId;
        Service svc = new Service();
        svc.setName("testService");
        Operation op1 = new Operation("one");
        svc.setOperations(Collections.singletonList(op1));

        subject.send(svc);
        assertThat(mockSender.spans, is(not(empty())));
        mockSender.spans.forEach(span -> assertEquals(expectedTraceId, span.traceId));
    }

    @Test
    public void sendsOperations() throws IOException {
        UUID expectedTraceId = subject.traceId;
        Operation op1 = new Operation("one");
        Operation op2 = new Operation("two");
        Operation op3 = new Operation("three");
        op1.addCall(op2);
        op2.addCall(op3);

        subject.send(op1);
        assertThat(mockSender.spans, hasSize(3));
        mockSender.spans.forEach(span -> assertEquals(expectedTraceId, span.traceId));
    }

    @Test
    public void spanBuilder() throws IOException {
        UUID followsFrom = UUID.randomUUID();
        UUID parent = UUID.randomUUID();

        SpanLog spanLog = new SpanLog(1234, emptyMap());

        Span expected = new Span.Builder("operationName", 1000, 253, "source").
                setIdentityTags("application", "cluster", "service", "shard").
                setParents(ImmutableList.of(parent)).
                setFollowsFrom(ImmutableList.of(followsFrom)).
                setSpanLogs(ImmutableList.of(spanLog)).build();

        subject.addSpan(expected);
        UUID expectedTraceId = subject.traceId;
        subject.flush();

        assertThat(mockSender.spans, hasSize(1));
        Span actual = mockSender.spans.get(0);
        assertEquals(expected.operationName, actual.operationName);

        assertEquals(expectedTraceId, actual.traceId);
        assertEquals(expected.startTime, actual.startTime);
        assertEquals(expected.duration, actual.duration);
        assertEquals(expected.source, actual.source);
        assertEquals(ImmutableList.of(parent), actual.parents);
        assertEquals(ImmutableList.of(followsFrom), actual.followsFrom);
        assertEquals(ImmutableList.of(spanLog), actual.spanLogs);
    }

    @Test
    public void requiredFields() throws IOException {
        subject.addSpan(new Span.Builder().build());
        UUID expectedTraceId = subject.traceId;

        subject.flush();

        assertThat(mockSender.spans, hasSize(1));
        Span actual = mockSender.spans.get(0);

        assertEquals(expectedTraceId, actual.traceId);
        assertThat(actual.spanId, notNullValue());
        assertTrue(Strings.isNotBlank(actual.operationName));
        assertTrue(Strings.isNotBlank(actual.source));

        assertEquals(1, getTagCount("application", actual.tags));
        assertEquals(1, getTagCount("cluster", actual.tags));
        assertEquals(1, getTagCount("service", actual.tags));
        assertEquals(1, getTagCount("shard", actual.tags));
    }

    @Test
    public void errors() throws IOException {
        subject.addSpan(new Span.Builder().errorChance(100).build());
        subject.flush();

        assertThat(mockSender.spans, hasSize(1));
        Span actual = mockSender.spans.get(0);
        assertEquals(1, getTagCount("error", actual.tags));
        assertEquals("true", getTagValue("error", actual.tags));
        // Uncomment the following line to validate sending of span logs (see {Span.addError})
        // assertThat(actual.spanLogs, hasSize(greaterThan(0)));
    }

    @Test
    public void clearsAfterSending() throws IOException {
        subject.addSpan(new Span.Builder().build());
        subject.flush();
        assertThat(mockSender.spans, hasSize(1));

        mockSender.clear();
        subject.flush();
        assertThat(mockSender.spans, hasSize(0));
    }

    private String getTagValue(String key, List<Pair<String, String>> tags) {
        for (Pair<String, String> tag : tags) {
            if (tag._1.equals(key)) {
                return tag._2;
            }
        }
        return null;
    }

    private int getTagCount(String key, List<Pair<String, String>> tags) {
        int count = 0;
        for (Pair<String, String> tag : tags) {
            if (tag._1.equals(key)) {
                count++;
            }
        }
        return count;
    }

}
