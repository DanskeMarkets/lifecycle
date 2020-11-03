package dk.danskebank.markets.lifecycle;

import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
import java.util.*;

import static java.util.stream.Collectors.toList;

@Slf4j
public class Orchestration {
	public final static Duration DEFAULT_START_TIMEOUT    = Duration.ofSeconds(60);
	public final static Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds( 1);

	private final List<Step> sequence;
	private final boolean reverseShutdown;

	private int lastStartedIndex = -1;

	private Orchestration(List<Step> sequence, boolean reverseShutdown) {
		this.sequence        = sequence;
		this.reverseShutdown = reverseShutdown;
	}

	/**
	 * Creates a builder for orchestration and specifies the first service(s) that should start.
	 * @param service The first service to start.
	 * @param concurrentlyWith Other services to start concurrently with the first.
	 * @return A Builder with default start and shutdown timeouts.
	 */
	public static Builder.WithDefaultTimeout firstStart(@NonNull Lifecycle service, Lifecycle... concurrentlyWith) {
		return new Builder().then(service, concurrentlyWith);
	}

	/** Starts all steps in the Orchestration. */
	@Synchronized public void start() {
		for (int i = 0; i < sequence.size(); ++i) {
			val step = sequence.get(i);
			log.info("Starting {}...", step);
			step.start();
			lastStartedIndex = i;
		}
	}

	/** Shuts down each step in the Orchestration in the specified order. */
	@Synchronized public void shutdown() {
		if (reverseShutdown) {
			for (int i = lastStartedIndex; i >= 0; --i) {
				shutdown(i);
			}
		}
		else {
			for (int i = 0; i <= lastStartedIndex; ++i) {
				shutdown(i);
			}
		}
	}

	private void shutdown(int stepNo) {
		val step = sequence.get(stepNo);
		log.info("Shutting down {}...", step);
		step.shutdown();
	}

	private static class Step implements Thread.UncaughtExceptionHandler {
		private final List<Lifecycle> lifecycles;
		private final long startTimeoutMs;
		private final long shutdownTimeoutMs;

		private final Thread[] startThreads;
		private final Thread[] shutdownThreads;

		private Throwable throwable;

		private Step(List<Lifecycle> lifecycles, Duration startTimeout, Duration shutdownTimeout) {
			this.lifecycles        = lifecycles;
			this.startTimeoutMs    = startTimeout.toMillis();
			this.shutdownTimeoutMs = shutdownTimeout.toMillis();
			this.startThreads      = new Thread[lifecycles.size()];
			this.shutdownThreads   = new Thread[lifecycles.size()];

			for (int i = 0; i < lifecycles.size(); ++i) {
				val lifecycle      = lifecycles.get(i);
				val startThread    = new Thread(lifecycle::start);
				val shutdownThread = new Thread(lifecycle::shutdown);
				startThreads[i]    = startThread;
				shutdownThreads[i] = shutdownThread;
				startThread.setUncaughtExceptionHandler(this);
				shutdownThread.setUncaughtExceptionHandler(this);
			}
		}

		@Override public void uncaughtException(Thread thread, Throwable throwable) {
			this.throwable = throwable;
		}

		private void start() {
			concurrently("start", startThreads, startTimeoutMs);
		}

		private void shutdown() {
			concurrently("shut down", shutdownThreads, shutdownTimeoutMs);
		}

		private void concurrently(String action, Thread[] threads, long timeoutMs) {
			this.throwable = null;

			for (val thread: threads) {
				thread.start();
			}

			joinWithinTimeoutOrThrow(action, threads, timeoutMs);

			if (this.throwable != null) {
				throw new RuntimeException(capitalize(action)+" threw one or more Throwables.", throwable);
			}
		}

		private static String capitalize(@NonNull String action) {
			return action.substring(0, 1).toUpperCase() + action.substring(1);
		}

		private void joinWithinTimeoutOrThrow(String action, Thread[] threads, long timeoutMs) {
			long deadline = System.currentTimeMillis() + timeoutMs;
			for (int i = 0; i < threads.length; ++i) {
				val thread = threads[i];
				try {
					long remainingTimeout = deadline - System.currentTimeMillis();
					if (remainingTimeout <= 0) { // Previous lifecycle did not complete action within timeout.
						throw new RuntimeException("No more time for "+action+"! ("+timeoutMs+" ms). Service "+lifecycles.get(i).name()+" was next.");
					}
					thread.join(remainingTimeout);
					if (thread.isAlive()) {
						throw new RuntimeException("Service "+lifecycles.get(i).name()+" did not "+action+" within timeout! ("+timeoutMs+" ms)");
					}
				} catch (InterruptedException e) {
					throw new AssertionError("Thread should never be interrupted.", e);
				}
			}
		}

