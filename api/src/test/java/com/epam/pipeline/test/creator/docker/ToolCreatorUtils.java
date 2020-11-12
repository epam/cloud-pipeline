/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.test.creator.docker;

import com.epam.pipeline.entity.docker.ImageDescription;
import com.epam.pipeline.entity.docker.ImageHistoryLayer;
import com.epam.pipeline.entity.docker.ToolDescription;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.scan.ToolScanResult;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.tool.ToolSymlinkRequest;

import java.util.Collections;
import java.util.Map;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class ToolCreatorUtils {

    private ToolCreatorUtils() {

    }

    public static Tool getTool(final String owner) {
        final Tool tool = new Tool();
        tool.setOwner(owner);
        tool.setId(ID);
        tool.setCpu(TEST_STRING);
        tool.setDefaultCommand(TEST_STRING);
        tool.setToolGroupId(ID);
        tool.setRegistry(TEST_STRING);
        tool.setImage(TEST_STRING);
        return tool;
    }

    public static ToolVersionScanResult getToolVersionScanResult() {
        return new ToolVersionScanResult();
    }

    public static ImageDescription getImageDescription() {
        return new ImageDescription();
    }

    public static ImageHistoryLayer getImageHistoryLayer() {
        return new ImageHistoryLayer();
    }

    public static ToolScanResult getToolScanResult() {
        final ToolScanResult toolScanResult = new ToolScanResult();
        final ToolVersionScanResult toolVersionScanResult = new ToolVersionScanResult();
        toolVersionScanResult.setVersion(TEST_STRING);
        final Map<String, ToolVersionScanResult> map = Collections.singletonMap(TEST_STRING, toolVersionScanResult);
        toolScanResult.setToolId(ID);
        toolScanResult.setToolVersionScanResults(map);
        return toolScanResult;
    }

    public static ToolDescription getToolDescription() {
        return new ToolDescription();
    }

    public static ToolVersion getToolVersion() {
        return new ToolVersion();
    }

    public static ToolSymlinkRequest getToolSymlinkRequest() {
        return new ToolSymlinkRequest(ID, ID);
    }
}
