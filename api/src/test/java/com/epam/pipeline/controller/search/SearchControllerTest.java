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

package com.epam.pipeline.controller.search;

import com.epam.pipeline.controller.vo.search.ElasticSearchRequest;
import com.epam.pipeline.entity.search.SearchResult;
import com.epam.pipeline.manager.search.SearchManager;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import static com.epam.pipeline.test.creator.search.SearchCreatorUtils.SEARCH_RESULT_TYPE;
import static com.epam.pipeline.test.creator.search.SearchCreatorUtils.getElasticSearchRequest;
import static com.epam.pipeline.test.creator.search.SearchCreatorUtils.getSearchResult;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = SearchController.class)
public class SearchControllerTest extends AbstractControllerTest {

    private static final String SEARCH_URL = SERVLET_PATH + "/search";
    private final SearchResult searchResult = getSearchResult();
    private final ElasticSearchRequest elasticSearchRequest = getElasticSearchRequest();

    @Autowired
    private SearchManager mockSearchManager;

    @Test
    public void shouldFailSearchForUnauthorizedUser() {
        performUnauthorizedRequest(post(SEARCH_URL));
    }

    @Test
    @WithMockUser
    public void shouldSearch() throws Exception {
        final String content = getObjectMapper().writeValueAsString(elasticSearchRequest);
        doReturn(searchResult).when(mockSearchManager).search(elasticSearchRequest);

        final MvcResult mvcResult = performRequest(post(SEARCH_URL).content(content));

        verify(mockSearchManager).search(elasticSearchRequest);
        assertResponse(mvcResult, searchResult, SEARCH_RESULT_TYPE);
    }
}
