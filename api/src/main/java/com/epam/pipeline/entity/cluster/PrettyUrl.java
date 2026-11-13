package com.epam.pipeline.entity.cluster;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PrettyUrl(@JsonProperty("path") String path,
                        @JsonProperty("domain") String domain) {
}
