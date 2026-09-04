package com.epam.pipeline.entity.cluster;

import java.time.LocalDateTime;

public record NodeDisk(Long size, String nodeId, LocalDateTime createdDate) {
}
