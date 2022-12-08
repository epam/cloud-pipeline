/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.search.ElasticSearchRequest;
import com.epam.pipeline.controller.vo.search.FacetedSearchExportRequest;
import com.epam.pipeline.controller.vo.search.FacetedSearchRequest;
import com.epam.pipeline.entity.search.FacetedSearchResult;
import com.epam.pipeline.entity.search.SearchResult;
import com.epam.pipeline.manager.search.SearchManager;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
public class SearchController extends AbstractRestController {

    private final SearchManager searchManager;

    @RequestMapping(value = "/search", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Full text search over all application.",
            notes = "Full text search over all application.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<SearchResult> search(@RequestBody ElasticSearchRequest searchRequest) {
        return Result.success(searchManager.search(searchRequest));
    }

    @PostMapping(value = "/search/facet")
    @ResponseBody
    @ApiOperation(
            value = "Search with faceted filters",
            notes = "Search with faceted filters",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<FacetedSearchResult> facetedSearch(@RequestBody final FacetedSearchRequest searchRequest) {
        return Result.success(searchManager.facetedSearch(searchRequest));
    }

    @PostMapping(value = "/search/export")
    @ResponseBody
    @ApiOperation(
            value = "Export faceted search result as a csv file.",
            notes = "Export faceted search result as a csv file.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public void export(@RequestBody final FacetedSearchExportRequest searchExportRequest,
                       final HttpServletResponse response) throws IOException {
        final String csvFileName = StringUtils.isNotBlank(searchExportRequest.getCsvFileName())
                ? searchExportRequest.getCsvFileName()
                : String.format("facet_report_%s.csv", LocalDateTime.now());
        writeFileToResponse(response, searchManager.export(searchExportRequest), csvFileName);
    }
}
