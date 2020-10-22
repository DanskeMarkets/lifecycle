package dk.danskebank.markets.lifecycle.helper;

import dk.danskebank.markets.lifecycle.Lifecycle;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@ToString @Getter @RequiredArgsConstructor
public class Service implements Lifecycle {
	private final double pnl;

	@Override public void start() {
		// Empty.
	}

	@Override public void shutdown() {
		// Empty.
	}

	@Override public String name() {
		return "Service";
	}
}
