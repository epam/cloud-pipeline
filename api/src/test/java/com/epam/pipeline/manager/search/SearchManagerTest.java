/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.manager.search;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.search.FacetedSearchExportRequest;
import com.epam.pipeline.controller.vo.search.FacetedSearchRequest;
import com.epam.pipeline.entity.search.FacetedSearchResult;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.test.creator.search.SearchCreatorUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static com.epam.pipeline.test.creator.search.SearchCreatorUtils.HEADER_WITH_ATTRIBUTE;
import static com.epam.pipeline.test.creator.search.SearchCreatorUtils.HUMAN;
import static com.epam.pipeline.test.creator.search.SearchCreatorUtils.ROLE_USER;
import static com.epam.pipeline.test.creator.search.SearchCreatorUtils.SPECIES;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SearchManagerTest extends AbstractSpringTest {

    private static final String EXPORT_FILE_NAME = "test_search_export.csv";
    private static final int PAGE_SIZE = 5000;

    @SpyBean
    private SearchManager searchManager;

    @SpyBean
    private PreferenceManager preferenceManager;

    @Before
    public void setUpPreferenceManager() {
        ReflectionTestUtils.setField(searchManager, "preferenceManager", preferenceManager);
        when(preferenceManager.getPreference(SystemPreferences.SEARCH_EXPORT_PAGE_SIZE)).thenReturn(PAGE_SIZE);
    }

    @Test
    public void shouldExportFacetedSearchResultWithPageSize() {
        final FacetedSearchResult facetedSearchResult = SearchCreatorUtils.getFacetedSearchResult(HUMAN);
        final FacetedSearchRequest facetedSearchRequest = SearchCreatorUtils.getFacetedSearchRequest(SPECIES);
        doReturn(facetedSearchResult).when(searchManager).facetedSearch(facetedSearchRequest);
        final FacetedSearchExportRequest facetedSearchExportRequest = SearchCreatorUtils
                .getFacetedSearchExportRequest(EXPORT_FILE_NAME, SPECIES);
        final String[] exportedCsv = new String(searchManager.export(facetedSearchExportRequest))
                .split("\n");
        Assert.assertNotNull(exportedCsv);
        Assert.assertEquals(3, exportedCsv.length);
        Assert.assertEquals(HEADER_WITH_ATTRIBUTE, exportedCsv[0]);
        Assert.assertTrue(Arrays.stream(exportedCsv).anyMatch(s -> s.contains(ROLE_USER) && s.contains(HUMAN)));
    }

    @Test
    public void shouldExportWithSearchExportPageSize() {
        final FacetedSearchResult facetedSearchResult = SearchCreatorUtils.getFacetedSearchResult(HUMAN);
        final FacetedSearchExportRequest facetedSearchExportRequest = SearchCreatorUtils
                .getFacetedSearchExportRequest(EXPORT_FILE_NAME, SPECIES);
        final FacetedSearchRequest facetedSearchRequest = facetedSearchExportRequest.getFacetedSearchRequest();
        doReturn(facetedSearchResult).when(searchManager)
                .facetedSearch(facetedSearchRequest);
        facetedSearchRequest.setPageSize(null);
        final String[] exportedCsv = new String(searchManager.export(facetedSearchExportRequest))
                .split("\n");
        verify(searchManager).facetedSearch(facetedSearchRequest);
        verify(preferenceManager, atLeast(1)).getPreference(SystemPreferences.SEARCH_EXPORT_PAGE_SIZE);
        Assert.assertNotNull(exportedCsv);
        Assert.assertEquals(3, exportedCsv.length);
        Assert.assertEquals(HEADER_WITH_ATTRIBUTE, exportedCsv[0]);
        Assert.assertTrue(Arrays.stream(exportedCsv).anyMatch(s -> s.contains(ROLE_USER) && s.contains(HUMAN)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToExportWithoutPageSize() {
        when(preferenceManager.getPreference(SystemPreferences.SEARCH_EXPORT_PAGE_SIZE)).thenReturn(null);
        final FacetedSearchExportRequest facetedSearchExportRequest = SearchCreatorUtils
                .getFacetedSearchExportRequest(EXPORT_FILE_NAME, SPECIES);
        facetedSearchExportRequest.getFacetedSearchRequest().setPageSize(null);
        searchManager.export(facetedSearchExportRequest);
    }
}
