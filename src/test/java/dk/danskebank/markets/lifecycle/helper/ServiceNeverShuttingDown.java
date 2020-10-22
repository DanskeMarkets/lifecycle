package dk.danskebank.markets.lifecycle.helper;

import dk.danskebank.markets.lifecycle.Lifecycle;
import lombok.ToString;

@ToString
public class ServiceNeverShuttingDown implements Lifecycle {
	@Override public void start() {
		// Empty.
	}

	@Override public void shutdown() {
		try {
			synchronized (this) {
				this.wait();
			}
		} catch (InterruptedException e) {
			// Ignore.
		}
	}

	@Override public String name() {
		return "ServiceNeverShuttingDown";
	}
}
