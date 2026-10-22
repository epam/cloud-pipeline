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
import com.epam.pipeline.entity.search.SearchTemplateExportInfo;
import com.epam.pipeline.manager.search.SearchExportManager;
import com.epam.pipeline.manager.search.SearchManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@Tag(name = "search-controller", description = "Search Controller")
public class SearchController extends AbstractRestController {

    private final SearchManager searchManager;
    private final SearchExportManager searchExportManager;

    @RequestMapping(value = "/search", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Full text search over all application.",
            description = "Full text search over all application.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<SearchResult> search(@RequestBody ElasticSearchRequest searchRequest) {
        return Result.success(searchManager.search(searchRequest));
    }

    @PostMapping(value = "/search/facet")
    @ResponseBody
    @Operation(
            summary = "Search with faceted filters",
            description = "Search with faceted filters")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<FacetedSearchResult> facetedSearch(@RequestBody final FacetedSearchRequest searchRequest) {
        return Result.success(searchManager.facetedSearch(searchRequest));
    }

    @PostMapping(value = "/search/facet/export")
    @ResponseBody
    @Operation(
            summary = "Export faceted search result as a csv file.",
            description = "Export faceted search result as a csv file.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public void export(@RequestBody final FacetedSearchExportRequest searchExportRequest,
                       final HttpServletResponse response) throws IOException {
        final String reportFileName = StringUtils.isNotBlank(searchExportRequest.getCsvFileName())
                ? searchExportRequest.getCsvFileName()
                : String.format("facet_report_%s.csv", LocalDateTime.now());
        writeFileToResponse(response, searchExportManager.export(searchExportRequest), reportFileName);
    }

    @PostMapping("/search/facet/export/templates")
    @ResponseBody
    @Operation(
            summary = "Export faceted search result in a predefined format.",
            description = "Export faceted search result in a predefined format.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public void templateExport(@RequestBody final FacetedSearchRequest searchRequest,
                               @RequestParam final String templateId,
                               @RequestParam(required = false) final String fileName,
                               final HttpServletResponse response) throws IOException {
        final String reportFileName = StringUtils.isNotBlank(fileName)
                ? fileName
                : String.format("%s-%s.xls", templateId, LocalDateTime.now());
        writeFileToResponse(response, searchExportManager.templateExport(searchRequest, templateId), reportFileName);
    }

    @PostMapping("/search/facet/export/templates/save")
    @ResponseBody
    @Operation(
            summary = "Persists export faceted search result in a predefined format by specified cloud path.",
            description = "Persists export faceted search result in a predefined format by specified cloud path.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<SearchTemplateExportInfo> saveTemplateExport(@RequestBody final FacetedSearchRequest searchRequest,
                                                               @RequestParam final String templateId) {
        return Result.success(searchExportManager.saveTemplateExport(searchRequest, templateId));
    }
}
