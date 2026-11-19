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
import com.epam.pipeline.controller.vo.cluster.capacityreservation.CapacityBlockExtensionRequest;
import com.epam.pipeline.controller.vo.cluster.capacityreservation.CapacityBlockOfferingRequest;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockExtension;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockInstanceTypeInfo;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockOffering;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * REST layer for capacity reservations, under {@code /cluster/capacity-reservation}.
 *
 * <p>Reading is a list and a single reservation; writing is the two decisions a human makes about a request -
 * approving one that waits for it, and cancelling one that is no longer wanted. Creation belongs to the node pool
 * endpoint, since a reservation only ever exists for a pool. {@code offerings} and {@code instance-types} are
 * different from the rest. They search the AWS capacity block catalogue, and they work before any reservation or
 * pool exists.
 *
 * <p>{@code extension-offerings} and {@code extension} extend a capacity block already purchased past its current
 * end date - the same two decisions apply to a request they create as to the reservation itself, so a waiting one
 * is approved under {@code extension/{extensionId}/approve} rather than this controller's own {@code approve}.
 *
 * <p>Every method delegates to {@link CapacityReservationApiService}, which is where the authorization is.
 */
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

    @PostMapping("offerings")
    @Operation(summary = "Searches current capacity block offerings")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<CapacityBlockOffering>> listOfferings(
            final @RequestBody CapacityBlockOfferingRequest request) {
        return Result.success(apiService.listCapacityBlockOfferings(request));
    }

    @GetMapping("instance-types")
    @Operation(summary = "Lists the instance types a region currently sells as capacity blocks, with their specs")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<CapacityBlockInstanceTypeInfo>> listInstanceTypes(final @RequestParam Long regionId) {
        return Result.success(apiService.listCapacityBlockInstanceTypes(regionId));
    }

    @GetMapping("{id}/extension-offerings")
    @Operation(summary = "Searches current offerings to extend a purchased capacity block; with "
            + "findNearestAvailable set, falls back to the nearest shorter duration AWS still offers when none "
            + "exist at durationHours itself")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<CapacityBlockOffering>> listExtensionOfferings(
            final @PathVariable Long id, final @RequestParam Integer durationHours,
            final @RequestParam(defaultValue = "false") boolean findNearestAvailable) {
        return Result.success(apiService.listExtensionOfferings(id, durationHours, findNearestAvailable));
    }

    @PostMapping("{id}/extension")
    @Operation(summary = "Requests an extension of a purchased capacity block")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<CapacityBlockExtension> requestExtension(
            final @PathVariable Long id, final @RequestBody CapacityBlockExtensionRequest request) {
        return Result.success(apiService.requestExtension(
                id, request.getExtensionOfferingId(), request.getDurationHours(), request.getOriginId()));
    }

    @PostMapping("extension/{extensionId}/approve")
    @Operation(summary = "Approves a capacity block extension request")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<CapacityBlockExtension> approveExtension(final @PathVariable Long extensionId) {
        return Result.success(apiService.approveExtension(extensionId));
    }
}
