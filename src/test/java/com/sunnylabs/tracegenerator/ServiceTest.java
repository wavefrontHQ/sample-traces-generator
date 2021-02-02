package com.sunnylabs.tracegenerator;

import com.wavefront.java_sdk.com.google.common.collect.ImmutableMap;
import com.wavefront.sdk.common.Pair;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class ServiceTest {
    @Test
    public void singleOperation() {
        Operation op1 = new Operation("one");
        Service subject = new Service.Builder().
                name("testService").
                operations(ImmutableMap.of(op1.getName(), op1)).
                build();

        UUID traceId = UUID.randomUUID();
        List<Span> result = subject.generateTrace(traceId, "one");
        assertThat(result, hasSize(1));
        assertThat(result.get(0).traceId, is(traceId));
        assertThat(result.get(0).operationName, is("one"));
    }

    @Test
    public void linearCallGraph() {
        Operation op1 = new Operation("one");
        Operation op2 = new Operation("two");
        Operation op3 = new Operation("three");
        op1.addCall(op2);
        op2.addCall(op3);
        Service subject = new Service.Builder().
                name("testService").
                operations(ImmutableMap.of(
                        op1.getName(), op1,
                        op2.getName(), op2,
                        op3.getName(), op3)).
                build();

        List<Span> result = subject.generateTrace(UUID.randomUUID(), "one");

        assertThat(result, hasSize(3));
        assertThat(result.get(0).operationName, is("one"));
        assertThat(result.get(1).operationName, is("two"));
        assertThat(result.get(2).operationName, is("three"));
    }

    @Test
    public void crossServiceCalls() {
        Operation op1 = new Operation("one");
        op1.setService("testService");
        Operation op2 = new Operation("two");
        op2.setService("otherService");
        op1.addCall(op2);
        Service subject = new Service.Builder().
                name("testService").
                operations(ImmutableMap.of(op1.getName(), op1)).
                build();

        List<Span> result = subject.generateTrace(UUID.randomUUID(), "one");

        assertThat(result, hasSize(2));
        assertThat(result.get(0).operationName, is("one"));
        assertThat(serviceName(result.get(0).tags), is("testService"));
        assertThat(result.get(1).operationName, is("two"));
        assertThat(serviceName(result.get(1).tags), is("otherService"));
    }

    private String serviceName(List<Pair<String, String>> tags) {
        return tags.stream().
                filter(tag -> tag._1.equals("service")).
                findFirst().map(tag -> tag._2).
                orElse(null);
    }
}
