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

package com.epam.pipeline.acl.metadata;

import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.manager.metadata.CategoricalAttributeManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.metadata.MetadataCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class CategoricalAttributeApiServiceTest extends AbstractAclTest {

    private final CategoricalAttribute attribute = MetadataCreatorUtils.getCategoricalAttribute();
    private final List<CategoricalAttribute> attributeList = Collections.singletonList(attribute);

    @Autowired
    private CategoricalAttributeApiService attributeApiService;

    @Autowired
    private CategoricalAttributeManager mockAttributeManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateAttributesForAdmin() {
        doReturn(true).when(mockAttributeManager).updateCategoricalAttributes(attributeList);

        assertThat(attributeApiService.updateCategoricalAttributes(attributeList)).isEqualTo(true);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateAttributesForNotAdmin() {
        doReturn(true).when(mockAttributeManager).updateCategoricalAttributes(attributeList);

        assertThrows(AccessDeniedException.class, () -> attributeApiService.updateCategoricalAttributes(attributeList));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllAttributesForAdmin() {
        doReturn(attributeList).when(mockAttributeManager).loadAll();

        assertThat(attributeApiService.loadAll()).isEqualTo(attributeList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadAllAttributesForNotAdmin() {
        doReturn(attributeList).when(mockAttributeManager).loadAll();

        assertThrows(AccessDeniedException.class, () -> attributeApiService.loadAll());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllValuesForKeyForAdmin() {
        doReturn(attribute).when(mockAttributeManager).loadAllValuesForKey(TEST_STRING);

        assertThat(attributeApiService.loadAllValuesForKey(TEST_STRING)).isEqualTo(attribute);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadAllValuesForKeyForNotAdmin() {
        doReturn(attribute).when(mockAttributeManager).loadAllValuesForKey(TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> attributeApiService.loadAllValuesForKey(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteAttributeValuesForAdmin() {
        doReturn(true).when(mockAttributeManager).deleteAttributeValues(TEST_STRING);

        assertThat(attributeApiService.deleteAttributeValues(TEST_STRING)).isEqualTo(true);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteAttributeValuesForNotAdmin() {
        doReturn(true).when(mockAttributeManager).deleteAttributeValues(TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> attributeApiService.deleteAttributeValues(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteAttributeValueForAdmin() {
        doReturn(true).when(mockAttributeManager).deleteAttributeValue(TEST_STRING, TEST_STRING);

        assertThat(attributeApiService.deleteAttributeValue(TEST_STRING, TEST_STRING)).isEqualTo(true);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteAttributeValueForNotAdmin() {
        doReturn(true).when(mockAttributeManager).deleteAttributeValue(TEST_STRING, TEST_STRING);

        assertThrows(AccessDeniedException.class, () ->
                attributeApiService.deleteAttributeValue(TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldSyncWithMetadataForAdmin() {
        doNothing().when(mockAttributeManager).syncWithMetadata();

        attributeApiService.syncWithMetadata();

        verify(mockAttributeManager).syncWithMetadata();
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenySyncWithMetadataForNotAdmin() {
        doNothing().when(mockAttributeManager).syncWithMetadata();

        assertThrows(AccessDeniedException.class, () -> attributeApiService.syncWithMetadata());
    }
}
