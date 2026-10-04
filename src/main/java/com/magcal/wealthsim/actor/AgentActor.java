package com.magcal.wealthsim.actor;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;

import com.magcal.wealthsim.domain.AgentId;
import com.magcal.wealthsim.domain.AgentState;
import com.magcal.wealthsim.domain.Money;

public final class AgentActor {
    private AgentActor() {
    }

    public sealed interface Command permits GetState, Credit, Debit, ReserveTrade,
            ReleaseTrade, DebitForTrade, CreditForTrade, GetReservation, ExpireReservation {
    }

    public record GetState(ActorRef<AgentState> replyTo) implements Command {
        public GetState {
            Objects.requireNonNull(replyTo, "replyTo");
        }
    }

    public record Credit(Money amount, ActorRef<OperationResult> replyTo) implements Command {
        public Credit {
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(replyTo, "replyTo");
        }
    }

    public record Debit(Money amount, ActorRef<OperationResult> replyTo) implements Command {
        public Debit {
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(replyTo, "replyTo");
        }
    }

    public record ReserveTrade(
            TradeId tradeId,
            Duration leaseDuration,
            ActorRef<ReservationResult> replyTo) implements Command {
        public ReserveTrade {
            Objects.requireNonNull(tradeId, "tradeId");
            Objects.requireNonNull(leaseDuration, "leaseDuration");
            Objects.requireNonNull(replyTo, "replyTo");
            if (leaseDuration.isZero() || leaseDuration.isNegative()) {
                throw new IllegalArgumentException("Lease duration must be positive");
            }
        }
    }

    public record ReleaseTrade(TradeId tradeId, ActorRef<ReleaseResult> replyTo) implements Command {
        public ReleaseTrade {
            Objects.requireNonNull(tradeId, "tradeId");
            Objects.requireNonNull(replyTo, "replyTo");
        }
    }

    public record DebitForTrade(
            TradeId tradeId,
            Money amount,
            ActorRef<OperationResult> replyTo) implements Command {
        public DebitForTrade {
            Objects.requireNonNull(tradeId, "tradeId");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(replyTo, "replyTo");
        }
    }

    public record CreditForTrade(
            TradeId tradeId,
            Money amount,
            ActorRef<OperationResult> replyTo) implements Command {
        public CreditForTrade {
            Objects.requireNonNull(tradeId, "tradeId");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(replyTo, "replyTo");
        }
    }

    public record GetReservation(ActorRef<ReservationState> replyTo) implements Command {
        public GetReservation {
            Objects.requireNonNull(replyTo, "replyTo");
        }
    }

    private record ExpireReservation(TradeId tradeId) implements Command {
    }

    public enum OperationFailure {
        INVALID_AMOUNT,
        INSUFFICIENT_WEALTH,
        PARTICIPANT_BUSY,
        RESERVATION_REQUIRED,
        WRONG_TRADE
    }

    public record OperationResult(boolean accepted, Optional<OperationFailure> failure) {
        public static OperationResult success() {
            return new OperationResult(true, Optional.empty());
        }

        public static OperationResult rejected(OperationFailure failure) {
            return new OperationResult(false, Optional.of(failure));
        }
    }

    public record ReservationResult(boolean accepted, Optional<TradeId> owner) {
    }

    public record ReleaseResult(boolean accepted, Optional<TradeId> owner) {
    }

    public record ReservationState(Optional<TradeId> owner) {
    }

