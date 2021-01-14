package com.sunnylabs.tracegenerator;

import com.wavefront.java_sdk.com.google.common.base.Strings;
import lombok.Data;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class Configuration {
    private final RawConfig raw;

    public Configuration(InputStream stream) {
        Yaml yaml = new Yaml(new Constructor(RawConfig.class));
        raw = getRawConfig(stream, yaml);
        setDefaults();
    }

    private void setDefaults() {
        if (raw.applications == null) {
            return;
        }
        raw.applications.forEach((appName, app) -> {
            app.setName(appName);
            app.getServices().forEach((name, svc) -> setServiceDefaults(svc, name, appName));
        });


        for (Application app : raw.applications.values()) {
            for (Service svc : app.getServices().values()) {
                List<Operation> ops = svc.getOperations();
                if (ops != null && !ops.isEmpty()) {
                    ops.stream().map(Operation::getCalls).forEach(this::fixOperationReferences);
                }
            }
        }
    }

    private void fixOperationReferences(List<Operation> calls) {
        for (int i = 0; i < calls.size(); i++) {
            Operation c = calls.get(i);
            calls.set(i, existingOperation(c).orElse(c));
        }
    }

    private Optional<Operation> existingOperation(Operation c) {
        Application app = getApplication(c.getApplication());
        if (app == null) {
            return Optional.empty();
        }
        Service svc = app.getService(c.getService());
        if (svc == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(getOperationOrRandom(c, svc));
    }

    private Operation getOperationOrRandom(Operation c, Service svc) {
        List<Operation> operations = svc.getOperations();
        return operations.stream().
                filter(o -> o.getName().equals(c.getName())).findFirst().
                orElse(operations.get(new Random().nextInt(operations.size())));
    }

    private void setServiceDefaults(Service svc, String serviceName, String defaultApplication) {
        if (Strings.isNullOrEmpty(svc.getName())) {
            svc.setName(serviceName);
        }
        // TODO
//        if (!svc.getName().equals(serviceName)) {
//            throw new IllegalArgumentException("Service name overridden in config");
//        }
        if (Strings.isNullOrEmpty(svc.getApplication())) {
            svc.setApplication(defaultApplication);
        }
        if (svc.getOperations() != null) {
            for (Operation op : svc.getOperations()) {
                setOperationDefaults(op, svc.getApplication(), svc.getName());
                for (Operation o : op.getCalls()) {
                    setOperationDefaults(o, svc.getApplication(), svc.getName());

                }
            }
        }

    }

    private void setOperationDefaults(Operation op, String defaultApplication, String defaultService) {
        if (Strings.isNullOrEmpty(op.getApplication())) {
            op.setApplication(defaultApplication);
        }
        if (Strings.isNullOrEmpty(op.getService())) {
            op.setService(defaultService);
        }
    }

    private RawConfig getRawConfig(InputStream stream, Yaml yaml) {
        try {
            if (stream == null || stream.available() == 0) {
                return new RawConfig();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return yaml.load(stream);
    }

    @NonNull
    public List<Application> applications() {
        return new ArrayList<>(raw.applications.values());
    }

    public List<Operation> entrypoints() {
        List<Operation> entries = new ArrayList<>();
        applications().forEach(a -> a.getServices().forEach((k, s) -> s.getOperations().forEach(o -> {
            String slug = a.getName() + "." + s.getName() + "." + o.getName();
            if (raw.entrypoints == null || raw.entrypoints.isEmpty() || raw.entrypoints.contains(slug)) {
                entries.add(o);
            }
        })));
        return entries;
    }

    public Application getApplication(String name) {
        return raw.applications.get(name);
    }

    @Data
    public static class RawConfig {
        public List<String> entrypoints;
        private Map<String, Application> applications;
    }
}
