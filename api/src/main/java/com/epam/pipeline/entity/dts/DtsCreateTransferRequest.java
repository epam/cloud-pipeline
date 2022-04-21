package com.epam.pipeline.entity.dts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DtsCreateTransferRequest {
    private DtsTaskStorageItem source;
    private DtsTaskStorageItem destination;
    private List<String> included;
}
