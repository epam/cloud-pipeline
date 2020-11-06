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

import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolGroupWithIssues;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;

public final class ToolGroupCreatorUtils {

    private ToolGroupCreatorUtils() {

    }

    public static ToolGroup getToolGroup() {
        final ToolGroup toolGroup = new ToolGroup();
        toolGroup.setId(ID);
        toolGroup.setRegistryId(ID);
        return toolGroup;
    }

    public static ToolGroup getToolGroup(Long id, String owner) {
        final ToolGroup toolGroup = new ToolGroup();
        toolGroup.setId(id);
        toolGroup.setRegistryId(id);
        toolGroup.setOwner(owner);
        return toolGroup;
    }

    public static ToolGroupWithIssues getToolGroupWithIssues() {
        return new ToolGroupWithIssues();
    }
}
