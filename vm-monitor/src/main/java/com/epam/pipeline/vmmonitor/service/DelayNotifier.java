package com.epam.pipeline.vmmonitor.service;

import com.epam.pipeline.entity.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class DelayNotifier<T> implements Notifier<T> {

    private final Notifier<T> inner;
    private final Duration delay;

    private LocalDateTime previous = null;

    @Override
    public synchronized void notify(final T event) {
        final LocalDateTime now = DateUtils.nowUTC();
        final Duration remainingDelay = resolveRemainingDelay(now);
        if (remainingDelay.isNegative() || remainingDelay.isZero()) {
            log.debug("Proceeding with notifications...");
            previous = now;
            inner.notify(event);
        } else {
            log.info("Aborting notifications for {} more minutes til {}...", Math.max(remainingDelay.toMinutes(), 1),
                    now.plus(remainingDelay));
        }
    }

    private Duration resolveRemainingDelay(final LocalDateTime now) {
        return Optional.ofNullable(previous)
                .map(it -> it.plus(delay))
                .map(next -> Duration.between(now, next))
                .orElse(Duration.ZERO);
    }
}