    public static Behavior<Command> create(AgentId agentId, Money initialWealth) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(initialWealth, "initialWealth");
        if (initialWealth.isNegative()) {
            throw new IllegalArgumentException("Initial wealth must not be negative");
        }
        return Behaviors.withTimers(timers -> active(timers, agentId, initialWealth, Optional.empty()));
    }

    private static Behavior<Command> active(
            TimerScheduler<Command> timers,
            AgentId agentId,
            Money wealth,
            Optional<TradeId> reservation) {
        return Behaviors.receive(Command.class)
                .onMessage(GetState.class, command -> {
                    command.replyTo().tell(new AgentState(agentId, wealth));
                    return Behaviors.same();
                })
                .onMessage(GetReservation.class, command -> {
                    command.replyTo().tell(new ReservationState(reservation));
                    return Behaviors.same();
                })
                .onMessage(Credit.class, command -> administrativeChange(
                        timers, agentId, wealth, reservation, command.amount(), command.replyTo(), true))
                .onMessage(Debit.class, command -> administrativeChange(
                        timers, agentId, wealth, reservation, command.amount(), command.replyTo(), false))
                .onMessage(ReserveTrade.class, command -> reserve(
                        timers, agentId, wealth, reservation, command))
                .onMessage(ReleaseTrade.class, command -> release(
                        timers, agentId, wealth, reservation, command))
                .onMessage(DebitForTrade.class, command -> tradeChange(
                        timers, agentId, wealth, reservation,
                        command.tradeId(), command.amount(), command.replyTo(), false))
                .onMessage(CreditForTrade.class, command -> tradeChange(
                        timers, agentId, wealth, reservation,
                        command.tradeId(), command.amount(), command.replyTo(), true))
                .onMessage(ExpireReservation.class, command -> {
                    if (reservation.isPresent() && reservation.get().equals(command.tradeId())) {
                        return active(timers, agentId, wealth, Optional.empty());
                    }
                    return Behaviors.same();
                })
                .build();
    }

    private static Behavior<Command> administrativeChange(
            TimerScheduler<Command> timers,
            AgentId agentId,
            Money wealth,
            Optional<TradeId> reservation,
            Money amount,
            ActorRef<OperationResult> replyTo,
            boolean credit) {
        if (reservation.isPresent()) {
            replyTo.tell(OperationResult.rejected(OperationFailure.PARTICIPANT_BUSY));
            return Behaviors.same();
        }
        return change(timers, agentId, wealth, reservation, amount, replyTo, credit);
    }

    private static Behavior<Command> tradeChange(
            TimerScheduler<Command> timers,
            AgentId agentId,
            Money wealth,
            Optional<TradeId> reservation,
            TradeId tradeId,
            Money amount,
            ActorRef<OperationResult> replyTo,
            boolean credit) {
        if (reservation.isEmpty()) {
            replyTo.tell(OperationResult.rejected(OperationFailure.RESERVATION_REQUIRED));
            return Behaviors.same();
        }
        if (!reservation.get().equals(tradeId)) {
            replyTo.tell(OperationResult.rejected(OperationFailure.WRONG_TRADE));
            return Behaviors.same();
        }
        return change(timers, agentId, wealth, reservation, amount, replyTo, credit);
    }

    private static Behavior<Command> change(
            TimerScheduler<Command> timers,
            AgentId agentId,
            Money wealth,
            Optional<TradeId> reservation,
            Money amount,
            ActorRef<OperationResult> replyTo,
            boolean credit) {
        if (amount.isNegative()) {
            replyTo.tell(OperationResult.rejected(OperationFailure.INVALID_AMOUNT));
            return Behaviors.same();
        }
        Money updated = credit ? wealth.add(amount) : wealth.subtract(amount);
        if (updated.isNegative()) {
            replyTo.tell(OperationResult.rejected(OperationFailure.INSUFFICIENT_WEALTH));
            return Behaviors.same();
        }
        replyTo.tell(OperationResult.success());
        return active(timers, agentId, updated, reservation);
    }

    private static Behavior<Command> reserve(
            TimerScheduler<Command> timers,
            AgentId agentId,
            Money wealth,
            Optional<TradeId> reservation,
            ReserveTrade command) {
        if (reservation.isPresent() && !reservation.get().equals(command.tradeId())) {
            command.replyTo().tell(new ReservationResult(false, reservation));
            return Behaviors.same();
        }
        timers.startSingleTimer(command.tradeId(), new ExpireReservation(command.tradeId()), command.leaseDuration());
        command.replyTo().tell(new ReservationResult(true, Optional.of(command.tradeId())));
        return active(timers, agentId, wealth, Optional.of(command.tradeId()));
    }

    private static Behavior<Command> release(
            TimerScheduler<Command> timers,
            AgentId agentId,
            Money wealth,
            Optional<TradeId> reservation,
            ReleaseTrade command) {
        if (reservation.isEmpty()) {
            command.replyTo().tell(new ReleaseResult(true, Optional.empty()));
            return Behaviors.same();
        }
        if (!reservation.get().equals(command.tradeId())) {
            command.replyTo().tell(new ReleaseResult(false, reservation));
            return Behaviors.same();
        }
        timers.cancel(command.tradeId());
        command.replyTo().tell(new ReleaseResult(true, Optional.empty()));
        return active(timers, agentId, wealth, Optional.empty());
    }
}