		@Override public String toString() {
			return String.join(", ",
					lifecycles.stream()
							.map(Lifecycle::name)
							.collect(toList())
			);
		}
	}

	public static class Builder {
		private final Set<Lifecycle> seenLifecycles = new HashSet<>();

		private final List<Step> steps = new ArrayList<>();

		private Builder() {}

		public class WithDefaultTimeout {
			private final List<Lifecycle> currentLifeCycles;

			private WithDefaultTimeout(List<Lifecycle> lifecycles) {
				ensureLifecyclesAreNotAlreadyAdded(lifecycles);
				currentLifeCycles = lifecycles;
			}

			private void ensureLifecyclesAreNotAlreadyAdded(List<Lifecycle> lifecycles) {
				for (val lifecycle: lifecycles) {
					if (seenLifecycles.contains(lifecycle)) {
						throw new IllegalArgumentException("Lifecycle already orchestrated: "+lifecycle.name());
					}
					seenLifecycles.add(lifecycle);
				}
			}

			/**
			 * Specify non-default timeouts for starting and shutting down service(s) at this step.
			 * @param startTimeout The start timeout.
			 * @param shutdownTimeout The shutdown timeout.
			 * @return A Builder for continued orchestration.
			 */
			public Builder with(@NonNull Duration startTimeout, @NonNull Duration shutdownTimeout) {
				steps.add(new Step(currentLifeCycles, startTimeout, shutdownTimeout));
				return Builder.this;
			}

			/**
			 * Schedules one or more services to start at this step.
			 * @param service The service to start and stop at this step.
			 * @param concurrentlyWith The other services to start and stop concurrently at this step.
			 * @return A builder object with default timeouts.
			 */
			public WithDefaultTimeout then(@NonNull Lifecycle service, Lifecycle... concurrentlyWith) {
				commitCurrentLifecycles();
				return new WithDefaultTimeout(listOf(service, concurrentlyWith));
			}

			/**
			 * <p>Finishes this Orchestration.
			 * <p>Specifies that services should be shut down in reverse order.
			 * @return An {@link Orchestration} that shuts down life cycles in the reverse order they were started.
			 */
			public Orchestration andShutdownInReverseOrder() {
				commitCurrentLifecycles();
				return Builder.this.andShutdownInReverseOrder();
			}

			/**
			 * <p>Finishes this Orchestration.
			 * <p>The default choice should be {@link #andShutdownInReverseOrder()}! Use with care.
			 * @return An {@link Orchestration} that shuts down life cycles in the same order they were started.
			 */
			public Orchestration andShutdownInSameOrder() {
				commitCurrentLifecycles();
				return Builder.this.andShutdownInSameOrder();
			}

			private void commitCurrentLifecycles() {
				steps.add(new Step(currentLifeCycles, DEFAULT_START_TIMEOUT, DEFAULT_SHUTDOWN_TIMEOUT));
			}
		}

		/**
		 * Schedules one or more services to start at this step.
		 * @param service The service to start and stop at this step.
		 * @param concurrentlyWith The other services to start and stop concurrently at this step.
		 * @return A builder object with default timeouts.
		 */
		public WithDefaultTimeout then(@NonNull Lifecycle service, Lifecycle... concurrentlyWith) {
			return new WithDefaultTimeout(listOf(service, concurrentlyWith));
		}

		/**
		 * <p>Finishes this Orchestration.
		 * <p>Specifies that services should be shut down in reverse order.
		 * @return An {@link Orchestration} that shuts down life cycles in the reverse order they were started.
		 */
		public Orchestration andShutdownInReverseOrder() {
			return new Orchestration(steps, true);
		}

		/**
		 * <p>Finishes this Orchestration.
		 * <p>The default choice should be {@link #andShutdownInReverseOrder()}! Use with care.
		 * @return An {@link Orchestration} that shuts down life cycles in the same order they were started.
		 */
		public Orchestration andShutdownInSameOrder() {
			return new Orchestration(steps, false);
		}
	}

	private static List<Lifecycle> listOf(Lifecycle service, Lifecycle[] concurrentlyWith) {
		List<Lifecycle> list = new ArrayList<>();
		list.add(service);
		list.addAll(Arrays.asList(concurrentlyWith));
		return list;
	}
}
