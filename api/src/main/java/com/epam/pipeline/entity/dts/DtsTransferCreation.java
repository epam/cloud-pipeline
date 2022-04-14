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
public class DtsTransferCreation {
    private DtsTransferStorageItem source;
    private DtsTransferStorageItem destination;
    private List<String> included;
}
