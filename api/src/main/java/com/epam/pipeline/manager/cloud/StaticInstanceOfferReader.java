package com.epam.pipeline.manager.cloud;

import com.epam.pipeline.entity.cluster.InstanceOffer;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class StaticInstanceOfferReader implements InstanceOfferReader {

    private final List<InstanceOffer> offers;

    @Override
    public List<InstanceOffer> read() throws IOException {
        return offers;
    }

    @Override
    public void close() {
    }
}
