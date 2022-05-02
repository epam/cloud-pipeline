package com.epam.pipeline.entity.cloudaccess.policy.aws;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AWSAccessPolicyDocument {
    @JsonProperty("Version")
    String version;
    @JsonProperty("Statement")
    List<AWSAccessPolicyStatement> statements;
}
