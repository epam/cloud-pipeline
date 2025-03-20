package com.epam.pipeline.manager.gcp;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GCPRegistryAction {
    INSERT("INSERT"),
    DELETE("DELETE");
    private final String action;
}
