package com.magcal.wealthsim.actor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

import com.magcal.wealthsim.application.SeededRandomSource;
import com.magcal.wealthsim.application.SimulationConfiguration;
import com.magcal.wealthsim.application.SimulationResult;
import com.magcal.wealthsim.application.TradeSelection;
import com.magcal.wealthsim.application.TradeSelector;
import com.magcal.wealthsim.domain.AgentId;
import com.magcal.wealthsim.domain.AgentState;
import com.magcal.wealthsim.domain.Population;

public final class SimulationCoordinator extends AbstractBehavior<SimulationCoordinator.Command> {
    public sealed interface Command permits Start, TradeFinished, FinalStateReceived {
    }

    public record Start(SimulationConfiguration configuration, ActorRef<Outcome> replyTo) implements Command {
        public Start {
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(replyTo, "replyTo");
        }
    }

    public sealed interface Outcome permits Completed, Failed {
    }

    public record Completed(SimulationResult result) implements Outcome {
    }

    public record Failed(String reason) implements Outcome {
    }

    private record TradeFinished(TradeCoordinator.Outcome outcome) implements Command {
    }

    private record FinalStateReceived(AgentId expectedId, AgentState state, Throwable error) implements Command {
    }

    private final Map<AgentId, ActorRef<AgentActor.Command>> agents = new LinkedHashMap<>();
    private final Map<AgentId, AgentState> finalStates = new LinkedHashMap<>();
    private SimulationConfiguration configuration;
    private ActorRef<Outcome> replyTo;
    private TradeSelector selector;
    private List<AgentId> identities;
    private ActorRef<TradeCoordinator.Outcome> tradeOutcomeAdapter;
    private int completedTrades;
    private boolean started;

    private SimulationCoordinator(ActorContext<Command> context) {
        super(context);
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(SimulationCoordinator::new);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Start.class, this::onStart)
                .onMessage(TradeFinished.class, this::onTradeFinished)
                .onMessage(FinalStateReceived.class, this::onFinalStateReceived)
                .build();
    }

    private Behavior<Command> onStart(Start start) {
        if (started) {
            start.replyTo().tell(new Failed("Simulation coordinator already started"));
            return this;
        }
        started = true;
        configuration = start.configuration();
        replyTo = start.replyTo();
        selector = new TradeSelector(new SeededRandomSource(configuration.seed()));
        tradeOutcomeAdapter = getContext().messageAdapter(
                TradeCoordinator.Outcome.class, TradeFinished::new);

        Population initial = Population.initialize(
                configuration.populationSize(), configuration.initialWealth());
        for (AgentState state : initial.agents()) {
            ActorRef<AgentActor.Command> actor = getContext().spawn(
                    AgentActor.create(state.id(), state.wealth()), state.id().value());
            agents.put(state.id(), actor);
        }
        identities = List.copyOf(agents.keySet());

        if (configuration.tradeCount() == 0) {
            collectFinalStates();
        } else {
            scheduleNextTrade();
        }
        return this;
    }

    private Behavior<Command> onTradeFinished(TradeFinished message) {
        if (message.outcome() instanceof TradeCoordinator.Rejected rejected) {
            return fail("Trade %s failed: %s".formatted(rejected.tradeId(), rejected.reason()));
        }
        completedTrades++;
        if (completedTrades == configuration.tradeCount()) {
            collectFinalStates();
        } else {
            scheduleNextTrade();
        }
        return this;
    }

    private void scheduleNextTrade() {
        TradeSelection selection = selector.next(identities);
        TradeId tradeId = new TradeId("trade-%06d".formatted(completedTrades + 1));
        TradeCoordinator.Participant left = new TradeCoordinator.Participant(
                selection.left(), agents.get(selection.left()));
        TradeCoordinator.Participant right = new TradeCoordinator.Participant(
                selection.right(), agents.get(selection.right()));
        getContext().spawnAnonymous(TradeCoordinator.create(
                tradeId,
                left,
                right,
                selection.winner(),
                configuration.stakePercentage(),
                tradeOutcomeAdapter,
                ActorRuntimeSettings.REQUEST_TIMEOUT,
                ActorRuntimeSettings.RESERVATION_LEASE));
    }

    private void collectFinalStates() {
        for (Map.Entry<AgentId, ActorRef<AgentActor.Command>> entry : agents.entrySet()) {
            AgentId expectedId = entry.getKey();
            getContext().ask(
                    AgentState.class,
                    entry.getValue(),
                    ActorRuntimeSettings.REQUEST_TIMEOUT,
                    AgentActor.GetState::new,
                    (state, error) -> new FinalStateReceived(expectedId, state, error));
        }
    }

    private Behavior<Command> onFinalStateReceived(FinalStateReceived message) {
        if (message.error() != null || message.state() == null) {
            return fail("Timed out collecting final state for " + message.expectedId());
        }
        if (!message.expectedId().equals(message.state().id())) {
            return fail("Agent identity changed during simulation");
        }
        finalStates.put(message.state().id(), message.state());
        if (finalStates.size() < agents.size()) {
            return this;
        }

        List<AgentState> ordered = new ArrayList<>(finalStates.values());
        ordered.sort((left, right) -> left.id().compareTo(right.id()));
        SimulationResult result = new SimulationResult(
                configuration, completedTrades, new Population(ordered));
        if (!result.totalWealth().equals(configuration.initialTotalWealth())) {
            return fail("Final wealth is not conserved");
        }
        replyTo.tell(new Completed(result));
        return Behaviors.stopped();
    }

    private Behavior<Command> fail(String reason) {
        replyTo.tell(new Failed(reason));
        return Behaviors.stopped();
    }
}
