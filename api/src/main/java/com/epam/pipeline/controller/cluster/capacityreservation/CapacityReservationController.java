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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@Tag(name = "capacity-reservation-controller", description = "Capacity Reservation Management")
@RequiredArgsConstructor
@RequestMapping("/cluster/capacity-reservation")
@ResponseBody
public class CapacityReservationController extends AbstractRestController {

    private final CapacityReservationApiService apiService;

    @GetMapping
    @Operation(summary = "Returns capacity reservations visible to the current user")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<CapacityReservation>> loadAll() {
        return Result.success(apiService.loadAll());
    }

    @GetMapping("{id}")
    @Operation(summary = "Returns a capacity reservation by id")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<CapacityReservation> load(final @PathVariable Long id) {
        return Result.success(apiService.load(id));
    }

    @PostMapping("{id}/approve")
    @Operation(summary = "Approves a capacity reservation request")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<CapacityReservation> approve(final @PathVariable Long id) {
        return Result.success(apiService.approve(id));
    }

    @PostMapping("{id}/cancel")
    @Operation(summary = "Cancels a capacity reservation")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<CapacityReservation> cancel(final @PathVariable Long id) {
        return Result.success(apiService.cancel(id));
    }
}
