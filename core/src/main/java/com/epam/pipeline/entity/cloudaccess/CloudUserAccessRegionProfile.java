package com.epam.pipeline.entity.cloudaccess;

import com.epam.pipeline.entity.cloudaccess.key.CloudUserAccessKeys;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicy;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CloudUserAccessRegionProfile {
    Long regionId;
    CloudUserAccessKeys keys;
    CloudAccessPolicy policy;
}
