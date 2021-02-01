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
    private final int servicesPerApp = 50;
    private final int internalCallsPerApp = 3;

    public Configuration(InputStream stream) {
        Yaml yaml = new Yaml(new Constructor(RawConfig.class));
        raw = getRawConfig(stream, yaml);
        setDefaults();
        checkCallGraph();
    }

    private void checkCallGraph() {
        for (Application app : raw.applications.values()) {
            if (app.getServices() == null || app.getServices().isEmpty()) {
                continue;
            }
            for (Service svc : app.getServices().values()) {
                List<Operation> ops = svc.getOperations();
                if (ops == null || ops.isEmpty()) {
                    continue;
                }
                for (Operation op : ops) {
                    checkCalls(op, Collections.emptyList());
                }
            }
        }
    }

    private void checkCalls(Operation operation, List<Operation> callGraph) {
        List<Operation> graph = new ArrayList<>(callGraph);
        graph.add(operation);
        for (Operation o : operation.getCalls()) {
            if (graph.contains(o)) {
                throw new IllegalArgumentException(String.format("Operation %s.%s.%s has circular" +
                                " reference to %s.%s.%s",
                        operation.getApplication(), operation.getService(), operation.getName(),
                        o.getApplication(), o.getService(), o.getName()));
            }
            checkCalls(o, graph);
        }
    }


    private void setDefaults() {
        if (raw.applications == null || raw.applications.isEmpty()) {
            raw.applications = createRandomApps();
        } else {
            fixReferences(raw.applications);
        }
    }

    private Map<String, Application> createRandomApps() {
        Map<String, Application> apps = new HashMap<>();
        Yaml yaml = new Yaml();
        InputStream inputStream = this.getClass()
                .getClassLoader()
                .getResourceAsStream("wordlists.yaml");
        Map<String, List<String>> words = yaml.load(inputStream);

        List<String> appNames = words.get("applications");
        for (int i = 0; i < 10; i++) {
            int idx = new Random().nextInt(appNames.size());
            String name = appNames.get(idx);
            appNames.remove(idx);

            List<String> serviceNames = new ArrayList<>(words.get("services"));
            List<String> operationNames = new ArrayList<>(words.get("operations"));
            Application a = createRandomApp(serviceNames, operationNames);
            apps.put(name, a);
        }

        fixReferences(apps);
        createRandomCalls(apps);
        return apps;
    }

    private void fixReferences(Map<String, Application> apps) {
        apps.forEach((appName, app) -> {
            app.setName(appName);
            if (app.getServices() == null) {
                return;
            }
            app.getServices().forEach((name, svc) -> setServiceDefaults(svc, name, appName));

            for (Service svc : app.getServices().values()) {
                List<Operation> ops = svc.getOperations();
                if (ops != null && !ops.isEmpty()) {
                    ops.stream().map(Operation::getCalls).forEach(this::fixOperationReferences);
                }
            }
        });
    }

    private void createRandomCalls(Map<String, Application> apps) {
        // internal calls per service
        apps.values().forEach(a -> a.getServices().values().forEach(s -> {
            List<Operation> ops = s.getOperations();
            List<Operation> available = new ArrayList<>(ops);
            for (int i = 0; i < internalCallsPerApp && available.size() > 1; i++) {
                Operation o = getRandom(ops);
                available.remove(o);
                o.addCall(getRandom(available));
            }
        }));

        // cross service calls per app
        apps.values().forEach(a -> {
            Collection<Service> services = a.getServices().values();
            List<Service> available = new ArrayList<>(services);
            while (available.size() > 1) {
                Service s = getRandom(services);
                Operation o = getRandom(s.getOperations());

                available.remove(s);
                Operation op2 = getRandom(getRandom(available).getOperations());

                o.addCall(op2);
            }
        });

        // cross app calls
        for (int i = 0; i < apps.size(); i++) {
            Operation random = getRandom(getRandom(getRandom(apps).getServices()).getOperations());
            Operation target = getRandom(getRandom(getRandom(apps).getServices()).getOperations());
            while (target.getApplication().equals(random.getApplication())) {
                target = getRandom(getRandom(getRandom(apps).getServices()).getOperations());
            }
            random.addCall(target);
        }
    }

    private <T> T getRandom(Map<?, T> from) {
        return new ArrayList<>(from.values()).get(new Random().nextInt(from.size()));
    }

    private <T> T getRandom(Collection<T> from) {
        return new ArrayList<>(from).get(new Random().nextInt(from.size()));
    }


    private Application createRandomApp(List<String> serviceNames, List<String> operationNames) {
        Application a = new Application();
        Map<String, Service> services = new HashMap<>();
        int desiredServices = Math.min(servicesPerApp, serviceNames.size());
        for (int i = 0; i < desiredServices; i++) {
            int idx = new Random().nextInt(serviceNames.size());
            String name = serviceNames.get(idx);
            serviceNames.remove(idx);
            Service service = createRandomService(new ArrayList<>(operationNames));
            services.put(name, service);
        }
        a.setServices(services);
        return a;
    }

    private Service createRandomService(List<String> operationNames) {
        Service s = new Service();
        List<Operation> operations = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int idx = new Random().nextInt(operationNames.size());
            String name = operationNames.get(idx);
            operationNames.remove(idx);
            operations.add(new Operation(name));
        }
        s.setOperations(operations);
        return s;
    }

    private void fixOperationReferences(List<Operation> calls) {
        for (int i = 0; i < calls.size(); i++) {
            final int index = i;
            existingOperation(calls.get(index)).ifPresent(e -> calls.set(index, e));
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
        return Optional.of(getOperationOrRandom(c, svc));
    }

    private Operation getOperationOrRandom(Operation c, Service svc) {
        List<Operation> operations = svc.getOperations();
        Optional<Operation> first = operations.stream().
                filter(o -> o.getName().equals(c.getName())).findFirst();
        Operation random = operations.get(new Random().nextInt(operations.size()));
        return first.orElse(random);
    }

    private void setServiceDefaults(Service svc, String serviceName, String defaultApplication) {
        svc.setName(serviceName);
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
        RawConfig rawConfig = yaml.load(stream);
        if (rawConfig == null) {
            return new RawConfig();
        }
        return rawConfig;

    }

    @NonNull
    public List<Application> applications() {
        if (raw.applications == null || raw.applications.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(raw.applications.values());
    }

    public List<Operation> entrypoints() {
        List<Operation> entries = new ArrayList<>();
        List<Application> applications = applications();
        if (!applications.isEmpty()) {
            applications.forEach(a -> a.getServices().forEach((k, s) -> s.getOperations().forEach(o -> {
                String slug = a.getName() + "." + s.getName() + "." + o.getName();
                if (raw.entrypoints == null || raw.entrypoints.isEmpty() || raw.entrypoints.contains(slug)) {
                    entries.add(o);
                }
            })));
        }
        return entries;
    }

    public Application getApplication(String name) {
        return raw.applications.get(name);
    }

    @Data
    public static class RawConfig {
        public List<String> entrypoints = new ArrayList<>();
        private Map<String, Application> applications = new HashMap<>();
    }
}
