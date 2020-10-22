package dk.danskebank.markets.lifecycle.helper;

import dk.danskebank.markets.lifecycle.Lifecycle;
import lombok.ToString;

@ToString
public class ServiceNeverStarting implements Lifecycle {
	@Override public void start() {
		try {
			synchronized (this) {
				this.wait();
			}
		} catch (InterruptedException e) {
			// Ignore.
		}
	}

	@Override public void shutdown() {
		// Empty.
	}

	@Override public String name() {
		return "ServiceNeverStarting";
	}
}
