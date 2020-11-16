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

package com.epam.pipeline.controller.entity;

import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclSid;
import com.epam.pipeline.manager.entity.EntityApiService;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.security.SecurityCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = EntityController.class)
public class EntityControllerTest extends AbstractControllerTest {

    private static final String ENTITIES_URL = SERVLET_PATH + "/entities";

    private static final String ACL_CLASS = "aclClass";
    private static final String IDENTIFIER = "identifier";
    private static final String ACL_CLASS_AS_STRING = String.valueOf(AclClass.DATA_STORAGE);

    final S3bucketDataStorage s3bucketDataStorage = DatastorageCreatorUtils.getS3bucketDataStorage();

    @Autowired
    private EntityApiService mockEntityApiService;

    @Test
    @WithMockUser
    public void shouldListToolGroups() {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(IDENTIFIER, TEST_STRING);
        params.add(ACL_CLASS, ACL_CLASS_AS_STRING);
        doReturn(s3bucketDataStorage).when(mockEntityApiService).loadByNameOrId(AclClass.DATA_STORAGE, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(ENTITIES_URL).params(params));

        Mockito.verify(mockEntityApiService).loadByNameOrId(AclClass.DATA_STORAGE, TEST_STRING);
        assertResponse(mvcResult, s3bucketDataStorage, DatastorageCreatorUtils.S3_BUCKET_TYPE);
    }

    @Test
    public void shouldFailListToolGroups() {
        performUnauthorizedRequest(get(ENTITIES_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadEntities() throws Exception {
        final List<S3bucketDataStorage> s3bucketDataStorageList = Collections.singletonList(s3bucketDataStorage);
        final Map<AclClass, List<S3bucketDataStorage>> map =
                Collections.singletonMap(AclClass.DATA_STORAGE, s3bucketDataStorageList);
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(ACL_CLASS, ACL_CLASS_AS_STRING);
        final AclSid aclSid = SecurityCreatorUtils.getAclSid();
        final String content = getObjectMapper().writeValueAsString(aclSid);
        doReturn(map).when(mockEntityApiService).loadAvailable(aclSid, AclClass.DATA_STORAGE);

        final MvcResult mvcResult = performRequest(post(ENTITIES_URL).params(params).content(content));

        Mockito.verify(mockEntityApiService).loadAvailable(aclSid, AclClass.DATA_STORAGE);
        assertResponse(mvcResult, map, SecurityCreatorUtils.ACL_SECURED_ENTITY_MAP_TYPE);
    }

    @Test
    public void shouldFailLoadEntities() {
        performUnauthorizedRequest(post(ENTITIES_URL));
    }
}
