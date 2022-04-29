package com.epam.pipeline.entity.cloudaccess.policy;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public enum CloudAccessPolicyEffect {
    ALLOW("Allow"), DENY("Deny");

    private static final Map<String, CloudAccessPolicyEffect> EFFECT_MAP = Arrays.stream(
            CloudAccessPolicyEffect.values()).collect(Collectors.toMap(e -> e.awsValue, e -> e));

    public String awsValue;

    CloudAccessPolicyEffect(String awsValue) {
        this.awsValue = awsValue;
    }

    public static CloudAccessPolicyEffect from(String value) {
        return Optional.ofNullable(EFFECT_MAP.get(value))
                .orElseThrow(() -> new IllegalArgumentException("Unsupported effect: " + value));
    }
}
