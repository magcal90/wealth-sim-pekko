package com.magcal.wealthsim.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.magcal.wealthsim.domain.AgentId;
import com.magcal.wealthsim.domain.AgentState;
import com.magcal.wealthsim.domain.Money;
import com.magcal.wealthsim.domain.StakePercentage;

class TradeCoordinatorTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final Duration LEASE = Duration.ofSeconds(5);
    private static ActorTestKit testKit;

    @BeforeAll
    static void startTestKit() {
        testKit = ActorTestKit.create();
    }

    @AfterAll
    static void stopTestKit() {
        testKit.shutdownTestKit();
    }

    @Test
    void completesTradeWithDomainStakeAndReleasesParticipants() {
        AgentId aId = new AgentId("A");
        AgentId bId = new AgentId("B");
        ActorRef<AgentActor.Command> a = testKit.spawn(AgentActor.create(aId, Money.of("100")));
        ActorRef<AgentActor.Command> b = testKit.spawn(AgentActor.create(bId, Money.of("50")));
        TestProbe<TradeCoordinator.Outcome> result = testKit.createTestProbe();

        testKit.spawn(TradeCoordinator.create(
                new TradeId("trade-success"), participant(aId, a), participant(bId, b), aId,
                StakePercentage.ofPercent("10"), result.ref(), TIMEOUT, LEASE));

        TradeCoordinator.Completed completed = assertInstanceOf(
                TradeCoordinator.Completed.class, result.receiveMessage());
        assertEquals(Money.of("5"), completed.stake());
        assertEquals(Money.of("105"), state(a).wealth());
        assertEquals(Money.of("45"), state(b).wealth());
        assertEquals(Optional.empty(), reservation(a).owner());
        assertEquals(Optional.empty(), reservation(b).owner());
    }

    @Test
    void releasesFirstReservationWhenSecondParticipantIsBusy() {
        AgentId aId = new AgentId("A2");
        AgentId bId = new AgentId("B2");
        ActorRef<AgentActor.Command> a = testKit.spawn(AgentActor.create(aId, Money.of("100")));
        ActorRef<AgentActor.Command> b = testKit.spawn(AgentActor.create(bId, Money.of("50")));
        TestProbe<AgentActor.ReservationResult> reserve = testKit.createTestProbe();
        b.tell(new AgentActor.ReserveTrade(new TradeId("other"), LEASE, reserve.ref()));
        assertTrue(reserve.receiveMessage().accepted());
        TestProbe<TradeCoordinator.Outcome> result = testKit.createTestProbe();

        testKit.spawn(TradeCoordinator.create(
                new TradeId("blocked"), participant(aId, a), participant(bId, b), aId,
                StakePercentage.ofPercent("10"), result.ref(), TIMEOUT, LEASE));

        TradeCoordinator.Rejected rejected = assertInstanceOf(
                TradeCoordinator.Rejected.class, result.receiveMessage());
        assertEquals(TradeCoordinator.FailureReason.PARTICIPANT_BUSY, rejected.reason());
        assertEquals(Optional.empty(), reservation(a).owner());
    }

    @Test
    void compensatesAfterCreditFailureAndSurfacesCompensationFailure() {
        AgentId aId = new AgentId("A3");
        AgentId bId = new AgentId("B3");
        TestProbe<AgentActor.Command> a = testKit.createTestProbe();
        TestProbe<AgentActor.Command> b = testKit.createTestProbe();
        TestProbe<TradeCoordinator.Outcome> result = testKit.createTestProbe();

        testKit.spawn(TradeCoordinator.create(
                new TradeId("compensation"), participant(aId, a.ref()), participant(bId, b.ref()), aId,
                StakePercentage.ofPercent("10"), result.ref(), TIMEOUT, LEASE));

        replyAccepted(a.expectMessageClass(AgentActor.ReserveTrade.class));
        replyAccepted(b.expectMessageClass(AgentActor.ReserveTrade.class));
        a.expectMessageClass(AgentActor.GetState.class).replyTo().tell(new AgentState(aId, Money.of("100")));
        b.expectMessageClass(AgentActor.GetState.class).replyTo().tell(new AgentState(bId, Money.of("50")));
        b.expectMessageClass(AgentActor.DebitForTrade.class).replyTo()
                .tell(AgentActor.OperationResult.success());
        a.expectMessageClass(AgentActor.CreditForTrade.class).replyTo()
                .tell(AgentActor.OperationResult.rejected(AgentActor.OperationFailure.INVALID_AMOUNT));
        b.expectMessageClass(AgentActor.CreditForTrade.class).replyTo()
                .tell(AgentActor.OperationResult.rejected(AgentActor.OperationFailure.INVALID_AMOUNT));

        TradeCoordinator.Rejected rejected = assertInstanceOf(
                TradeCoordinator.Rejected.class, result.receiveMessage());
        assertEquals(TradeCoordinator.FailureReason.COMPENSATION_FAILED, rejected.reason());
        a.expectMessageClass(AgentActor.ReleaseTrade.class);
        b.expectMessageClass(AgentActor.ReleaseTrade.class);
    }

    @Test
    void successfulCompensationReportsRejectedTradeAndReleasesBothParticipants() {
        AgentId aId = new AgentId("A4");
        AgentId bId = new AgentId("B4");
        TestProbe<AgentActor.Command> a = testKit.createTestProbe();
        TestProbe<AgentActor.Command> b = testKit.createTestProbe();
        TestProbe<TradeCoordinator.Outcome> result = testKit.createTestProbe();

        testKit.spawn(TradeCoordinator.create(
                new TradeId("compensated"), participant(aId, a.ref()), participant(bId, b.ref()), aId,
                StakePercentage.ofPercent("10"), result.ref(), TIMEOUT, LEASE));

        replyAccepted(a.expectMessageClass(AgentActor.ReserveTrade.class));
        replyAccepted(b.expectMessageClass(AgentActor.ReserveTrade.class));
        a.expectMessageClass(AgentActor.GetState.class).replyTo().tell(new AgentState(aId, Money.of("100")));
        b.expectMessageClass(AgentActor.GetState.class).replyTo().tell(new AgentState(bId, Money.of("50")));
        b.expectMessageClass(AgentActor.DebitForTrade.class).replyTo()
                .tell(AgentActor.OperationResult.success());
        a.expectMessageClass(AgentActor.CreditForTrade.class).replyTo()
                .tell(AgentActor.OperationResult.rejected(AgentActor.OperationFailure.INVALID_AMOUNT));
        b.expectMessageClass(AgentActor.CreditForTrade.class).replyTo()
                .tell(AgentActor.OperationResult.success());

        TradeCoordinator.Rejected rejected = assertInstanceOf(
                TradeCoordinator.Rejected.class, result.receiveMessage());
        assertEquals(TradeCoordinator.FailureReason.CREDIT_REJECTED_COMPENSATED, rejected.reason());
        a.expectMessageClass(AgentActor.ReleaseTrade.class);
        b.expectMessageClass(AgentActor.ReleaseTrade.class);
    }

    @Test
    void rejectsSelfTradeWithoutMutatingActor() {
        AgentId id = new AgentId("self");
        ActorRef<AgentActor.Command> actor = testKit.spawn(AgentActor.create(id, Money.of("100")));
        TestProbe<TradeCoordinator.Outcome> result = testKit.createTestProbe();

        testKit.spawn(TradeCoordinator.create(
                new TradeId("self-trade"), participant(id, actor), participant(id, actor), id,
                StakePercentage.ofPercent("10"), result.ref(), TIMEOUT, LEASE));

        TradeCoordinator.Rejected rejected = assertInstanceOf(
                TradeCoordinator.Rejected.class, result.receiveMessage());
        assertEquals(TradeCoordinator.FailureReason.INVALID_PARTICIPANTS, rejected.reason());
        assertEquals(Money.of("100"), state(actor).wealth());
    }

    private void replyAccepted(AgentActor.ReserveTrade command) {
        command.replyTo().tell(new AgentActor.ReservationResult(true, Optional.of(command.tradeId())));
    }

    private TradeCoordinator.Participant participant(AgentId id, ActorRef<AgentActor.Command> actor) {
        return new TradeCoordinator.Participant(id, actor);
    }

    private AgentState state(ActorRef<AgentActor.Command> actor) {
        TestProbe<AgentState> probe = testKit.createTestProbe();
        actor.tell(new AgentActor.GetState(probe.ref()));
        return probe.receiveMessage();
    }

    private AgentActor.ReservationState reservation(ActorRef<AgentActor.Command> actor) {
        TestProbe<AgentActor.ReservationState> probe = testKit.createTestProbe();
        actor.tell(new AgentActor.GetReservation(probe.ref()));
        return probe.receiveMessage();
    }
}
