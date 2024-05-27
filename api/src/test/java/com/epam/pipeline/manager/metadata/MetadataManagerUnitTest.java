/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.metadata;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.metadata.MetadataDao;
import com.epam.pipeline.entity.metadata.CommonCustomInstanceTagsTypes;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.google.common.collect.Maps;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MetadataManagerUnitTest {
    private static final String KEY_1 = "key1";
    private static final String KEY_2 = "key2";
    private static final String KEY_3 = "key3";
    private static final String VALUE_1 = "value1";
    private static final String TYPE = "string";
    private static final String TEST_USER = "TEST_USER";
    private static final String OWNER_KEY = "CP_OWNER";
    private static final String RUN_ID_KEY = "CP_RUN_ID";
    private static final String TOOL_KEY = "CP_TOOL";
    private static final String TEST_RUN_ID = "1";
    private static final String TEST_IMAGE = "test:8080/docker/image:latest";
    private static final long TEST_TOOL_ID = 1L;

    @Mock
    private ToolManager toolManager;
    @Mock
    private PreferenceManager preferenceManager;
    @Mock
    private MetadataDao metadataDao;

    @InjectMocks
    private MetadataManager metadataManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldPrepareTagsFromPreferenceAndToolsMetadata() {
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TAGS))
                .thenReturn(buildCommonTagsMapping());

        final PipelineRun run = new PipelineRun();
        run.setId(Long.valueOf(TEST_RUN_ID));
        run.setOwner(TEST_USER);
        run.setDockerImage(TEST_IMAGE);

        final Tool tool = new Tool();
        tool.setId(TEST_TOOL_ID);
        when(toolManager.loadByNameOrId(TEST_IMAGE)).thenReturn(tool);

        when(preferenceManager.findPreference(SystemPreferences.CLUSTER_INSTANCE_ALLOWED_CUSTOM_TAGS))
                .thenReturn(Optional.of(String.join(",", KEY_1, KEY_2)));

        final EntityVO entityVO = new EntityVO(TEST_TOOL_ID, AclClass.TOOL);
        when(metadataDao.loadMetadataItem(entityVO)).thenReturn(toolMetadata(entityVO));

        final Map<String, String> tags = metadataManager.prepareCustomInstanceTags(run);
        assertThat(tags)
                .hasSize(4)
                .contains(Maps.immutableEntry(KEY_1, VALUE_1))
                .contains(Maps.immutableEntry(OWNER_KEY, TEST_USER))
                .contains(Maps.immutableEntry(RUN_ID_KEY, TEST_RUN_ID))
                .contains(Maps.immutableEntry(TOOL_KEY, TEST_IMAGE));
    }

    @Test
    public void shouldPrepareTagsFromPreference() {
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TAGS))
                .thenReturn(buildCommonTagsMapping());

        final PipelineRun run = new PipelineRun();
        run.setId(Long.valueOf(TEST_RUN_ID));
        run.setOwner(TEST_USER);
        run.setDockerImage(TEST_IMAGE);

        when(preferenceManager.findPreference(SystemPreferences.CLUSTER_INSTANCE_ALLOWED_CUSTOM_TAGS))
                .thenReturn(Optional.empty());

        final Map<String, String> tags = metadataManager.prepareCustomInstanceTags(run);
        assertThat(tags)
                .hasSize(3)
                .contains(Maps.immutableEntry(OWNER_KEY, TEST_USER))
                .contains(Maps.immutableEntry(RUN_ID_KEY, TEST_RUN_ID))
                .contains(Maps.immutableEntry(TOOL_KEY, TEST_IMAGE));
        verify(metadataDao, never()).loadMetadataItem(any());
        verify(toolManager, never()).loadByNameOrId(any());
    }

    @Test
    public void shouldPrepareTagsFromToolsMetadata() {
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TAGS)).thenReturn(null);

        final PipelineRun run = new PipelineRun();
        run.setId(Long.valueOf(TEST_RUN_ID));
        run.setOwner(TEST_USER);
        run.setDockerImage(TEST_IMAGE);

        final Tool tool = new Tool();
        tool.setId(TEST_TOOL_ID);
        when(toolManager.loadByNameOrId(TEST_IMAGE)).thenReturn(tool);

        final EntityVO entityVO = new EntityVO(TEST_TOOL_ID, AclClass.TOOL);
        when(metadataDao.loadMetadataItem(entityVO)).thenReturn(toolMetadata(entityVO));

        when(preferenceManager.findPreference(SystemPreferences.CLUSTER_INSTANCE_ALLOWED_CUSTOM_TAGS))
                .thenReturn(Optional.of(String.join(",", KEY_1, KEY_2)));

        final Map<String, String> tags = metadataManager.prepareCustomInstanceTags(run);
        assertThat(tags)
                .hasSize(1)
                .contains(Maps.immutableEntry(KEY_1, VALUE_1));
    }

    @Test
    public void shouldReturnEmptyTagsIfToolsMetadataNotMatch() {
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TAGS)).thenReturn(null);

        final PipelineRun run = new PipelineRun();
        run.setId(Long.valueOf(TEST_RUN_ID));
        run.setOwner(TEST_USER);
        run.setDockerImage(TEST_IMAGE);

        final Tool tool = new Tool();
        tool.setId(TEST_TOOL_ID);
        when(toolManager.loadByNameOrId(TEST_IMAGE)).thenReturn(tool);

        final EntityVO entityVO = new EntityVO(TEST_TOOL_ID, AclClass.TOOL);
        when(metadataDao.loadMetadataItem(entityVO)).thenReturn(toolMetadata(entityVO));

        when(preferenceManager.findPreference(SystemPreferences.CLUSTER_INSTANCE_ALLOWED_CUSTOM_TAGS))
                .thenReturn(Optional.of(KEY_2));

        final Map<String, String> tags = metadataManager.prepareCustomInstanceTags(run);
        assertThat(tags).hasSize(0);
    }

    @Test
    public void shouldReturnEmptyTagsIfError() {
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TAGS))
                .thenReturn(buildCommonTagsMapping());

        final PipelineRun run = new PipelineRun();
        run.setId(Long.valueOf(TEST_RUN_ID));

        when(preferenceManager.findPreference(SystemPreferences.CLUSTER_INSTANCE_ALLOWED_CUSTOM_TAGS))
                .thenReturn(Optional.of(String.join(",", KEY_1, KEY_2)));

        final Map<String, String> tags = metadataManager.prepareCustomInstanceTags(run);
        assertThat(tags).hasSize(0);
    }

    @Test
    public void shouldReturnEmptyTagsIfNoToolMetadata() {
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TAGS)).thenReturn(null);

        final PipelineRun run = new PipelineRun();
        run.setId(Long.valueOf(TEST_RUN_ID));
        run.setDockerImage(TEST_IMAGE);

        final Tool tool = new Tool();
        tool.setId(TEST_TOOL_ID);
        when(toolManager.loadByNameOrId(TEST_IMAGE)).thenReturn(tool);

        when(preferenceManager.findPreference(SystemPreferences.CLUSTER_INSTANCE_ALLOWED_CUSTOM_TAGS))
                .thenReturn(Optional.of(String.join(",", KEY_1, KEY_2)));
        final EntityVO entityVO = new EntityVO(TEST_TOOL_ID, AclClass.TOOL);
        when(metadataDao.loadMetadataItem(entityVO)).thenReturn(null);

        final Map<String, String> tags = metadataManager.prepareCustomInstanceTags(run);
        assertThat(tags).hasSize(0);
        verify(toolManager).loadByNameOrId(TEST_IMAGE);
    }

    private static Map<CommonCustomInstanceTagsTypes, String> buildCommonTagsMapping() {
        final Map<CommonCustomInstanceTagsTypes, String> mapping = new HashMap<>();
        mapping.put(CommonCustomInstanceTagsTypes.owner, OWNER_KEY);
        mapping.put(CommonCustomInstanceTagsTypes.run_id, RUN_ID_KEY);
        mapping.put(CommonCustomInstanceTagsTypes.tool, TOOL_KEY);
        return mapping;
    }

    private static MetadataEntry toolMetadata(final EntityVO entityVO) {
        final Map<String, PipeConfValue> data = new HashMap<>();
        data.put(KEY_1, new PipeConfValue(TYPE, VALUE_1));
        data.put(KEY_3, new PipeConfValue(TYPE, VALUE_1));
        final MetadataEntry metadataEntry = new MetadataEntry();
        metadataEntry.setEntity(entityVO);
        metadataEntry.setData(data);
        return metadataEntry;
    }
}
