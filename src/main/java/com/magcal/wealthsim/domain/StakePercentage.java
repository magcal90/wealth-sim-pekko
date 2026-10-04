package com.magcal.wealthsim.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record StakePercentage(BigDecimal proportion) {
    private static final BigDecimal ONE = BigDecimal.ONE;

    public StakePercentage {
        Objects.requireNonNull(proportion, "proportion");
        if (proportion.signum() <= 0 || proportion.compareTo(ONE) > 0) {
            throw new IllegalArgumentException("Stake percentage must be greater than 0 and at most 1");
        }
        proportion = proportion.stripTrailingZeros();
    }

    public static StakePercentage ofPercent(String percent) {
        return new StakePercentage(new BigDecimal(percent).movePointLeft(2));
    }

    public Money applyTo(Money wealth) {
        return Money.of(wealth.amount().multiply(proportion));
    }
}
