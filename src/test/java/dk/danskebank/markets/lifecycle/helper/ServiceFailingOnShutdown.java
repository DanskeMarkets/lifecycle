package dk.danskebank.markets.lifecycle.helper;

import dk.danskebank.markets.lifecycle.Lifecycle;
import lombok.ToString;

@ToString
public class ServiceFailingOnShutdown implements Lifecycle {
	@Override public void start() {
		// Empty.
	}

	@Override public void shutdown() {
		throw new RuntimeException("Service failed on shutdown.");
	}
}
