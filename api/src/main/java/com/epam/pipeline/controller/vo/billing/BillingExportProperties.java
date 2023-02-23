package com.epam.pipeline.controller.vo.billing;

import lombok.Value;

import java.util.List;

@Value
public class BillingExportProperties {

    //Storage specific parameters
    boolean includeStorageOldVersions;
    List<String> includeStorageClasses;
}
