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

package com.epam.pipeline.manager.user;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.PipelineUserEvent;
import com.epam.pipeline.entity.user.PipelineUserWithStoragePath;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.metadata.CategoricalAttributeManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getPipelineUser;
import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getUserWithMetadata;
import static com.epam.pipeline.util.CustomAssertions.notInvoked;
import static com.epam.pipeline.util.CustomMatchers.anyLongList;
import static com.epam.pipeline.util.CustomMatchers.anyStringList;
import static com.epam.pipeline.util.CustomMatchers.anyStringMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserImportManagerTest {
    private static final String USER_NAME = "user";
    private static final String ROLE_NAME = "role";
    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final String LINKED_KEY = "key1";
    private static final String LINKED_VALUE = "value1";
    private static final String LINKED_KEY_2 = "key2";
    private static final String LINKED_VALUE_2 = "value2";

    private final UserManager userManager = mock(UserManager.class);
    private final CategoricalAttributeManager categoricalAttributeManager = mock(CategoricalAttributeManager.class);
    private final MetadataManager metadataManager = mock(MetadataManager.class);
    private final RoleManager roleManager = mock(RoleManager.class);
    private final UserImportManager userImportManager = new UserImportManager(userManager,
            categoricalAttributeManager, metadataManager, roleManager);

    @Test
    public void shouldCreateUserAndRolesAndMetadataIfAllowed() {
        final PipelineUser pipelineUser = getPipelineUser(USER_NAME);
        final Role role = new Role(ROLE_NAME);
        pipelineUser.setRoles(Collections.singletonList(role));
        final PipelineUserWithStoragePath userWithMetadata = getUserWithMetadata(pipelineUser, buildMetadata());
        final CategoricalAttribute categoricalAttribute = getCategoricalAttribute(KEY, VALUE, null);

        when(userManager.createUser(anyString(), anyLongList(), anyStringList(), anyStringMap(), anyLong()))
                .thenReturn(getPipelineUser(USER_NAME));
        when(roleManager.findRoleByName(ROLE_NAME)).thenReturn(Optional.empty());
        when(roleManager.createRole(anyString(), anyBoolean(), anyBoolean(), anyLong())).thenReturn(role);

        final List<PipelineUserEvent> resultEvents = userImportManager
                .processUser(userWithMetadata, true, true,
                        Collections.singletonList(categoricalAttribute));

        verify(userManager).createUser(anyString(), anyLongList(), anyStringList(), anyStringMap(), anyLong());
        verify(roleManager).createRole(anyString(), anyBoolean(), anyBoolean(), anyLong());
        verify(roleManager).assignRole(anyLong(), anyLongList());
        verify(metadataManager).updateEntityMetadata(any(), any(), any());
        assertThat(resultEvents).hasSize(4);
    }

    @Test
    public void shouldNotCreateUserRoleAndMetadataIfExists() {
        final PipelineUser pipelineUser = getPipelineUser(USER_NAME);
        final Role role = new Role(ROLE_NAME);
        pipelineUser.setRoles(Collections.singletonList(role));
        final PipelineUserWithStoragePath userWithMetadata = getUserWithMetadata(pipelineUser, buildMetadata());
        final CategoricalAttribute categoricalAttribute = getCategoricalAttribute(KEY, VALUE, null);

        when(userManager.loadUserByName(USER_NAME)).thenReturn(pipelineUser);
        when(roleManager.findRoleByName(ROLE_NAME)).thenReturn(Optional.of(role));
        when(metadataManager.loadMetadataItem(pipelineUser.getId(), AclClass.PIPELINE_USER))
                .thenReturn(userMetadata(pipelineUser.getId(), buildMetadata()));

        final List<PipelineUserEvent> resultEvents = userImportManager
                .processUser(userWithMetadata, true, true,
                        Collections.singletonList(categoricalAttribute));

        notInvoked(userManager).createUser(anyString(), anyLongList(), anyStringList(), anyStringMap(), anyLong());
        notInvoked(roleManager).createRole(anyString(), anyBoolean(), anyBoolean(), anyLong());
        notInvoked(roleManager).assignRole(anyLong(), anyLongList());
        verify(metadataManager).updateEntityMetadata(any(), any(), any());
        assertThat(CollectionUtils.isEmpty(resultEvents)).isTrue();
    }

    @Test
    public void shouldNotCreateRoleIfNotAllowed() {
        final PipelineUser pipelineUser = getPipelineUser(USER_NAME);
        final Role role = new Role(ROLE_NAME);
        pipelineUser.setRoles(Collections.singletonList(role));
        final PipelineUserWithStoragePath userWithMetadata = getUserWithMetadata(pipelineUser, buildMetadata());

        when(userManager.loadUserByName(USER_NAME)).thenReturn(getPipelineUser(USER_NAME));
        when(roleManager.findRoleByName(ROLE_NAME)).thenReturn(Optional.empty());

        final List<PipelineUserEvent> resultEvents = userImportManager
                .processUser(userWithMetadata, false, false, Collections.emptyList());

        notInvoked(roleManager).createRole(anyString(), anyBoolean(), anyBoolean(), anyLong());
        notInvoked(roleManager).assignRole(anyLong(), anyLongList());
        assertThat(resultEvents).hasSize(1);
    }

    @Test
    public void shouldNotCreateUserIfNotAllowed() {
        final PipelineUser pipelineUser = getPipelineUser(USER_NAME);
        final PipelineUserWithStoragePath userWithMetadata = getUserWithMetadata(pipelineUser, buildMetadata());

        when(userManager.loadUserByName(USER_NAME)).thenReturn(null);

        final List<PipelineUserEvent> resultEvents = userImportManager
                .processUser(userWithMetadata, false, true, Collections.emptyList());

        notInvoked(userManager).createUser(anyString(), anyLongList(), anyStringList(), anyStringMap(), anyLong());
        assertThat(resultEvents).hasSize(1);
    }

    @Test
    public void shouldNotUpdateMetadataIfNotAllowed() {
        final PipelineUser pipelineUser = getPipelineUser(USER_NAME);
        final Map<String, PipeConfValue> metadata = buildMetadata();
        metadata.put(LINKED_KEY, new PipeConfValue(null, LINKED_VALUE));
        metadata.put(LINKED_KEY_2, new PipeConfValue(null, LINKED_VALUE_2));
        final PipelineUserWithStoragePath userWithMetadata = getUserWithMetadata(pipelineUser, metadata);

        when(userManager.loadUserByName(USER_NAME)).thenReturn(pipelineUser);
        when(metadataManager.loadMetadataItem(pipelineUser.getId(), AclClass.PIPELINE_USER))
                .thenReturn(userMetadata(pipelineUser.getId(), buildMetadata()));

        final List<PipelineUserEvent> resultEvents = userImportManager
                .processUser(userWithMetadata, true, true, Collections.emptyList());

        final ArgumentCaptor<Map<String, PipeConfValue>> dataCaptor = getCaptor();
        verify(metadataManager).updateEntityMetadata(dataCaptor.capture(), any(), any());
        final Map<String, PipeConfValue> capturedData = dataCaptor.getValue();
        assertThat(capturedData)
                .hasSize(1)
                .containsKey(KEY);
        assertThat(capturedData.get(KEY).getValue()).isEqualTo(VALUE);

        assertThat(CollectionUtils.isEmpty(resultEvents)).isTrue();
    }

    @Test
    public void shouldUpdateLinkedMetadata() {
        final PipelineUser pipelineUser = getPipelineUser(USER_NAME);
        final PipelineUserWithStoragePath userWithMetadata = getUserWithMetadata(pipelineUser, buildMetadata());

        final CategoricalAttribute attribute1 = getCategoricalAttribute(KEY, VALUE,
                Collections.singletonList(getCategoricalAttributeValue(LINKED_KEY, LINKED_VALUE)));
        final CategoricalAttribute attribute2 = getCategoricalAttribute(LINKED_KEY, LINKED_VALUE,
                Collections.singletonList(getCategoricalAttributeValue(LINKED_KEY_2, LINKED_VALUE_2)));
        final CategoricalAttribute attribute3 = getCategoricalAttribute(LINKED_KEY_2, LINKED_VALUE_2, null);

        when(userManager.loadUserByName(USER_NAME)).thenReturn(pipelineUser);

        final List<PipelineUserEvent> resultEvents = userImportManager
                .processUser(userWithMetadata, true, true,
                        Arrays.asList(attribute1, attribute2, attribute3));

        final ArgumentCaptor<Map<String, PipeConfValue>> dataCaptor = getCaptor();
        verify(metadataManager).updateEntityMetadata(dataCaptor.capture(), any(), any());
        final Map<String, PipeConfValue> capturedData = dataCaptor.getValue();
        assertThat(capturedData)
                .hasSize(3)
                .containsKeys(KEY, LINKED_KEY, LINKED_KEY_2);
        assertThat(capturedData.get(KEY).getValue()).isEqualTo(VALUE);
        assertThat(capturedData.get(LINKED_KEY).getValue()).isEqualTo(LINKED_VALUE);
        assertThat(capturedData.get(LINKED_KEY_2).getValue()).isEqualTo(LINKED_VALUE_2);

        assertThat(resultEvents).hasSize(3);
    }

    private Map<String, PipeConfValue> buildMetadata() {
        final Map<String, PipeConfValue> metadata = new HashMap<>();
        metadata.put(KEY, new PipeConfValue(null, VALUE));
        return metadata;
    }

    private CategoricalAttribute getCategoricalAttribute(final String key, final String value,
                                                         final List<CategoricalAttributeValue> links) {
        final CategoricalAttributeValue attributeValue = getCategoricalAttributeValue(key, value);
        attributeValue.setLinks(links);
        return new CategoricalAttribute(key, Collections.singletonList(attributeValue));
    }

    private CategoricalAttributeValue getCategoricalAttributeValue(final String key, final String value) {
        final CategoricalAttributeValue attributeValue = new CategoricalAttributeValue();
        attributeValue.setKey(key);
        attributeValue.setValue(value);
        return attributeValue;
    }

    private MetadataEntry userMetadata(final Long userId, final Map<String, PipeConfValue> data) {
        final MetadataEntry metadataEntry = new MetadataEntry();
        metadataEntry.setEntity(new EntityVO(userId, AclClass.PIPELINE_USER));
        metadataEntry.setData(data);
        return metadataEntry;
    }

    private ArgumentCaptor<Map<String, PipeConfValue>> getCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }
}
