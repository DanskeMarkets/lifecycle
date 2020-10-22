# Life Cycle

## Introduction

This library contains small, composable utilities related to managing the life cycle of components.

"But why?", you may ask. How hard can it be? Well, what should happen if one of your services never starts and hangs forever?
Wouldn't it be nice with a timeout? Are you always shutting down your services in exact reverse order of starting them?
And what happens if one of your services fails during a startup sequence? Do you shutdown all started services in the
sequence so far?
Have you checked (manually) that you don't start the same service instance more than once?
What if some of your services can be started in parallel to speed up the startup/shutdown phase?

This library handles all this complexity, so you don't have to.

## Guide

To use this library, add it as a dependency (replace `x.y.z` with the newest version):

**Maven:**

    <dependency>
      <groupId>dk.danskebank.markets</groupId>
      <artifactId>lifecycle</artifactId>
      <version>x.y.z</version>
    </dependency>

**Gradle:**

    implementation group: 'dk.danskebank.markets', name: 'event-router', version: 'x.y.z'

Any service participating in the orchestration needs to implement the `Lifecycle` interface:

    public interface Lifecycle {
              void   start();
      default void   shutdown() { /* Do nothing. */  }
      default String name()     { return getClass().getSimpleName(); }
    }

As you can see, it's pretty straight forward. The `name` method is for having nice names in the logs.

Now, you can orchestrate the start and shutdown ordering of your services:

    val orchestration = Orchestration
      .firstStart(service1)
      .then(      service2)
      .then(      service3)
      .andShutdownInReverseOrder(); // This should always be the default. Shutdown in "same" order is also supported.

    orchestration.start();

    // Much later...

    orchestration.shutdown();

The default timeout for a service starting is 60 seconds and shutting down is 1 second. This can be overwritten:

    val startTimeout    = Duration.ofMillis(200);
    val shutdownTimeout = Duration.ofMillis( 50);
    val orchestration   = Orchestration
      .firstStart(service1)
      .then(service2).with(startTimeout, shutdownTimeout)
      .then(service3)
      .andShutdownInReverseOrder();

If a timeout breaches, a `RuntimeException` will be thrown, and the start (or shutdown) sequence stops.
The `Orchestration` will remember what services started (successfully) so a later `shutdown()` stops only the
running services.

To start/shutdown services in parallel, you add more services to each step:

    val orchestration = Orchestration
      .firstStart(service1, service2)
      .then(service3, service4)
      .then(service3)
      .andShutdownInReverseOrder();

Non-default timeouts apply to all services on the same step. E.g.

    val startTimeout    = Duration.ofMillis(200);
    val shutdownTimeout = Duration.ofMillis( 50);
    val orchestration = Orchestration
      .firstStart(service1, service2).with(startTimeout, shutdownTimeout)
      .then(service3, service4)
      .then(service3)
      .andShutdownInReverseOrder();

means that `service1` and `service2` should each start and shutdown within 200ms/50 ms (in parallel).

### Tips and Tricks

If your code does not (and cannot easily) implement the `Lifecycle` interface, just add an anonymous class and delegate
the calls:

    val orchestration = Orchestration
       .firstStart(service1)
       .then(new Lifecycle() {
         @Override public void start() { service.initiate(); }
         @Override public void shutdown() { service.close(); }
       })
       .then(service3)
       .andShutdownInReverseOrder();

This technique can also be used to e.g. add validation code in the start sequence:

    val orchestration = Orchestration
       .firstStart(service1)
       .then(service2)
       .then(() -> {
         if (!service1.agreesWith(service2)) throw new RuntimeException("Service 1 & 2 disagrees.");
       })
       .then(service2)
       .then(service3)
       .andShutdownInReverseOrder();

Or manually start a service somewhere else but include it in the shutdown sequence:

    val orchestration = Orchestration
       .firstStart(service1)
       .then(new Lifecycle() {// for Service2.
         @Override public void start() { /* Started manually elsewhere. */ }
         @Override public void shutdown() { service2.shutdown(); }
       })
       .then(service3)
       .andShutdownInReverseOrder();