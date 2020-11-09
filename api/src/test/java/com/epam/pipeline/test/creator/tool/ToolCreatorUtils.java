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
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.tool.ToolSymlinkRequest;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class ToolCreatorUtils {

    public static final TypeReference<Result<Tool>> TOOL_INSTANCE_TYPE =
            new TypeReference<Result<Tool>>() {};

    public static final TypeReference<Result<List<String>>> LIST_STRING_INSTANCE_TYPE =
            new TypeReference<Result<List<String>>>() {};

    private ToolCreatorUtils() {

    }

    public static Tool getTool() {
        final Tool tool = new Tool();
        tool.setId(ID);
        tool.setImage(TEST_STRING);
        tool.setCpu(TEST_STRING);
        tool.setRam(TEST_STRING);
        tool.setInstanceType(TEST_STRING);
        tool.setDisk(TEST_INT);
        return tool;
    }

    public static ToolSymlinkRequest getToolSymlinkRequest() {
        return new ToolSymlinkRequest(ID, ID);
    }
}
