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

import com.epam.pipeline.controller.Result;
<<<<<<< HEAD
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolGroupWithIssues;
=======
import com.epam.pipeline.entity.docker.ImageDescription;
import com.epam.pipeline.entity.docker.ImageHistoryLayer;
import com.epam.pipeline.entity.docker.ToolDescription;
import com.epam.pipeline.entity.docker.ToolVersion;
>>>>>>> Issue #1405: Implemented tests for Tool controller layer
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

public final class DockerCreatorUtils {

<<<<<<< HEAD
    public static final TypeReference<Result<ToolGroup>> TOOL_GROUP_TYPE =
            new TypeReference<Result<ToolGroup>>() {};
    public static final TypeReference<Result<ToolGroupWithIssues>> TOOL_GROUP_WITH_ISSUES_TYPE =
            new TypeReference<Result<ToolGroupWithIssues>>() {};
    public static final TypeReference<Result<List<ToolGroup>>> TOOL_GROUP_LIST_TYPE =
            new TypeReference<Result<List<ToolGroup>>>() {};
=======
    public static final TypeReference<Result<ImageDescription>> IMAGE_DESCRIPTION_INSTANCE_TYPE =
            new TypeReference<Result<ImageDescription>>() {};
    public static final TypeReference<Result<ToolDescription>> TOOL_DESCRIPTION_INSTANCE_TYPE =
            new TypeReference<Result<ToolDescription>>() {};
    public static final TypeReference<Result<ToolVersion>> TOOL_VERSION_INSTANCE_TYPE =
            new TypeReference<Result<ToolVersion>>() {};
    public static final TypeReference<Result<List<ImageHistoryLayer>>> IMAGE_HISTORY_LIST_INSTANCE_TYPE =
            new TypeReference<Result<List<ImageHistoryLayer>>>() {};
    public static final TypeReference<Result<List<ToolVersion>>> TOOL_VERSION_LIST_INSTANCE_TYPE =
            new TypeReference<Result<List<ToolVersion>>>() {};
>>>>>>> Issue #1405: Implemented tests for Tool controller layer

    private DockerCreatorUtils() {

    }

<<<<<<< HEAD
    public static ToolGroup getToolGroup() {
        return new ToolGroup();
    }

    public static ToolGroupWithIssues getToolGroupWithIssues() {
        return new ToolGroupWithIssues();
=======
    public static ImageDescription getImageDescription() {
        return new ImageDescription();
    }

    public static ImageHistoryLayer getImageHistoryLayer() {
        return new ImageHistoryLayer();
    }

    public static ToolDescription getToolDescription() {
        return new ToolDescription();
    }

    public static ToolVersion getToolVersion() {
        return new ToolVersion();
>>>>>>> Issue #1405: Implemented tests for Tool controller layer
    }
}
