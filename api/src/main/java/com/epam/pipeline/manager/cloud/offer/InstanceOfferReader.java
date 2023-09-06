package com.epam.pipeline.manager.cloud.offer;

import com.epam.pipeline.entity.cluster.InstanceOffer;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public interface InstanceOfferReader extends Closeable {

    List<InstanceOffer> read() throws IOException;
}
