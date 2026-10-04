package com.magcal.wealthsim;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

public final class WealthSimApplication {
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(10);

    private WealthSimApplication() {
    }

    public static void main(String[] args) throws Exception {
        ActorSystem<Void> system = ActorSystem.create(rootBehavior(), "wealth-sim");
        system.terminate();
        system.getWhenTerminated()
                .toCompletableFuture()
                .get(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    static Behavior<Void> rootBehavior() {
        return Behaviors.setup(context -> {
            context.getLog().info("Wealth simulator runtime started");
            return Behaviors.empty();
        });
    }
}
