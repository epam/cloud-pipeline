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
public class DtsTransfer {
    private Long id;
    private Long dtsId;
    private DtsTransferStorageItem source;
    private DtsTransferStorageItem destination;
    private DtsTransferStatus status;
    private String reason;
    private LocalDateTime created;
    private LocalDateTime started;
    private LocalDateTime finished;
    private List<String> included;
    private String user;
}
