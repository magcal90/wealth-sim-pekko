package com.magcal.wealthsim;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.Test;

class WealthSimApplicationTest {
    @Test
    void rootBehaviorStartsInTypedActorSystem() {
        ActorTestKit testKit = ActorTestKit.create();

        try {
            ActorRef<Void> guardian = testKit.spawn(WealthSimApplication.rootBehavior());

            assertNotNull(guardian);
        } finally {
            testKit.shutdownTestKit();
        }
    }
}
