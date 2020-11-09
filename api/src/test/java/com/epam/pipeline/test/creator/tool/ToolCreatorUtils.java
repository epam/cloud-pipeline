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

package com.epam.pipeline.test.creator.tool;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolGroupWithIssues;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

public final class ToolCreatorUtils {

    public static final TypeReference<Result<ToolGroup>> TOOL_GROUP_TYPE =
            new TypeReference<Result<ToolGroup>>() {};
    public static final TypeReference<Result<ToolGroupWithIssues>> TOOL_GROUP_WITH_ISSUES_TYPE =
            new TypeReference<Result<ToolGroupWithIssues>>() {};
    public static final TypeReference<Result<List<ToolGroup>>> TOOL_GROUP_LIST_TYPE =
            new TypeReference<Result<List<ToolGroup>>>() {};

    private ToolCreatorUtils() {

    }

    public static ToolGroup getToolGroup() {
        return new ToolGroup();
    }

    public static ToolGroupWithIssues getToolGroupWithIssues() {
        return new ToolGroupWithIssues();
    }
}
