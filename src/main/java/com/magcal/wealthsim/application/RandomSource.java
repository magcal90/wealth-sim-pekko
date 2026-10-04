package com.magcal.wealthsim.application;

public interface RandomSource {
    int nextInt(int bound);

    boolean nextBoolean();
}
