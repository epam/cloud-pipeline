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

package com.epam.pipeline.controller.cluster.capacityreservation;

import com.epam.pipeline.acl.cluster.capacityreservation.CapacityReservationApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * REST layer for capacity reservations, under {@code /cluster/capacity-reservation}.
 *
 * <p>Reading is a list and a single reservation; writing is the two decisions a human makes about a request -
 * approving one that waits for it, and cancelling one that is no longer wanted. Creation belongs to the node pool
 * endpoint, since a reservation only ever exists for a pool.
 *
 * <p>Every method delegates to {@link CapacityReservationApiService}, which is where the authorization is.
 */
@Controller
@Api(value = "Capacity Reservation Management")
@RequiredArgsConstructor
@RequestMapping("/cluster/capacity-reservation")
@ResponseBody
public class CapacityReservationController extends AbstractRestController {

    private final CapacityReservationApiService apiService;

    @GetMapping
    @ApiOperation(value = "Returns capacity reservations visible to the current user",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<CapacityReservation>> loadAll() {
        return Result.success(apiService.loadAll());
    }

    @GetMapping("{id}")
    @ApiOperation(value = "Returns a capacity reservation by id", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<CapacityReservation> load(final @PathVariable Long id) {
        return Result.success(apiService.load(id));
    }

    @PostMapping("{id}/approve")
    @ApiOperation(value = "Approves a capacity reservation request",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<CapacityReservation> approve(final @PathVariable Long id) {
        return Result.success(apiService.approve(id));
    }

    @PostMapping("{id}/cancel")
    @ApiOperation(value = "Cancels a capacity reservation", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<CapacityReservation> cancel(final @PathVariable Long id) {
        return Result.success(apiService.cancel(id));
    }
}
