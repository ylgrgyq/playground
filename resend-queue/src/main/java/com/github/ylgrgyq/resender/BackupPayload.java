package com.github.ylgrgyq.resender;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public interface BackupPayload extends Delayed {

    int increaseFailedTimes();

    int getFailedTimes();

    boolean isExpired();

    default int compareTo(Delayed o) {
        if (o == null) return -1;
        if (o == this) return 0;

        return (int)(getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS));
    }

    default boolean noNeedToResend() {
        return isExpired() || isFailedTooManyTimes();
    }

    default boolean isFailedTooManyTimes() {
        return getFailedTimes() > 10;
    }
}
