package com.sunnylabs.tracegenerator;

import com.wavefront.sdk.proxy.WavefrontProxyClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

@SpringBootApplication
public class TraceGeneratorApplication {
    public Logger log = Logger.getLogger(TraceSender.class.toString());

    @SuppressWarnings("deprecation")
    private WavefrontProxyClient client;
    private TraceSender traceSender;
    @Value("${generator.send_frequency_ms:30000}")
    private int sendFrequency;
    @Value("${topology.app_count:10}")
    private int desiredRandomApps;
    @Value("${topology.services_per_app:50}")
    private int servicesPerApp;
    @Value("${topology.operations_per_service:10}")
    private int operationsPerService;
    @Value("${topology.internal_call_count:3}")
    private int internalCallsPerApp;

    private Topology topology;


    public static void main(String[] args) {
        SpringApplication.run(TraceGeneratorApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {
            topology = new Topology(desiredRandomApps, servicesPerApp,
                    operationsPerService, internalCallsPerApp);
            InputStream inputStream = this.getClass().getClassLoader()
                    .getResourceAsStream("config.yaml");
            topology.load(inputStream);

            // TODO extend WavefrontClient instead of using WavefrontProxyClient
            // TODO get ports and hostnames from app properties
            client = new WavefrontProxyClient.Builder("localhost").
                    distributionPort(2878).
                    metricsPort(2878).
                    tracingPort(30001).build();
            traceSender = new TraceSender(client);

            Timer t = new Timer();
            t.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        Operation op = randomEntrypoint();
                        List<Span> trace = op.generateTrace(traceSender.traceId);
                        log.info(String.format("Sending %d spans for %s.%s.%s", trace.size(),
                                op.getApplication(), op.getService(), op.getName()));
                        traceSender.send(trace);
                    } catch (IOException ignored) {
                    }
                }
            }, 0, sendFrequency);
        };
    }

    private Operation randomEntrypoint() {
        return topology.entrypoints().get(new Random().nextInt(topology.entrypoints().size()));
    }
}
