package com.sunnylabs.tracegenerator;

import com.wavefront.java_sdk.com.google.common.collect.ImmutableList;
import com.wavefront.java_sdk.com.google.common.collect.ImmutableMap;
import com.wavefront.sdk.common.Pair;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class ApplicationTest {
    @Test
    public void singleService() {
        Service svc = new Service.Builder().
                name("one").
                operations(ImmutableMap.of("1", new Operation("1"))).build();
        Application subject = getTestSubject(Collections.singletonList(svc));

        UUID traceId = UUID.randomUUID();
        List<Span> result = subject.generateTrace(traceId, "one");
        assertThat(result, hasSize(1));
        Span span = result.get(0);
        assertThat(span.traceId, is(traceId));
        assertThat(serviceName(span.tags), is("one"));
    }

    private Application getTestSubject(List<Service> serviceList) {
        Map<String, Service> services = new HashMap<>();
        serviceList.forEach(s -> services.put(s.getName(), s));
        Application subject = new Application();
        subject.setName("testApplication");
        subject.setServices(services);
        return subject;
    }

    @Test
    public void linearCallGraph() {
        Operation op1 = new Operation("1");
        Service svc1 = new Service.Builder().name("one").
                operations(ImmutableMap.of("1", op1)).build();
        Operation op2 = new Operation("2");
        Service svc2 = new Service.Builder().name("two").
                operations(ImmutableMap.of("2", op2)).build();
        Operation op3 = new Operation("3");
        Service svc3 = new Service.Builder().name("three").
                operations(ImmutableMap.of("3", op3)).build();

        op1.addCall(op2);
        op2.addCall(op3);

        Application subject = getTestSubject(ImmutableList.of(svc1, svc2, svc3));

        List<Span> result = subject.generateTrace(UUID.randomUUID(), "one");
        assertThat(result, hasSize(3));
        assertThat(serviceName(result.get(0).tags), is("one"));
        assertThat(serviceName(result.get(1).tags), is("two"));
        assertThat(serviceName(result.get(2).tags), is("three"));
    }

    private String serviceName(List<Pair<String, String>> tags) {
        return getTagValue(tags, "service");
    }

    private String getTagValue(List<Pair<String, String>> tags, String search) {
        return tags.stream().
                filter(tag -> tag._1.equals(search)).
                findFirst().map(tag -> tag._2).
                orElse(null);
    }

}
