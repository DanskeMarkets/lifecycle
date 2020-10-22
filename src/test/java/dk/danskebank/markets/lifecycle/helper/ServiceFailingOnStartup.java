package dk.danskebank.markets.lifecycle.helper;

import dk.danskebank.markets.lifecycle.Lifecycle;
import lombok.ToString;

@ToString
public class ServiceFailingOnStartup implements Lifecycle {
	@Override public void start() {
		throw new RuntimeException("Service failed on startup.");
	}

	@Override public void shutdown() {
		// Empty.
	}
}
