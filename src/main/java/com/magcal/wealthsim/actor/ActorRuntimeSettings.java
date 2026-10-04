package com.magcal.wealthsim.actor;

import java.time.Duration;

public final class ActorRuntimeSettings {
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration RESERVATION_LEASE = Duration.ofSeconds(10);

    private ActorRuntimeSettings() {
    }
}
