package com.magcal.wealthsim.application;

import java.math.BigDecimal;
import java.util.Objects;

import com.magcal.wealthsim.domain.Money;

public record WealthDistributionMetrics(
        int populationSize,
        Money totalWealth,
        Money meanWealth,
        Money medianWealth,
        Money minimumWealth,
        Money maximumWealth,
        BigDecimal giniCoefficient,
        BigDecimal topOnePercentShare,
        BigDecimal topTenPercentShare,
        BigDecimal bottomFiftyPercentShare) {
    public WealthDistributionMetrics {
        Objects.requireNonNull(totalWealth, "totalWealth");
        Objects.requireNonNull(meanWealth, "meanWealth");
        Objects.requireNonNull(medianWealth, "medianWealth");
        Objects.requireNonNull(minimumWealth, "minimumWealth");
        Objects.requireNonNull(maximumWealth, "maximumWealth");
        Objects.requireNonNull(giniCoefficient, "giniCoefficient");
        Objects.requireNonNull(topOnePercentShare, "topOnePercentShare");
        Objects.requireNonNull(topTenPercentShare, "topTenPercentShare");
        Objects.requireNonNull(bottomFiftyPercentShare, "bottomFiftyPercentShare");
    }
}
