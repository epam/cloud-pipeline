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

package com.epam.pipeline.controller.datastorage;

import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class DataStorageRuleControllerTest extends AbstractDataStorageControllerTest {

    @Test
    public void shouldFailSaveDataStorageRuleForUnauthorizedUser() {
        performUnauthorizedRequest(post(SAVE_RULE_URL));
    }

    @Test
    @WithMockUser
    public void shouldSaveDataStorageRule() throws Exception {
        final String content = getObjectMapper().writeValueAsString(dataStorageRule);
        Mockito.doReturn(dataStorageRule).when(mockStorageApiService).createRule(dataStorageRule);

        final MvcResult mvcResult = performRequest(post(SAVE_RULE_URL).content(content));

        Mockito.verify(mockStorageApiService).createRule(dataStorageRule);
        assertResponse(mvcResult, dataStorageRule, DatastorageCreatorUtils.DATA_STORAGE_RULE_URL);
    }

    @Test
    public void shouldFailLoadDataStorageRuleForUnauthorizedUser() {
        performUnauthorizedRequest(get(LOAD_RULES_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadDataStorageRule() {
        final List<DataStorageRule> dataStorageRules = Collections.singletonList(dataStorageRule);
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PIPELINE_ID, ID_AS_STRING);
        params.add(FILE_MASK, TEST);
        Mockito.doReturn(dataStorageRules).when(mockStorageApiService).loadRules(ID, TEST);

        final MvcResult mvcResult = performRequest(get(LOAD_RULES_URL).params(params));

        Mockito.verify(mockStorageApiService).loadRules(ID, TEST);
        assertResponse(mvcResult, dataStorageRules, DatastorageCreatorUtils.DATA_STORAGE_RULE_LIST_URL);
    }

    @Test
    public void shouldFailDeleteDataStorageRuleForUnauthorizedUser() {
        performUnauthorizedRequest(delete(DELETE_RULES_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteDataStorageRule() {
        Mockito.doReturn(dataStorageRule).when(mockStorageApiService).deleteRule(ID, TEST);
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(ID_PARAM, ID_AS_STRING);
        params.add(FILE_MASK, TEST);

        final MvcResult mvcResult = performRequest(delete(DELETE_RULES_URL).params(params));

        Mockito.verify(mockStorageApiService).deleteRule(ID, TEST);
        assertResponse(mvcResult, dataStorageRule, DatastorageCreatorUtils.DATA_STORAGE_RULE_URL);
    }
}
