package com.epam.pipeline.manager.cloud.offer;

import com.epam.pipeline.entity.cluster.InstanceOffer;

import java.util.List;

public interface InstanceOfferFilter {

    List<InstanceOffer> filter(List<InstanceOffer> offers);
}
