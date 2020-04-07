/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.docker.scan;

import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.scan.ToolExecutionCheckStatus;
import com.epam.pipeline.entity.scan.ToolScanPolicy;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.exception.ToolScanExternalServiceException;

/**
 * An interface for various managers, that allows scanning Tools with image verification software
 */
public interface ToolScanManager {

    /**
     * Scan a tool to get it's vulnerabilities
     * @param toolId an ID of a tool
     * @param tag a tag to analyze
     * @return a list of tool's vulnerabilities
     */
    ToolVersionScanResult scanTool(Long toolId, String tag, Boolean rescan) throws ToolScanExternalServiceException;

    /**
     * Scan a tool to get it's vulnerabilities
     * @param tool a Tool to scan
     * @param tag a tag to analyze
     * @param rescan
     * @return a list of tool's vulnerabilities
     */
    ToolVersionScanResult scanTool(Tool tool, String tag, Boolean rescan) throws ToolScanExternalServiceException;

    ToolExecutionCheckStatus checkTool(Tool tool, String tag);

    /**
     * Loads current security policy.
     * @return a {@link ToolScanPolicy}
     */
    ToolScanPolicy getPolicy();

}
