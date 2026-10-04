package com.magcal.wealthsim.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.magcal.wealthsim.domain.AgentId;

class TradeSelectorTest {
    @Test
    void selectsDistinctPopulationMembersAndParticipantWinner() {
        List<AgentId> population = identities(100);
        TradeSelection selection = new TradeSelector(new SeededRandomSource(12345)).next(population);

        assertNotEquals(selection.left(), selection.right());
        assertTrue(population.contains(selection.left()));
        assertTrue(population.contains(selection.right()));
        assertTrue(selection.winner().equals(selection.left()) || selection.winner().equals(selection.right()));
    }

    @Test
    void sameSeedReproducesSelectionSequence() {
        List<AgentId> population = identities(20);
        TradeSelector first = new TradeSelector(new SeededRandomSource(12345));
        TradeSelector second = new TradeSelector(new SeededRandomSource(12345));

        List<TradeSelection> firstSequence = IntStream.range(0, 100)
                .mapToObj(ignored -> first.next(population)).toList();
        List<TradeSelection> secondSequence = IntStream.range(0, 100)
                .mapToObj(ignored -> second.next(population)).toList();

        assertEquals(firstSequence, secondSequence);
    }

    @Test
    void rejectsPopulationWithFewerThanTwoAgents() {
        TradeSelector selector = new TradeSelector(new SeededRandomSource(1));

        assertThrows(IllegalArgumentException.class,
                () -> selector.next(List.of(new AgentId("only"))));
    }

    private List<AgentId> identities(int size) {
        return IntStream.range(0, size)
                .mapToObj(index -> new AgentId("agent-" + index))
                .toList();
    }
}
