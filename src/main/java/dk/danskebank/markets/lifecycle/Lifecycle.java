package dk.danskebank.markets.lifecycle;

public interface Lifecycle {
	/**
	 * <p>Invoked by {@link Orchestration} when this Lifecycle should be started.
	 * <p>The implementation of {@link Orchestration} guarantees it will be invoked at most once.
	 */
	void start();

	/**
	 * <p>Shuts down the component. The default implementation does nothing. Override if needed.
	 * <p>The implementation of {@link Orchestration} guarantees it will be invoked at most once.
	 */
	default void shutdown() { /* Do nothing. */ }

	/** @return The name of the Lifecycle for logs. Default implementation uses {@link Class#getSimpleName()}. */
	default String name() {
		return getClass().getSimpleName();
	}
}
