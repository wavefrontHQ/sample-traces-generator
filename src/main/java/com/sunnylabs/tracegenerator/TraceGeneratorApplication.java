package com.sunnylabs.tracegenerator;

import com.wavefront.sdk.proxy.WavefrontProxyClient;
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
    private Configuration config;

    public static void main(String[] args) {
        SpringApplication.run(TraceGeneratorApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {
            InputStream inputStream = this.getClass().getClassLoader()
                    .getResourceAsStream("config.yaml");
            config = new Configuration(inputStream);

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
            }, 0, Integer.parseInt(System.getProperty("generator.send_frequency_ms", "30000")));
        };
    }

    private Operation randomEntrypoint() {
        return config.entrypoints().get(new Random().nextInt(config.entrypoints().size()));
    }
}
