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

package com.epam.pipeline.acl.run;

import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

@RequiredArgsConstructor
public class ToolAclFactory {
    public static final Long TEST_TOOL_ID = 10L;
    public static final String TEST_TOOL_NAME = "image";

    private final AuthManager authManager;
    private final ToolManager mockToolManager;

    public Tool initToolForCurrentUser() {
        return initToolForOwner(authManager.getAuthorizedUser());
    }

    public Tool initToolForOwner(final String owner) {
        final Tool tool = new Tool();
        tool.setId(TEST_TOOL_ID);
        tool.setImage(TEST_TOOL_NAME);
        tool.setOwner(owner);
        doReturn(tool).when(mockToolManager).load(eq(TEST_TOOL_ID));
        doReturn(tool).when(mockToolManager).loadByNameOrId(eq(TEST_TOOL_NAME));
        return tool;
    }
}
