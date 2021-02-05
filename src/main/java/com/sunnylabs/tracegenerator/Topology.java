package com.sunnylabs.tracegenerator;

import com.wavefront.java_sdk.com.google.common.base.Strings;
import lombok.Data;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * The collection of applications and entrypoints to use when simulating traces
 */
public class Topology {
    private final int desiredRandomApps;
    private final int servicesPerApp;
    private final int operationsPerService;
    private final int internalCallsPerApp;
    private RawConfig raw;

    /**
     * @param desiredRandomApps    for random topology, how many apps to create
     * @param servicesPerApp       for random topology, how many services to add per app
     * @param operationsPerService for random topology, how many operations each service should have
     * @param internalCallsPerApp  for random topology, how many calls between each app's services
     */
    public Topology(int desiredRandomApps, int servicesPerApp, int operationsPerService, int internalCallsPerApp) {
        this.desiredRandomApps = desiredRandomApps;
        this.servicesPerApp = servicesPerApp;
        this.operationsPerService = operationsPerService;
        this.internalCallsPerApp = internalCallsPerApp;
    }

    /**
     * @param stream InputStream from which to read YAML topology
     */
    public void load(InputStream stream) {
        Yaml yaml = new Yaml(new Constructor(RawConfig.class));
        raw = getRawConfig(stream, yaml);
        setDefaults();
        checkCallGraph();
    }

    /**
     * Get the applications in the topology
     *
     * @return the applications as a list
     */
    @NonNull
    public List<Application> applications() {
        if (raw.applications == null || raw.applications.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(raw.applications.values());
    }

    /**
     * Get the trace entrypoints
     *
     * @return a list of operations from which to start traces
     */
    @NonNull
    public List<Operation> entrypoints() {
        List<Operation> entries = new ArrayList<>();
        List<Application> applications = applications();
        if (!applications.isEmpty()) {
            applications.forEach(a -> a.getServices().forEach((svcName, s) -> s.getOperations().forEach((opName, o) -> {
                String slug = a.getName() + "." + s.getName() + "." + o.getName();
                if (raw.entrypoints == null || raw.entrypoints.isEmpty() || raw.entrypoints.contains(slug)) {
                    entries.add(o);
                }
            })));
        }
        return entries;
    }

    /**
     * Get an application by name
     *
     * @param name the application name
     * @return the application or null
     */
    public Application getApplication(String name) {
        return raw.applications.get(name);
    }

    private void checkCallGraph() {
        for (Application app : raw.applications.values()) {
            if (app.getServices() == null || app.getServices().isEmpty()) {
                continue;
            }
            for (Service svc : app.getServices().values()) {
                Map<String, Operation> ops = svc.getOperations();
                if (ops == null || ops.isEmpty()) {
                    continue;
                }
                for (Operation op : ops.values()) {
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
                throw new IllegalArgumentException(
                        String.format("Operation %s.%s.%s has circular reference to %s.%s.%s",
                                operation.getApplication(), operation.getService(), operation.getName(),
                                o.getApplication(), o.getService(), o.getName()));
            }
            checkCalls(o, graph);
        }
    }

    private void setDefaults() {
        // TODO split out random topology and configured topology
        if (raw.applications == null || raw.applications.isEmpty()) {
            raw.applications = createRandomApps();
        } else {
            fixReferences(raw.applications);
        }
    }

    private Map<String, Application> createRandomApps() {
        Map<String, Application> apps = new HashMap<>();
        Yaml yaml = new Yaml();
        InputStream inputStream = this.getClass().getClassLoader()
                .getResourceAsStream("wordlists.yaml");
        Map<String, List<String>> words = yaml.load(inputStream);

        List<String> appNames = words.get("applications");
        for (int i = 0; i < desiredRandomApps && appNames.size() > 0; i++) {
            int idx = new Random().nextInt(appNames.size());
            String name = appNames.get(idx);
            appNames.remove(idx);

            apps.put(name, createRandomApp(words.get("services"), words.get("operations")));
        }

        fixReferences(apps);
        createRandomCalls(apps);
        return apps;
    }

    /**
     * Wire up internal references and tree structure left incomplete when loading from YAML
     *
     * @param apps the incomplete topology of apps by name
     */
    private void fixReferences(Map<String, Application> apps) {
        apps.forEach((appName, app) -> {
            app.setName(appName);
            if (app.getServices() == null) {
                return;
            }
            app.getServices().forEach((name, svc) -> setServiceAttributes(svc, name, appName));

            for (Service svc : app.getServices().values()) {
                Map<String, Operation> ops = svc.getOperations();
                if (ops != null && !ops.isEmpty()) {
                    ops.values().stream().map(Operation::getCalls).forEach(this::fixOperationReferences);
                }
            }
        });
    }

    private void createRandomCalls(Map<String, Application> apps) {
        // internal calls per service
        apps.values().forEach(a -> a.getServices().values().forEach(s -> {
            Map<String, Operation> ops = s.getOperations();
            List<Operation> available = new ArrayList<>(ops.values());
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

    private Application createRandomApp(List<String> svcNames, List<String> opNames) {
        List<String> serviceNames = new ArrayList<>(svcNames);
        List<String> operationNames = new ArrayList<>(opNames);
        Application a = new Application();
        Map<String, Service> services = new HashMap<>();
        int desiredServices = Math.min(servicesPerApp, serviceNames.size());
        for (int i = 0; i < desiredServices; i++) {
            int idx = new Random().nextInt(serviceNames.size());
            String name = serviceNames.get(idx);
            serviceNames.remove(idx);
            Service service = createRandomService(operationNames);
            services.put(name, service);
        }
        a.setServices(services);
        return a;
    }

    private Service createRandomService(List<String> operationNames) {
        List<String> names = new ArrayList<>(operationNames);
        Service s = new Service();
        Map<String, Operation> operations = new HashMap<>();
        int desiredOperations = Math.min(operationsPerService, names.size());
        for (int i = 0; i < desiredOperations; i++) {
            int idx = new Random().nextInt(names.size());
            String name = names.get(idx);
            names.remove(idx);
            operations.put(name, new Operation(name));
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
        if (Strings.isNullOrEmpty(c.getName())) {
            // if operation name is left blank, pick a random operation
            return Optional.ofNullable(getRandom(svc.getOperations()));
        }
        return Optional.ofNullable(svc.getOperation(c.getName()));
    }

    private void setServiceAttributes(Service svc, String serviceName, String defaultApplication) {
        svc.setName(serviceName);
        if (Strings.isNullOrEmpty(svc.getApplication())) {
            svc.setApplication(defaultApplication);
        }
        if (svc.getOperations() != null) {
            svc.getOperations().forEach((name, op) -> {
                setOperationAttributes(op, name, svc.getApplication(), svc.getName());
                for (Operation o : op.getCalls()) {
                    setOperationAttributes(o, o.getName(), svc.getApplication(), svc.getName());

                }
            });
        }

    }

    private void setOperationAttributes(Operation op,
                                        String operationName,
                                        String defaultApplication,
                                        String defaultService) {
        op.setName(operationName);
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

    @Data
    private static class RawConfig {
        public List<String> entrypoints = new ArrayList<>();
        public Map<String, Application> applications = new HashMap<>();
    }
}
