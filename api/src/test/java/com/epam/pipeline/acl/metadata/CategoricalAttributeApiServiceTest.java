/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.metadata.MetadataCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class CategoricalAttributeApiServiceTest extends AbstractAclTest {

    private final CategoricalAttribute attribute = MetadataCreatorUtils.getCategoricalAttribute();
    private final CategoricalAttribute existingAttribute = MetadataCreatorUtils.getCategoricalAttributeWithId();

    @Autowired
    private CategoricalAttributeApiService attributeApiService;

    @Autowired
    private CategoricalAttributeManager mockAttributeManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateAttributeForAdmin() {
        doReturn(attribute).when(mockAttributeManager).create(attribute);
        assertThat(attributeApiService.updateCategoricalAttribute(attribute)).isEqualTo(attribute);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyCreateAttributesForNotAdmin() {
        doReturn(attribute).when(mockAttributeManager).create(attribute);
        assertThrows(AccessDeniedException.class, () -> attributeApiService.updateCategoricalAttribute(attribute));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateAttributeForAdmin() {
        doReturn(existingAttribute).when(mockAttributeManager).update(existingAttribute);

        assertThat(attributeApiService.updateCategoricalAttribute(existingAttribute)).isEqualTo(existingAttribute);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateAttributesForNotAdmin() {
        doReturn(existingAttribute).when(mockAttributeManager).update(existingAttribute);

        assertThrows(AccessDeniedException.class, () ->
            attributeApiService.updateCategoricalAttribute(existingAttribute));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllAttributesForAdmin() {
        final CategoricalAttribute userAttribute = createUserAttribute();
        final CategoricalAttribute adminAttribute = createAdminAttribute();
        final List<CategoricalAttribute> attributes = listOf(userAttribute, adminAttribute);
        doReturn(attributes).when(mockAttributeManager).loadAll();
        assertThat(attributeApiService.loadAll()).isEqualTo(attributes);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadOnlyAvailableAttributesForNotAdmin() {
        final CategoricalAttribute userAttribute = createUserAttribute();
        final CategoricalAttribute adminAttribute = createAdminAttribute();
        final List<CategoricalAttribute> attributes = listOf(userAttribute, adminAttribute);
        doReturn(attributes).when(mockAttributeManager).loadAll();
        assertThat(attributeApiService.loadAll()).isEqualTo(Collections.singletonList(userAttribute));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllValuesForKeyForAdmin() {
        final CategoricalAttribute categoricalAttribute = createUserAttribute();
        doReturn(categoricalAttribute).when(mockAttributeManager).loadByNameOrId(TEST_STRING);
        assertThat(attributeApiService.loadAllValuesForKey(TEST_STRING)).isEqualTo(categoricalAttribute);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadAllValuesForKeyForNotAdmin() {
        final CategoricalAttribute categoricalAttribute = createAdminAttribute();
        doReturn(categoricalAttribute).when(mockAttributeManager).loadByNameOrId(TEST_STRING);
        assertThrows(AccessDeniedException.class, () -> attributeApiService.loadAllValuesForKey(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteAttributeValuesForAdmin() {
        doReturn(true).when(mockAttributeManager).delete(TEST_STRING);

        assertThat(attributeApiService.deleteAttributeValues(TEST_STRING)).isEqualTo(true);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteAttributeValuesForNotAdmin() {
        doReturn(true).when(mockAttributeManager).delete(TEST_STRING);

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

    private CategoricalAttribute createAdminAttribute() {
        return createAttribute(CommonCreatorConstants.ID, ADMIN_ROLE);
    }

    private CategoricalAttribute createUserAttribute() {
        return createAttribute(CommonCreatorConstants.ID_2, SIMPLE_USER);
    }

    private CategoricalAttribute createAttribute(final Long id, final String owner) {
        final CategoricalAttribute categoricalAttribute = MetadataCreatorUtils.getCategoricalAttribute();
        categoricalAttribute.setId(id);
        categoricalAttribute.setOwner(owner);
        initAclEntity(categoricalAttribute);
        return categoricalAttribute;
    }

    private List<CategoricalAttribute> listOf(final CategoricalAttribute ... attributes) {
        return Stream.of(attributes).collect(Collectors.toList());
    }
}
