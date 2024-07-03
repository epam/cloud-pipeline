package com.epam.pipeline.elasticsearchagent.service.lock;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LockService {

    private final LockProvider lockProvider;

    @Value("${sync.index.lock.attempts:60}")
    private Integer lockAttempts;

    @Value("${sync.index.lock.timeout:5000}")
    private Integer lockTimeout;

    public Optional<SimpleLock> getLock(final Long storageId) {
        return lockProvider.lock(
                new LockConfiguration("storage-index-" + storageId,
                        LocalDateTime.now().plusMinutes(5L).toInstant(ZoneOffset.UTC)));
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void runWithLock(final Long storageId, final Runnable call) {
        SimpleLock lock = null;
        try {
            lock = obtainLock(storageId);
            call.run();
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            throw e;
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }

    @SneakyThrows
    public SimpleLock obtainLock(final Long storageId) {
        int attempts = lockAttempts;
        while (attempts > 0) {
            final Optional<SimpleLock> lock = getLock(storageId);
            if (lock.isPresent()) {
                return lock.get();
            }
            attempts--;
            Thread.sleep(lockAttempts);
        }
        throw new IllegalArgumentException(String.format("Failed to obtain lock in %d attempts", lockTimeout));
    }
}
