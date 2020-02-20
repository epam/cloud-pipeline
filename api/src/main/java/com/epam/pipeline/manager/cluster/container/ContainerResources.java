package com.epam.pipeline.manager.cluster.container;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ContainerResources {

    private final Map<String, Quantity> limits;
    private final Map<String, Quantity> requests;

    public ResourceRequirements toContainerRequirements() {
        return new ResourceRequirements(limits, requests);
    }
}
