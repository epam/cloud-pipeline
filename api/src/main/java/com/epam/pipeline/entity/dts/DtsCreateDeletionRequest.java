package com.epam.pipeline.entity.dts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DtsCreateDeletionRequest {
    private DtsTaskStorageItem target;
    private LocalDateTime scheduled;
    private List<String> included;
}
