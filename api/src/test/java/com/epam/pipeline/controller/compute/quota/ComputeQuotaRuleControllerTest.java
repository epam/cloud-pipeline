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

package com.epam.pipeline.controller.compute.quota;

import com.epam.pipeline.acl.compute.quota.ComputeQuotaRuleApiService;
import com.epam.pipeline.controller.vo.FilterFieldVO;
import com.epam.pipeline.dto.compute.quota.ComputeQuotaRule;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.compute.quota.ComputeQuotaRuleCreatorsUtils.COMPUTE_QUOTA_RULE_LIST_TYPE;
import static com.epam.pipeline.test.creator.compute.quota.ComputeQuotaRuleCreatorsUtils.COMPUTE_QUOTA_RULE_TYPE;
import static com.epam.pipeline.test.creator.compute.quota.ComputeQuotaRuleCreatorsUtils.ID;
import static com.epam.pipeline.test.creator.compute.quota.ComputeQuotaRuleCreatorsUtils.computeQuotaRule;
import static com.epam.pipeline.test.creator.compute.quota.ComputeQuotaRuleCreatorsUtils.computeQuotaRuleList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(controllers = ComputeQuotaRuleController.class)
public class ComputeQuotaRuleControllerTest extends AbstractControllerTest {

    private static final String RULES_URL = SERVLET_PATH + "/compute/quotas/rules";
    private static final String RULE_BY_ID_URL = RULES_URL + "/%d";
    private static final String KEYWORDS_URL = RULES_URL + "/keywords";
    private static final TypeReference<Result<List<FilterFieldVO>>> FILTER_FIELD_LIST_TYPE =
            new TypeReference<Result<List<FilterFieldVO>>>() {};

    @Autowired
    private ComputeQuotaRuleApiService mockComputeQuotaRuleApiService;

    @Test
    public void shouldFailLoadAllForUnauthorizedUser() {
        performUnauthorizedRequest(get(RULES_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadAll() {
        final List<ComputeQuotaRule> rules = computeQuotaRuleList();
        doReturn(rules).when(mockComputeQuotaRuleApiService).loadAll();

        final MvcResult result = performRequest(get(RULES_URL));

        verify(mockComputeQuotaRuleApiService).loadAll();
        assertResponse(result, rules, COMPUTE_QUOTA_RULE_LIST_TYPE);
    }

    @Test
    public void shouldFailCreateForUnauthorizedUser() {
        performUnauthorizedRequest(post(RULES_URL));
    }

    @Test
    @WithMockUser
    public void shouldCreate() throws Exception {
        final ComputeQuotaRule rule = computeQuotaRule();
        doReturn(rule).when(mockComputeQuotaRuleApiService).create(rule);
        final String content = getObjectMapper().writeValueAsString(rule);

        final MvcResult result = performRequest(post(RULES_URL).content(content));

        verify(mockComputeQuotaRuleApiService).create(rule);
        assertResponse(result, rule, COMPUTE_QUOTA_RULE_TYPE);
    }

    @Test
    public void shouldFailUpdateForUnauthorizedUser() {
        performUnauthorizedRequest(put(String.format(RULE_BY_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUpdate() throws Exception {
        final ComputeQuotaRule rule = computeQuotaRule();
        doReturn(rule).when(mockComputeQuotaRuleApiService).update(ID, rule);
        final String content = getObjectMapper().writeValueAsString(rule);

        final MvcResult result = performRequest(put(String.format(RULE_BY_ID_URL, ID)).content(content));

        verify(mockComputeQuotaRuleApiService).update(ID, rule);
        assertResponse(result, rule, COMPUTE_QUOTA_RULE_TYPE);
    }

    @Test
    public void shouldFailDeleteForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(RULE_BY_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDelete() {
        final MvcResult result = performRequest(delete(String.format(RULE_BY_ID_URL, ID)));

        verify(mockComputeQuotaRuleApiService).delete(ID);
        assertResponse(result, null, new TypeReference<Result<Void>>() {});
    }

    @Test
    public void shouldFailGetKeywordsForUnauthorizedUser() {
        performUnauthorizedRequest(get(KEYWORDS_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetKeywords() {
        final List<FilterFieldVO> keywords = Collections.emptyList();
        doReturn(keywords).when(mockComputeQuotaRuleApiService).getKeywords();

        final MvcResult result = performRequest(get(KEYWORDS_URL));

        verify(mockComputeQuotaRuleApiService).getKeywords();
        assertResponse(result, keywords, FILTER_FIELD_LIST_TYPE);
    }
}
