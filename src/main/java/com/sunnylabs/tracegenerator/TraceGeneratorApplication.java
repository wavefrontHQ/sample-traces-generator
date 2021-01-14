package com.sunnylabs.tracegenerator;

import com.wavefront.sdk.proxy.WavefrontProxyClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@SpringBootApplication
public class TraceGeneratorApplication {
	public Logger log = Logger.getLogger(TraceSender.class.toString());

	private WavefrontProxyClient client;
	private TraceSender traceSender;

	public static void main(String[] args) {
		SpringApplication.run(TraceGeneratorApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		return args -> {
			InputStream inputStream = this.getClass()
					.getClassLoader()
					.getResourceAsStream("config.yaml");
			// ...
			Configuration config = new Configuration(inputStream);


			// TODO extend WavefrontClient instead of using deprecated class
			client = new WavefrontProxyClient.Builder("localhost").
					metricsPort(2878).tracingPort(30001).build();
			traceSender = new TraceSender(client);

			for (int i = 0; i < 100; i++) {
				sendTrace(config.entrypoints());
				TimeUnit.SECONDS.sleep(1);
			}

		};
	}

	private void sendTrace(List<Operation> entrypoints) {
		entrypoints.forEach((operation) -> {
			List<Span> spans = operation.generateTrace(traceSender.traceId);
			spans.forEach(traceSender::addSpan);
			try {
				traceSender.flush();
			} catch (IOException ignored) {

			}
		});
	}


}
