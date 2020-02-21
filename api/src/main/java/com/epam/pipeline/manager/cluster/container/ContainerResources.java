package com.epam.pipeline.manager.cluster.container;

import com.epam.pipeline.utils.CommonUtils;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

@Data
@Builder
public class ContainerResources {

    private final Map<String, Quantity> limits;
    private final Map<String, Quantity> requests;

    public ResourceRequirements toContainerRequirements() {
        return new ResourceRequirements(limits, requests);
    }

    public static ContainerResources empty() {
        return ContainerResources.builder()
                .limits(Collections.emptyMap())
                .requests(Collections.emptyMap())
                .build();
    }

    public static ContainerResources merge(final ContainerResources first,
                                           final ContainerResources second) {
        return ContainerResources
                .builder()
                .limits(CommonUtils.mergeMaps(first.getLimits(), second.getLimits()))
                .requests(CommonUtils.mergeMaps(first.getRequests(), second.getRequests()))
                .build();
    }
}
