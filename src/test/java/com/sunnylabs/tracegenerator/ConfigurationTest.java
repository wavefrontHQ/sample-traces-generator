package com.sunnylabs.tracegenerator;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.constructor.ConstructorException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

public class ConfigurationTest {
    @Test
    public void emptyConfig() {
        Configuration subject = new Configuration(new InputStream() {
            @Override
            public int read() throws IOException {
                return 0;
            }
        });
        // TODO defaults
        assertThat(subject.applications(), hasSize(10));
    }

    @Test
    public void createsApplicationFromConfig() {
        Configuration subject = generateConfiguration("applications:\n  testApp: {services: {}}");

        assertThat(subject.applications(), hasSize(1));
        assertThat(subject.applications().get(0).getName(), is("testApp"));
    }

    @Test
    public void createsServicesFromConfig() {
        Configuration subject = generateConfiguration("applications:\n" +
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
        Configuration subject = generateConfiguration("applications:\n" +
                "  testApp:\n" +
                "    services: \n" +
                "      testService:\n" +
                "        operations:\n" +
                "        - name: theOperation\n");

        assertThat(subject.applications(), hasSize(1));
        Application app = subject.applications().get(0);
        assertThat(app.getServices(), aMapWithSize(1));
        Service svc = app.getService("testService");
        assertThat(svc.getOperations(), hasSize(1));
        Operation op = svc.getOperations().get(0);
        assertThat(op.getApplication(), is("testApp"));
        assertThat(op.getService(), is("testService"));
        assertThat(op.getName(), is("theOperation"));
    }

    @Test
    public void linksOperationsWithinAService() {
        Configuration subject = generateConfiguration("applications:\n" +
                "  testApp:\n" +
                "    services:\n" +
                "      testService:\n" +
                "        operations:\n" +
                "        - { name: op1, calls: [{ name: op2 }] }\n" +
                "        - { name: op2 }\n");

        Service svc = subject.applications().get(0).getService("testService");

        Operation op1 = svc.getOperations().get(0);
        Operation op2 = svc.getOperations().get(1);
        assertThat(op1.getName(), is("op1"));
        assertThat(op1.getCalls(), hasSize(1));

        assertThat(op1.getCalls(), contains(op2));
    }

    @Test
    public void linksOperationsAcrossApplications() {
        Configuration subject = generateConfiguration("applications:\n" +
                "  app1:\n" +
                "    services:\n" +
                "      svc1:\n" +
                "        operations:\n" +
                "        - name: op1\n" +
                "          calls:\n" +
                "          - { application: app2, service: svc2, name: op2 }\n" +
                "  app2:\n" +
                "    services:\n" +
                "      svc2: { operations: [{ name: op2, errorChance: 100 }] }\n");

        Operation op1 = subject.getApplication("app1").getService("svc1").getOperations().get(0);
        assertThat(op1.getName(), is("op1"));
        assertThat(op1.getService(), is("svc1"));
        assertThat(op1.getApplication(), is("app1"));
        Operation op2 = subject.getApplication("app2").getService("svc2").getOperations().get(0);
        assertThat(op2.getName(), is("op2"));
        assertThat(op2.getService(), is("svc2"));
        assertThat(op2.getApplication(), is("app2"));

        assertThat(op1.getCalls(), contains(op2));
    }

    @Test
    public void errorChance() {
        Configuration valid = generateConfiguration("applications:\n" +
                "  testApp:\n" +
                "    services:\n" +
                "      testService:\n" +
                "        operations: [{ name: theOperation, errorChance: 97 }]");
        Operation op1 = valid.applications().get(0).getService("testService").getOperations().get(0);
        assertThat(op1.getErrorChance(), is(97F));
    }

    @Test
    public void rejectsNegativeErrorChance() {
        try {
            generateConfiguration("applications:\n" +
                    "- name: testApp\n" +
                    "  services: \n" +
                    "  - name: testService\n" +
                    "    operations:\n" +
                    "    - name: theOperation\n" +
                    "      errorChance: -97\n");
        } catch (ConstructorException ignored) {
            return;
        }
        fail();
    }

    @Test
    public void rejectsTooHighErrorChance() {
        try {
            generateConfiguration("applications:\n" +
                    "- name: testApp\n" +
                    "  services: \n" +
                    "  - name: testService\n" +
                    "    operations:\n" +
                    "    - name: theOperation\n" +
                    "      errorChance: 197\n");
        } catch (ConstructorException ignored) {
            return;
        }
        fail();
    }

    @Test
    public void addsTagsOnServices() {
        Configuration subject = generateConfiguration("applications:\n" +
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
        Configuration subject = generateConfiguration("applications:\n" +
                "  testApp:\n" +
                "    services: \n" +
                "      testService: { operations: [{ name: theOperation }] }\n");

        assertThat(subject.entrypoints(), hasSize(1));
        assertThat(subject.entrypoints().get(0).getName(), is("theOperation"));
    }

    @Test
    public void setsEntryPointsFromConfig() {
        Configuration subject = generateConfiguration("entrypoints: [ app.svc.op1 ]\n" +
                "applications:\n" +
                "  app:\n" +
                "    services: \n" +
                "      svc: { operations: [{ name: op1 }, { name: op2 }] }\n");

        assertThat(subject.entrypoints(), hasSize(1));
    }

    @Test
    public void loopDetection() {
        try {
            generateConfiguration("applications: { app: { services: { svc: { " +
                    "operations: [" +
                    "{ name: op1, calls: [{ name: op2}]}," +
                    "{ name: op2, calls: [{ name: op3}]}," +
                    "{ name: op3, calls: [{ name: op1}]}" +
                    "]}}}}");
        } catch (IllegalArgumentException ignored) {
            return;
        }
        fail();
    }

    @Test
    public void ignoresApplicationNameOverride() {
        Configuration subject = generateConfiguration("applications: { app: { name: different }}");
        assertThat(subject.applications(), hasSize(1));
        assertThat(subject.applications().get(0).getName(), is("app"));
    }

    @Test
    public void ignoresServiceNameOverride() {
        Configuration subject = generateConfiguration("applications: { app: { services: { svc: { " +
                "name: different" +
                "}}}}");
        assertThat(subject.getApplication("app").getServices(), hasKey("svc"));
        assertThat(subject.getApplication("app").getServices(), not(hasKey("different")));
    }


    private Configuration generateConfiguration(String config) {
        return new Configuration(new ByteArrayInputStream(config.getBytes()));
    }
}
