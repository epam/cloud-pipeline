/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.dao.tool.ToolVulnerabilityDao;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class ToolScanInfoManagerTest {

    private static final String IMAGE = "registry/library/tool";
    private static final String DEFAULT_VERSION = "1.0";
    private static final Long TOOL_ID = 1L;

    @Mock
    private ToolManager toolManager;

    @Mock
    @SuppressWarnings("PMD.UnusedPrivateField")
    private ToolVersionManager toolVersionManager;

    @Mock
    private ToolVulnerabilityDao toolVulnerabilityDao;

    @InjectMocks
    private final ToolScanInfoManager toolScanInfoManager = new ToolScanInfoManager();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldLoadScanOfVersionResolvedForToolIfImageHasNoTag() {
        final Tool tool = new Tool();
        tool.setId(TOOL_ID);
        final ToolVersionScanResult scan = new ToolVersionScanResult(DEFAULT_VERSION);
        scan.setStatus(ToolScanStatus.NOT_SCANNED);
        doReturn(tool).when(toolManager).loadByNameOrId(IMAGE);
        doReturn(DEFAULT_VERSION).when(toolManager).getTagFromImageName(tool, IMAGE);
        doReturn(new HashMap<>(Collections.singletonMap(DEFAULT_VERSION, scan))).when(toolVulnerabilityDao)
                .loadToolVersionScanInfo(TOOL_ID, Collections.singletonList(DEFAULT_VERSION));

        final Optional<ToolVersionScanResult> result = toolScanInfoManager.loadToolVersionScanInfoByImageName(IMAGE);

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo(scan);
    }
}
