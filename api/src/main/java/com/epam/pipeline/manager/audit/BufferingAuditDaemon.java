package com.epam.pipeline.manager.audit;

import com.epam.pipeline.manager.audit.entity.DataAccessEntry;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class BufferingAuditDaemon implements AuditDaemon {

    private final AuditContainer container;
    private final AuditConsumer consumer;
    private final TimeUnit bufferTimeUnit;
    private final int bufferTime;
    private final int bufferSize;

    public BufferingAuditDaemon(final AuditContainer container, final AuditConsumer consumer,
                                final int bufferTime, final int bufferSize) {
        this(container, consumer, TimeUnit.SECONDS, bufferTime, bufferSize);
    }

    @Override
    public void start() {
        log.info("Initiating audit daemon...");
        entries()
                .subscribeOn(Schedulers.from(Executors.newSingleThreadExecutor()))
                .buffer(bufferTime, bufferTimeUnit, bufferSize)
                .filter(CollectionUtils::isNotEmpty)
                .forEach(consumer::consume);
    }

    private Flowable<DataAccessEntry> entries() {
        return Flowable.create(emitter -> {
            while (!emitter.isCancelled()) {
                try {
                    container.pull().forEach(emitter::onNext);
                } catch (Exception e) {
                    log.error("Audit entries pulling has failed.", e);
                }
            }
        }, BackpressureStrategy.BUFFER);
    }
}
