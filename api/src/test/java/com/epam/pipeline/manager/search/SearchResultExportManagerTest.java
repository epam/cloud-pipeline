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
import com.epam.pipeline.entity.search.FacetedSearchResult;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.search.SearchCreatorUtils.HEADER_WITH_ATTRIBUTE;
import static com.epam.pipeline.test.creator.search.SearchCreatorUtils.HUMAN;
import static com.epam.pipeline.test.creator.search.SearchCreatorUtils.ROLE_USER;
import static com.epam.pipeline.test.creator.search.SearchCreatorUtils.SPECIES;
import static com.epam.pipeline.test.creator.search.SearchCreatorUtils.getFacetedSearchExportRequest;
import static com.epam.pipeline.test.creator.search.SearchCreatorUtils.getFacetedSearchResult;

public class SearchResultExportManagerTest extends AbstractSpringTest {

    private static final String EXPORT_FILE_NAME = "test_search_export.csv";
    private static final String PLAIN_HEADER = "Name,Changed,Size,Owner,Path,Cloud path,Mount path";

    @Autowired
    private SearchResultExportManager searchResultExportManager;

    @Test
    public void shouldExportFacetedSearchResult() {
        final FacetedSearchResult facetedSearchResult = getFacetedSearchResult(null);
        final FacetedSearchExportRequest facetedSearchExportRequest = getFacetedSearchExportRequest(EXPORT_FILE_NAME,
                null);
        final String[] exportedCsv = new String(
                searchResultExportManager.export(facetedSearchExportRequest, facetedSearchResult)).split("\n");
        Assert.assertNotNull(exportedCsv);
        Assert.assertEquals(2, exportedCsv.length);
        Assert.assertEquals(PLAIN_HEADER, exportedCsv[0]);
        Assert.assertTrue(Arrays.stream(exportedCsv).anyMatch(s -> s.contains(TEST_STRING)));
    }

    @Test
    public void shouldExportFacetedSearchResultWithAttributes() {
        final FacetedSearchResult facetedSearchResult = getFacetedSearchResult(HUMAN);
        final FacetedSearchExportRequest facetedSearchExportRequest = getFacetedSearchExportRequest(EXPORT_FILE_NAME,
                SPECIES);
        final String[] exportedCsv = new String(
                searchResultExportManager.export(facetedSearchExportRequest, facetedSearchResult)).split("\n");
        Assert.assertNotNull(exportedCsv);
        Assert.assertEquals(3, exportedCsv.length);
        Assert.assertEquals(HEADER_WITH_ATTRIBUTE, exportedCsv[0]);
        Assert.assertTrue(Arrays.stream(exportedCsv).anyMatch(s -> s.contains(ROLE_USER) && s.contains(HUMAN)));
    }
}
