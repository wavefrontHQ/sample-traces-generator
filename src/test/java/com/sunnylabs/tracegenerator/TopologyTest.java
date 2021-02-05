package com.sunnylabs.tracegenerator;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.constructor.ConstructorException;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.fail;

public class TopologyTest {
    @Test
    public void emptyConfig() {
        Topology subject = new Topology(10, 1, 1, 1);
        subject.load(new ByteArrayInputStream("".getBytes()));
        assertThat(subject.applications(), hasSize(10));
    }

    @Test
    public void createsApplicationFromConfig() {
        Topology subject = loadConfig("applications:\n  testApp: {services: {}}");

        assertThat(subject.applications(), hasSize(1));
        assertThat(subject.applications().get(0).getName(), is("testApp"));
    }

    @Test
    public void createsServicesFromConfig() {
        Topology subject = loadConfig("applications:\n" +
                "  testApp: { services: { testService: {}} }");

        assertThat(subject.applications(), hasSize(1));
        Application app = subject.applications().get(0);
        assertThat(app.getServices(), aMapWithSize(1));
        Service resultingService = app.getServices().get("testService");
        assertThat(resultingService.getApplication(), is("testApp"));
        assertThat(resultingService.getName(), is("testService"));
    }

    @Test
    public void createsOperationsFromConfig() {
        Topology subject = loadConfig("applications:\n" +
                "  testApp:\n" +
                "    services: \n" +
                "      testService:\n" +
                "        operations:\n" +
                "          theOperation: {}\n");

        assertThat(subject.applications(), hasSize(1));
        Application app = subject.applications().get(0);
        assertThat(app.getServices(), aMapWithSize(1));
        Service svc = app.getService("testService");
        assertThat(svc.getOperations(), aMapWithSize(1));
        Operation op = svc.getOperation("theOperation");
        assertThat(op.getApplication(), is("testApp"));
        assertThat(op.getService(), is("testService"));
        assertThat(op.getName(), is("theOperation"));
    }

    @Test
    public void linksOperationsWithinAService() {
        Topology subject = loadConfig("applications:\n" +
                "  testApp:\n" +
                "    services:\n" +
                "      testService:\n" +
                "        operations:\n" +
                "          op1: { calls: [{ name: op2 }] }\n" +
                "          op2: { }\n");

        Service svc = subject.applications().get(0).getService("testService");
        Operation op1 = svc.getOperation("op1");
        Operation op2 = svc.getOperation("op2");
        assertThat(op1.getCalls(), hasSize(1));
        assertThat(op1.getCalls(), contains(op2));
    }

    @Test
    public void linksOperationsAcrossApplications() {
        Topology subject = loadConfig("applications:\n" +
                "  app1:\n" +
                "    services:\n" +
                "      svc1:\n" +
                "        operations:\n" +
                "          op1:\n" +
                "            calls:\n" +
                "            - { application: app2, service: svc2, name: op2 }\n" +
                "  app2:\n" +
                "    services:\n" +
                "      svc2: { operations: { op2: { errorChance: 100 } } }\n");

        Operation op1 = subject.getApplication("app1").getService("svc1").getOperation(
                "op1");
        assertThat(op1.getName(), is("op1"));
        assertThat(op1.getService(), is("svc1"));
        assertThat(op1.getApplication(), is("app1"));
        Operation op2 = subject.getApplication("app2").getService("svc2").getOperation(
                "op2");
        assertThat(op2.getName(), is("op2"));
        assertThat(op2.getService(), is("svc2"));
        assertThat(op2.getApplication(), is("app2"));
        assertThat(op1.getCalls(), contains(op2));
    }

    @Test
    public void errorChance() {
        Topology valid = loadConfig("applications:\n" +
                "  testApp:\n" +
                "    services:\n" +
                "      testService:\n" +
                "        operations: { theOperation: { errorChance: 97 } }");
        Operation op1 = valid.applications().get(0).getService("testService").getOperation(
                "theOperation");
        assertThat(op1.getErrorChance(), is(97F));
    }

    @Test
    public void rejectsNegativeErrorChance() {
        try {
            loadConfig("applications:\n" +
                    "- name: testApp\n" +
                    "  services: \n" +
                    "  - name: testService\n" +
                    "    operations:\n" +
                    "      theOperation:\n" +
                    "        errorChance: -97\n");
        } catch (ConstructorException ignored) {
            return;
        }
        fail();
    }

    @Test
    public void rejectsTooHighErrorChance() {
        try {
            loadConfig("applications:\n" +
                    "- name: testApp\n" +
                    "  services: \n" +
                    "  - name: testService\n" +
                    "    operations:\n" +
                    "      theOperation:\n" +
                    "        errorChance: 197\n");
        } catch (ConstructorException ignored) {
            return;
        }
        fail();
    }

    @Test
    public void addsTagsOnServices() {
        Topology subject = loadConfig("applications:\n" +
                "  app1:\n" +
                "    services:\n" +
                "      svc1:\n" +
                "        tags: { tagName: tagValue }\n");

        Map<String, String> tags = subject.getApplication("app1").getService("svc1").getTags();
        assertThat(tags, is(aMapWithSize(1)));
        assertThat(tags, hasKey("tagName"));
        assertThat(tags.get("tagName"), is("tagValue"));
    }

    @Test
    public void setsEntryPoints() {
        Topology subject = loadConfig("applications:\n" +
                "  testApp:\n" +
                "    services: \n" +
                "      testService: { operations: { theOperation: {} } }\n");

        assertThat(subject.entrypoints(), hasSize(1));
        assertThat(subject.entrypoints().get(0).getName(), is("theOperation"));
    }

    @Test
    public void setsEntryPointsFromConfig() {
        Topology subject = loadConfig("entrypoints: [ app.svc.op1 ]\n" +
                "applications:\n" +
                "  app:\n" +
                "    services: \n" +
                "      svc: { operations: { op1: {}, op2: {} } }\n");

        assertThat(subject.entrypoints(), hasSize(1));
    }

    @Test
    public void loopDetection() {
        try {
            loadConfig("applications: { app: { services: { svc: { " +
                    "operations: {" +
                    "  op1: { calls: [{ name: op2 }] }," +
                    "  op2: { calls: [{ name: op3 }] }," +
                    "  op3: { calls: [{ name: op4 }] }" +
                    "} }}}}");
        } catch (IllegalArgumentException ignored) {
            return;
        }
        fail();
    }

    @Test
    public void ignoresApplicationNameOverride() {
        Topology subject = loadConfig("applications: { app: { name: different }}");
        assertThat(subject.applications(), hasSize(1));
        assertThat(subject.applications().get(0).getName(), is("app"));
    }

    @Test
    public void ignoresServiceNameOverride() {
        Topology subject = loadConfig("applications: { app: { services: { svc: { " +
                "name: different" +
                "}}}}");
        assertThat(subject.getApplication("app").getServices(), hasKey("svc"));
        assertThat(subject.getApplication("app").getServices(), not(hasKey("different")));
    }


    private Topology loadConfig(String config) {
        Topology configuration = new Topology(1, 1, 1, 1);
        configuration.load(new ByteArrayInputStream(config.getBytes()));
        return configuration;
    }
}
