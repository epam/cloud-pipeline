package com.epam.pipeline.entity.cloudaccess;

import com.epam.pipeline.entity.user.PipelineUser;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class CloudUserAccessProfile {
    PipelineUser user;
    Map<Long, CloudUserAccessRegionProfile> profiles;
}
