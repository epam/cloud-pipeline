package com.epam.pipeline.billingreportagent.service.impl.synchronizer.merging;

import com.epam.pipeline.billingreportagent.service.ElasticsearchMergingSynchronizer;
import com.epam.pipeline.billingreportagent.service.ElasticsearchMergingFrame;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@RequiredArgsConstructor
public class InitialMergingSynchronizer implements ElasticsearchMergingSynchronizer {

    private final ElasticsearchMergingSynchronizer synchronizer;

    @Override
    public String name() {
        return synchronizer.name();
    }

    @Override
    public ElasticsearchMergingFrame frame() {
        return synchronizer.frame();
    }

    @Override
    public void synchronize(final LocalDateTime from, final LocalDateTime to) {
        synchronizer.synchronize(null, to);
    }
}
