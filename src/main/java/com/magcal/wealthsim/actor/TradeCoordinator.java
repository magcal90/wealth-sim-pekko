package com.magcal.wealthsim.actor;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

import com.magcal.wealthsim.domain.AgentId;
import com.magcal.wealthsim.domain.AgentState;
import com.magcal.wealthsim.domain.Money;
import com.magcal.wealthsim.domain.StakePercentage;
import com.magcal.wealthsim.domain.TradeDecision;
import com.magcal.wealthsim.domain.YardSaleTrade;

public final class TradeCoordinator extends AbstractBehavior<TradeCoordinator.Command> {
    public sealed interface Command permits FirstReserved, SecondReserved, LeftStateReceived,
            RightStateReceived, DebitFinished, CreditFinished, CompensationFinished {
    }

    public record Participant(AgentId id, ActorRef<AgentActor.Command> actor) {
        public Participant {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(actor, "actor");
        }
    }

    public sealed interface Outcome permits Completed, Rejected {
        TradeId tradeId();
    }

    public record Completed(
            TradeId tradeId,
            AgentId left,
            AgentId right,
            AgentId winner,
            Money stake) implements Outcome {
    }

    public record Rejected(TradeId tradeId, FailureReason reason, Money stake) implements Outcome {
    }

    public enum FailureReason {
        INVALID_PARTICIPANTS,
        INVALID_WINNER,
        PARTICIPANT_BUSY,
        DEBIT_REJECTED,
        CREDIT_REJECTED_COMPENSATED,
        COMPENSATION_FAILED,
        TIMEOUT
    }

    private record FirstReserved(AgentActor.ReservationResult result, Throwable error) implements Command {
    }

    private record SecondReserved(AgentActor.ReservationResult result, Throwable error) implements Command {
    }

    private record LeftStateReceived(AgentState state, Throwable error) implements Command {
    }

    private record RightStateReceived(AgentState state, Throwable error) implements Command {
    }

    private record DebitFinished(AgentActor.OperationResult result, Throwable error) implements Command {
    }

    private record CreditFinished(AgentActor.OperationResult result, Throwable error) implements Command {
    }

    private record CompensationFinished(AgentActor.OperationResult result, Throwable error) implements Command {
    }

    private final TradeId tradeId;
    private final Participant left;
    private final Participant right;
    private final Participant first;
    private final Participant second;
    private final AgentId winner;
    private final StakePercentage stakePercentage;
    private final ActorRef<Outcome> replyTo;
    private final Duration timeout;
    private final Duration leaseDuration;
    private final YardSaleTrade yardSaleTrade = new YardSaleTrade();
    private AgentState leftState;
    private AgentState rightState;
    private TradeDecision decision;
    private FailureReason compensatedFailureReason;

    private TradeCoordinator(
            ActorContext<Command> context,
            TradeId tradeId,
            Participant left,
            Participant right,
            AgentId winner,
            StakePercentage stakePercentage,
            ActorRef<Outcome> replyTo,
            Duration timeout,
            Duration leaseDuration) {
        super(context);
        this.tradeId = Objects.requireNonNull(tradeId, "tradeId");
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
        this.winner = Objects.requireNonNull(winner, "winner");
        this.stakePercentage = Objects.requireNonNull(stakePercentage, "stakePercentage");
        this.replyTo = Objects.requireNonNull(replyTo, "replyTo");
        this.timeout = positive(timeout, "timeout");
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        if (left.id().compareTo(right.id()) <= 0) {
            this.first = left;
            this.second = right;
        } else {
            this.first = right;
            this.second = left;
        }
        start();
    }

    public static Behavior<Command> create(
            TradeId tradeId,
            Participant left,
            Participant right,
            AgentId winner,
            StakePercentage stakePercentage,
            ActorRef<Outcome> replyTo,
            Duration timeout,
            Duration leaseDuration) {
        if (left.id().equals(right.id()) || left.actor().equals(right.actor())) {
            replyTo.tell(new Rejected(tradeId, FailureReason.INVALID_PARTICIPANTS, Money.ZERO));
            return Behaviors.stopped();
        }
        if (!winner.equals(left.id()) && !winner.equals(right.id())) {
            replyTo.tell(new Rejected(tradeId, FailureReason.INVALID_WINNER, Money.ZERO));
            return Behaviors.stopped();
        }
        return Behaviors.setup(context -> new TradeCoordinator(
                context, tradeId, left, right, winner, stakePercentage,
                replyTo, timeout, leaseDuration));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(FirstReserved.class, this::onFirstReserved)
                .onMessage(SecondReserved.class, this::onSecondReserved)
                .onMessage(LeftStateReceived.class, this::onLeftState)
                .onMessage(RightStateReceived.class, this::onRightState)
                .onMessage(DebitFinished.class, this::onDebitFinished)
                .onMessage(CreditFinished.class, this::onCreditFinished)
                .onMessage(CompensationFinished.class, this::onCompensationFinished)
                .build();
    }

