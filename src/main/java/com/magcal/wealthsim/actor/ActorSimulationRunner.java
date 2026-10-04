package com.magcal.wealthsim.actor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import com.magcal.wealthsim.application.SimulationConfiguration;
import com.magcal.wealthsim.application.SimulationResult;

public final class ActorSimulationRunner {
    public SimulationResult run(SimulationConfiguration configuration) throws Exception {
        CompletableFuture<SimulationCoordinator.Outcome> completion = new CompletableFuture<>();
        ActorSystem<SimulationCoordinator.Outcome> system = ActorSystem.create(
                runBehavior(configuration, completion), "wealth-sim-actor-run");
        try {
            SimulationCoordinator.Outcome outcome = completion.get();
            if (outcome instanceof SimulationCoordinator.Completed completed) {
                return completed.result();
            }
            SimulationCoordinator.Failed failed = (SimulationCoordinator.Failed) outcome;
            throw new IllegalStateException(failed.reason());
        } finally {
            system.terminate();
            system.getWhenTerminated()
                    .toCompletableFuture()
                    .get(ActorRuntimeSettings.REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private Behavior<SimulationCoordinator.Outcome> runBehavior(
            SimulationConfiguration configuration,
            CompletableFuture<SimulationCoordinator.Outcome> completion) {
        return Behaviors.setup(context -> {
            ActorRef<SimulationCoordinator.Command> coordinator = context.spawn(
                    SimulationCoordinator.create(), "simulation");
            context.watchWith(
                    coordinator,
                    new SimulationCoordinator.Failed("Simulation coordinator stopped without a result"));
            coordinator.tell(new SimulationCoordinator.Start(configuration, context.getSelf()));
            return Behaviors.receiveMessage(outcome -> {
                completion.complete(outcome);
                return Behaviors.stopped();
            });
        });
    }
}
