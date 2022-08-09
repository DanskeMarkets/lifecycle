package dk.danskebank.markets.lifecycle;

import dk.danskebank.markets.lifecycle.helper.*;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@DisplayName("An Orchestration")
public class OrchestrationTest {
	@Test @DisplayName("should throw an IllegalArgumentException if the same Lifecycle is scheduled twice")
	void sameServiceScheduledTwiceThrows() {
		val lifecycle = mock(Lifecycle.class);
		when(lifecycle.name()).thenReturn("Service A");

		val exception = assertThrows(IllegalArgumentException.class, () ->
				Orchestration
						.firstStart(lifecycle)
						.then(lifecycle)
						.andShutdownInReverseOrder()
		);
		assertEquals("Lifecycle already orchestrated: Service A", exception.getMessage());
	}

	@Test @DisplayName("should throw an IllegalStateException if #then calls are not chained correctly")
	void notChainingThenCallsCorrectlyThrows() {
		val exception = assertThrows(IllegalStateException.class, () -> {
			val builder = Orchestration.firstStart(new Service());
			builder.then(new Service()); // Here the object returned is thrown away and not used in the chain.
			builder.then(new Service()); // Here we detect it and throw.
		});
		assertEquals("Calls must be chained. Look in your code and see if you " +
				"forgot to store a returned instance from the #then call.", exception.getMessage());
	}

	@Test @DisplayName("should throw an IllegalStateException if #then calls are not chained correctly")
	void notChainingThenCallsCorrectlyThrows2() {
		val exception = assertThrows(IllegalStateException.class, () -> {
			val builder = Orchestration.firstStart(new Service()).with(Duration.ofSeconds(2), Duration.ofSeconds(3));
			builder.then(new Service()); // Here the object returned is thrown away and not used in the chain.
			builder.then(new Service()); // Here we detect it and throw.
		});
		assertEquals("Calls must be chained. Look in your code and see if you " +
				"forgot to store a returned instance from the #then call.", exception.getMessage());
	}

	@Test @DisplayName("should throw an IllegalStateException if #andShutdownInReverseOrder calls are not chained correctly")
	void notChainingAndShutdownInReverseOrderCallsCorrectlyThrows() {
		val exception = assertThrows(IllegalStateException.class, () -> {
			val builder = Orchestration.firstStart(new Service());
			builder.then(new Service());         // Here the object returned is not used in the chain.
			builder.andShutdownInReverseOrder(); // Here we detect it and throw.
		});
		assertEquals("Calls must be chained. Look in your code and see if you " +
				"forgot to store a returned instance from the #then call.", exception.getMessage());
	}

	@Test @DisplayName("should throw an IllegalStateException if #andShutdownInReverseOrder calls are not chained correctly")
	void notChainingAndShutdownInReverseOrderCallsCorrectlyThrows2() {
		val exception = assertThrows(IllegalStateException.class, () -> {
			val builder = Orchestration.firstStart(new Service()).with(Duration.ofSeconds(2), Duration.ofSeconds(3));
			builder.then(new Service());         // Here the object returned is not used in the chain.
			builder.andShutdownInReverseOrder(); // Here we detect it and throw.
		});
		assertEquals("Calls must be chained. Look in your code and see if you " +
				"forgot to store a returned instance from the #then call.", exception.getMessage());
	}

	@Test @DisplayName("should throw an IllegalStateException if calls are not chained correctly")
	void withCallsNotChainedCorrectlyThrows() {
		val service1 = new Service();
		val service2 = new Service();

		val exception = assertThrows(IllegalStateException.class, () -> {
			val builder = Orchestration.firstStart(service1);
			builder.then(service2);                                   // Here the object returned is not used in the chain.
			builder.with(Duration.ofSeconds(2), Duration.ofSeconds(3)); // Here we detect it and throw.
		});
		assertEquals("Calls must be chained. Look in your code and see if you " +
				"forgot to store a returned instance from the #then call.", exception.getMessage());
	}

	@Test @DisplayName("should stop starting Lifecycles and throw a RuntimeException if a Lifecycle throws an exception during start")
	void throwsARuntimeExceptionIfLifecycleThrowsDuringStart() {
		val service1      = mock(Lifecycle.class);
		val service2      = mock(Lifecycle.class);
		val orchestration = Orchestration
				.firstStart(service1)
				.then(new ServiceFailingOnStartup())
				.then(service2)
				.andShutdownInReverseOrder();

		val exception = assertThrows(RuntimeException.class, orchestration::start);

		verify(service1).start();

		assertEquals("Start threw one or more Throwables.", exception.getMessage());
		val cause     = exception.getCause();
		assertEquals("Service failed on startup.", cause.getMessage());

		verify(service2, never()).start();
	}