    private void start() {
        getContext().ask(
                AgentActor.ReservationResult.class,
                first.actor(), timeout,
                response -> new AgentActor.ReserveTrade(tradeId, leaseDuration, response),
                FirstReserved::new);
    }

    private Behavior<Command> onFirstReserved(FirstReserved message) {
        if (message.error() != null || message.result() == null || !message.result().accepted()) {
            return reject(message.error() == null ? FailureReason.PARTICIPANT_BUSY : FailureReason.TIMEOUT, false);
        }
        getContext().ask(
                AgentActor.ReservationResult.class,
                second.actor(), timeout,
                response -> new AgentActor.ReserveTrade(tradeId, leaseDuration, response),
                SecondReserved::new);
        return this;
    }

    private Behavior<Command> onSecondReserved(SecondReserved message) {
        if (message.error() != null || message.result() == null || !message.result().accepted()) {
            release(first);
            return reject(message.error() == null ? FailureReason.PARTICIPANT_BUSY : FailureReason.TIMEOUT, false);
        }
        getContext().ask(AgentState.class, left.actor(), timeout,
                AgentActor.GetState::new, LeftStateReceived::new);
        getContext().ask(AgentState.class, right.actor(), timeout,
                AgentActor.GetState::new, RightStateReceived::new);
        return this;
    }

    private Behavior<Command> onLeftState(LeftStateReceived message) {
        if (message.error() != null || message.state() == null) {
            return reject(FailureReason.TIMEOUT, true);
        }
        leftState = message.state();
        return decideWhenReady();
    }

    private Behavior<Command> onRightState(RightStateReceived message) {
        if (message.error() != null || message.state() == null) {
            return reject(FailureReason.TIMEOUT, true);
        }
        rightState = message.state();
        return decideWhenReady();
    }

    private Behavior<Command> decideWhenReady() {
        if (leftState == null || rightState == null) {
            return this;
        }
        decision = yardSaleTrade.decide(leftState, rightState, stakePercentage, winner);
        if (decision.stake().isZero()) {
            releaseBoth();
            replyTo.tell(new Completed(tradeId, left.id(), right.id(), winner, Money.ZERO));
            return Behaviors.stopped();
        }
        Participant loser = participant(decision.loser());
        getContext().ask(
                AgentActor.OperationResult.class,
                loser.actor(), timeout,
                response -> new AgentActor.DebitForTrade(tradeId, decision.stake(), response),
                DebitFinished::new);
        return this;
    }

    private Behavior<Command> onDebitFinished(DebitFinished message) {
        if (message.error() != null || message.result() == null) {
            compensatedFailureReason = FailureReason.TIMEOUT;
            requestCompensation();
            return this;
        }
        if (!message.result().accepted()) {
            return reject(FailureReason.DEBIT_REJECTED, true);
        }
        Participant winningParticipant = participant(decision.winner());
        getContext().ask(
                AgentActor.OperationResult.class,
                winningParticipant.actor(), timeout,
                response -> new AgentActor.CreditForTrade(tradeId, decision.stake(), response),
                CreditFinished::new);
        return this;
    }

    private Behavior<Command> onCreditFinished(CreditFinished message) {
        if (message.error() == null && message.result() != null && message.result().accepted()) {
            releaseBoth();
            replyTo.tell(new Completed(tradeId, left.id(), right.id(), winner, decision.stake()));
            return Behaviors.stopped();
        }
        compensatedFailureReason = FailureReason.CREDIT_REJECTED_COMPENSATED;
        requestCompensation();
        return this;
    }

    private Behavior<Command> onCompensationFinished(CompensationFinished message) {
        boolean compensated = message.error() == null
                && message.result() != null
                && message.result().accepted();
        releaseBoth();
        replyTo.tell(new Rejected(
                tradeId,
                compensated ? compensatedFailureReason : FailureReason.COMPENSATION_FAILED,
                decision.stake()));
        return Behaviors.stopped();
    }

    private void requestCompensation() {
        Participant loser = participant(decision.loser());
        getContext().ask(
                AgentActor.OperationResult.class,
                loser.actor(), timeout,
                response -> new AgentActor.CreditForTrade(tradeId, decision.stake(), response),
                CompensationFinished::new);
    }

    private Behavior<Command> reject(FailureReason reason, boolean releaseBoth) {
        if (releaseBoth) {
            releaseBoth();
        }
        replyTo.tell(new Rejected(tradeId, reason, decision == null ? Money.ZERO : decision.stake()));
        return Behaviors.stopped();
    }

    private void releaseBoth() {
        release(left);
        release(right);
    }

    private void release(Participant participant) {
        participant.actor().tell(new AgentActor.ReleaseTrade(
                tradeId, getContext().getSystem().ignoreRef()));
    }

    private Participant participant(AgentId id) {
        return left.id().equals(id) ? left : right;
    }

    private static Duration positive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
