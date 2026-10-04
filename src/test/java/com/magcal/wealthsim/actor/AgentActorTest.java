package com.magcal.wealthsim.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class AgentActorTest {
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
    void supportsStateQueryCreditDebitAndExpectedRejections() {
        ActorRef<AgentActor.Command> agent = spawn("operations", "100");
        TestProbe<AgentActor.OperationResult> operations = testKit.createTestProbe();

        agent.tell(new AgentActor.Credit(Money.of("25"), operations.ref()));
        assertTrue(operations.receiveMessage().accepted());
        agent.tell(new AgentActor.Debit(Money.of("10"), operations.ref()));
        assertTrue(operations.receiveMessage().accepted());
        agent.tell(new AgentActor.Debit(Money.of("200"), operations.ref()));
        assertFalse(operations.receiveMessage().accepted());
        agent.tell(new AgentActor.Credit(Money.of("-1"), operations.ref()));
        assertEquals(AgentActor.OperationFailure.INVALID_AMOUNT,
                operations.receiveMessage().failure().orElseThrow());

        assertEquals(Money.of("115"), state(agent).wealth());
    }

    @Test
    void processesCommandsSeriallyAndAllowsFullBalanceDebit() {
        ActorRef<AgentActor.Command> agent = spawn("serial", "20");
        TestProbe<AgentActor.OperationResult> operations = testKit.createTestProbe();

        agent.tell(new AgentActor.Credit(Money.of("10"), operations.ref()));
        agent.tell(new AgentActor.Debit(Money.of("20"), operations.ref()));
        agent.tell(new AgentActor.Debit(Money.of("10"), operations.ref()));
        operations.receiveMessage();
        operations.receiveMessage();
        operations.receiveMessage();

        assertEquals(Money.ZERO, state(agent).wealth());
    }

    @Test
    void reservationIsOwnedIdempotentAndGatesTradeMutation() {
        ActorRef<AgentActor.Command> agent = spawn("reserved", "100");
        TestProbe<AgentActor.ReservationResult> reservations = testKit.createTestProbe();
        TestProbe<AgentActor.ReleaseResult> releases = testKit.createTestProbe();
        TestProbe<AgentActor.OperationResult> operations = testKit.createTestProbe();
        TradeId first = new TradeId("T1");
        TradeId second = new TradeId("T2");

        agent.tell(new AgentActor.ReserveTrade(first, Duration.ofSeconds(2), reservations.ref()));
        assertTrue(reservations.receiveMessage().accepted());
        agent.tell(new AgentActor.ReserveTrade(first, Duration.ofSeconds(2), reservations.ref()));
        assertTrue(reservations.receiveMessage().accepted());
        agent.tell(new AgentActor.ReserveTrade(second, Duration.ofSeconds(2), reservations.ref()));
        assertFalse(reservations.receiveMessage().accepted());

        agent.tell(new AgentActor.DebitForTrade(second, Money.of("10"), operations.ref()));
        assertEquals(AgentActor.OperationFailure.WRONG_TRADE,
                operations.receiveMessage().failure().orElseThrow());
        agent.tell(new AgentActor.DebitForTrade(first, Money.of("10"), operations.ref()));
        assertTrue(operations.receiveMessage().accepted());
        agent.tell(new AgentActor.ReleaseTrade(second, releases.ref()));
        assertFalse(releases.receiveMessage().accepted());
        agent.tell(new AgentActor.ReleaseTrade(first, releases.ref()));
        assertTrue(releases.receiveMessage().accepted());

        assertEquals(Money.of("90"), state(agent).wealth());
        assertEquals(Optional.empty(), reservation(agent).owner());
    }

    @Test
    void abandonedReservationExpiresWithoutChangingWealth() {
        ActorRef<AgentActor.Command> agent = spawn("expiry", "100");
        TestProbe<AgentActor.ReservationResult> reservations = testKit.createTestProbe();
        TradeId tradeId = new TradeId("expires");

        agent.tell(new AgentActor.ReserveTrade(tradeId, Duration.ofMillis(100), reservations.ref()));
        assertTrue(reservations.receiveMessage().accepted());

        TestProbe<AgentActor.ReservationState> probe = testKit.createTestProbe();
        probe.awaitAssert(Duration.ofSeconds(2), () -> {
            agent.tell(new AgentActor.GetReservation(probe.ref()));
            assertEquals(Optional.empty(), probe.receiveMessage().owner());
            return null;
        });
        assertEquals(Money.of("100"), state(agent).wealth());
    }

    private ActorRef<AgentActor.Command> spawn(String suffix, String wealth) {
        return testKit.spawn(AgentActor.create(new AgentId("agent-" + suffix), Money.of(wealth)));
    }

    private AgentState state(ActorRef<AgentActor.Command> agent) {
        TestProbe<AgentState> probe = testKit.createTestProbe();
        agent.tell(new AgentActor.GetState(probe.ref()));
        return probe.receiveMessage();
    }

    private AgentActor.ReservationState reservation(ActorRef<AgentActor.Command> agent) {
        TestProbe<AgentActor.ReservationState> probe = testKit.createTestProbe();
        agent.tell(new AgentActor.GetReservation(probe.ref()));
        return probe.receiveMessage();
    }
}
