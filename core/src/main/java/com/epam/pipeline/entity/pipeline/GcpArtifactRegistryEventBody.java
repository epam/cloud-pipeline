package com.epam.pipeline.entity.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GcpArtifactRegistryEventBody {
    private Message message;
    private String subscription;
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String data;
        private String messageId;
        private String publishTime;
    }
}
