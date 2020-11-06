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

import com.epam.pipeline.entity.pipeline.Tool;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class ToolCreatorUtils {

    private ToolCreatorUtils() {

    }

    public static Tool getTool() {
        final Tool tool = new Tool();
        tool.setId(ID);
        tool.setName(TEST_STRING);
        tool.setCpu(TEST_STRING);
        tool.setDefaultCommand(TEST_STRING);
        tool.setToolGroupId(ID);
        tool.setRegistry(TEST_STRING);
        tool.setRegistryId(ID);
        return tool;
    }

    public static Tool getTool(String owner) {
        final Tool tool = new Tool();
        tool.setOwner(owner);
        tool.setId(ID);
        tool.setCpu(TEST_STRING);
        tool.setDefaultCommand(TEST_STRING);
        tool.setToolGroupId(ID);
        tool.setRegistry(TEST_STRING);
        return tool;
    }
}
