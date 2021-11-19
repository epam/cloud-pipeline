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

package com.epam.pipeline.dts.listing.rest.controller;

import com.epam.pipeline.dts.common.rest.controller.AbstractRestController;
import com.epam.pipeline.dts.common.rest.Result;
import com.epam.pipeline.dts.listing.rest.dto.ItemsListingRequestDTO;
import com.epam.pipeline.dts.listing.rest.dto.ListingItemsPagingDTO;
import com.epam.pipeline.dts.listing.rest.mapper.ListingItemsPagingMapper;
import com.epam.pipeline.dts.listing.service.ListingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.epam.pipeline.dts.common.rest.controller.AbstractRestController.API_STATUS_DESCRIPTION;
import static com.epam.pipeline.dts.common.rest.controller.AbstractRestController.HTTP_STATUS_OK;

@RequestMapping("list")
@Api(value = "Listing transfer items management")
@ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
@RestController
@AllArgsConstructor
public class ListingController extends AbstractRestController {
    private ListingService listingService;
    private ListingItemsPagingMapper listingItemsPagingMapper;

    @PostMapping
    @ApiOperation(
            value = "Returns storage content specified by path. " +
                    "If paging is specified returns content with required restrictions.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<ListingItemsPagingDTO> getListing(@RequestBody final ItemsListingRequestDTO request) {
        return Result.success(listingItemsPagingMapper.modelToDTO(listingService.list(request)));
    }
}
