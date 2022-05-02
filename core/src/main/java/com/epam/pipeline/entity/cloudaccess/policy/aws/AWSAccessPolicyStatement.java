package com.epam.pipeline.entity.cloudaccess.policy.aws;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Value
@Builder
public class AWSAccessPolicyStatement {

    @JsonProperty("Effect")
    String effect;

    @JsonProperty("Action")
    Set<String> actions;

    @JsonProperty("Resource")
    String resource;
}