	@Test @DisplayName("should stop shutting down Lifecycles and throw a RuntimeException if a Lifecycle throws an exception during shutdown")
	void throwsARuntimeExceptionIfLifecycleThrowsDuringShutdown() {
		val service1      = mock(Lifecycle.class);
		val service2      = mock(Lifecycle.class);
		val ordering      = inOrder(service1, service2);
		val orchestration = Orchestration
				.firstStart(service1)
				.then(new ServiceFailingOnShutdown())
				.then(service2)
				.andShutdownInReverseOrder();

		// Start
		orchestration.start();
		ordering.verify(service1).start();
		ordering.verify(service2).start();

		// Shutdown
		val exception = assertThrows(RuntimeException.class, orchestration::shutdown);
		ordering.verify(service2).shutdown();
		assertEquals("Shut down threw one or more Throwables.", exception.getMessage());
		val cause     = exception.getCause();
		assertEquals("Service failed on shutdown.", cause.getMessage());
		ordering.verify(service1, never()).shutdown();
	}

	@Test @DisplayName("should throw a RuntimeException if a Lifecycle times out during start")
	void throwsARuntimeExceptionIfLifecycleTimesOutDuringStart() {
		val service1      = mock(Lifecycle.class);
		val service2      = mock(Lifecycle.class);
		val orchestration = Orchestration
				.firstStart(service1)
				.then(new ServiceNeverStarting()).with(Duration.ofMillis(50), Duration.ofMillis(50))
				.then(service2)
				.andShutdownInReverseOrder();

		val exception = assertThrows(RuntimeException.class, orchestration::start);

		verify(service1).start();
		assertEquals("Service ServiceNeverStarting did not start within timeout! (50 ms)", exception.getMessage());
		verify(service2, never()).start();
	}

	@Test @DisplayName("should throw a RuntimeException if a Lifecycle times out during shutdown")
	void throwsARuntimeExceptionIfLifecycleTimesOutDuringShutdown() {
		val service1      = mock(Lifecycle.class);
		val service2      = mock(Lifecycle.class);
		val ordering      = inOrder(service1, service2);
		val orchestration = Orchestration
				.firstStart(service1)
				.then(new ServiceNeverShuttingDown()).with(Duration.ofMillis(50), Duration.ofMillis(50))
				.then(service2)
				.andShutdownInReverseOrder();

		// Start
		orchestration.start();
		ordering.verify(service1).start();
		ordering.verify(service2).start();

		// Shutdown
		val exception = assertThrows(RuntimeException.class, orchestration::shutdown);
		ordering.verify(service2).shutdown();
		assertEquals("Service ServiceNeverShuttingDown did not shut down within timeout! (50 ms)", exception.getMessage());
		ordering.verify(service1, never()).shutdown();
	}

	@Test @DisplayName("should start lifecycles in sequence and shutdown in reverse order.")
	void shouldStartAndShutdownLifecyclesInReverseOrder() {
		val service1      = mock(Lifecycle.class);
		val service2      = mock(Lifecycle.class);
		val service3      = mock(Lifecycle.class);
		val service4      = mock(Lifecycle.class);
		val orderingA     = inOrder(service1, service2, service4);
		val orderingB     = inOrder(service1, service3, service4);
		val orchestration = Orchestration
				.firstStart(service1)
				.then(service2, service3) // Parallel step.
				.then(service4)
				.andShutdownInReverseOrder();

		// Start
		orchestration.start();

		// Verify ordering A.
		orderingA.verify(service1).start();
		orderingA.verify(service2).start();
		orderingA.verify(service4).start();

		// Verify ordering B.
		orderingB.verify(service1).start();
		orderingB.verify(service3).start();
		orderingB.verify(service4).start();

		// Shutdown
		orchestration.shutdown();

		// Verify ordering A.
		orderingA.verify(service4).shutdown();
		orderingA.verify(service2).shutdown();
		orderingA.verify(service1).shutdown();

		// Verify ordering B.
		orderingB.verify(service4).shutdown();
		orderingB.verify(service3).shutdown();
		orderingB.verify(service1).shutdown();
	}

	@Test @DisplayName("should start lifecycles in sequence and shutdown in same order.")
	void shouldStartAndShutdownLifecyclesInSameOrder() {
		val service1      = mock(Lifecycle.class);
		val service2      = mock(Lifecycle.class);
		val service3      = mock(Lifecycle.class);
		val service4      = mock(Lifecycle.class);
		val orderingA     = inOrder(service1, service2, service4);
		val orderingB     = inOrder(service1, service3, service4);
		val orchestration = Orchestration
				.firstStart(service1)
				.then(service2, service3) // Parallel step.
				.then(service4)
				.andShutdownInSameOrder();

		// Start
		orchestration.start();

		// Verify ordering A.
		orderingA.verify(service1).start();
		orderingA.verify(service2).start();
		orderingA.verify(service4).start();

		// Verify ordering B.
		orderingB.verify(service1).start();
		orderingB.verify(service3).start();
		orderingB.verify(service4).start();

		// Shutdown
		orchestration.shutdown();

		// Verify ordering A.
		orderingA.verify(service1).shutdown();
		orderingA.verify(service2).shutdown();
		orderingA.verify(service4).shutdown();

		// Verify ordering B.
		orderingB.verify(service1).shutdown();
		orderingB.verify(service3).shutdown();
		orderingB.verify(service4).shutdown();
	}
}
