package com.epam.pipeline.test.creator.scan;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.scan.ToolScanPolicy;
import com.epam.pipeline.entity.scan.ToolScanResultView;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.scan.ToolVersionScanResultView;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class ScanCreatorUtils {

    public static final TypeReference<Result<ToolVersionScanResult>> TOOL_VERSION_SCAN_INSTANCE_TYPE =
            new TypeReference<Result<ToolVersionScanResult>>() {};
    public static final TypeReference<Result<ToolScanResultView>> SCAN_RESULT_VIEW_INSTANCE_TYPE =
            new TypeReference<Result<ToolScanResultView>>() {};
    public static final TypeReference<Result<ToolScanPolicy>> TOOL_SCAN_POLICY_INSTANCE_TYPE =
            new TypeReference<Result<ToolScanPolicy>>() {};

    private ScanCreatorUtils() {

    }

    public static ToolVersionScanResult getToolVersionScanResult() {
        final ToolVersionScanResult toolVersionScanResult = new ToolVersionScanResult();
        toolVersionScanResult.setToolId(ID);
        toolVersionScanResult.setVersion(TEST_STRING);
        toolVersionScanResult.setFromWhiteList(true);
        return toolVersionScanResult;
    }

    public static ToolScanResultView getToolScanResultView() {
        return new ToolScanResultView(ID, Collections.singletonMap(TEST_STRING, getToolVersionScanResultView()));
    }

    public static ToolVersionScanResultView getToolVersionScanResultView() {
        final ToolVersionScanResultView scanResultView = ToolVersionScanResultView.builder().build();
        return scanResultView;
    }

    public static ToolScanPolicy getToolScanPolicy() {
        return new ToolScanPolicy();
    }
}
