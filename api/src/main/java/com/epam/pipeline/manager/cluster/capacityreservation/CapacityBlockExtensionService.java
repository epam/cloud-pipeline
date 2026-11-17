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

package com.epam.pipeline.manager.cluster.capacityreservation;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.cluster.capacityreservation.CapacityBlockExtensionDao;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockExtension;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockExtensionStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockOffering;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityBlockExtension;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservationState;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Business-logic layer for a request to extend a purchased {@link CapacityReservationType#CAPACITY_BLOCK} past its
 * current end date - {@link #requestExtension} and {@link #approve} are its two ways forward.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CapacityBlockExtensionService {

    private static final int HOURS_PER_DAY = 24;
    static final int MAX_ONE_DAY_STEP_TOTAL_HOURS = 14 * HOURS_PER_DAY;
    static final int MAX_TOTAL_EXTENSION_HOURS = 182 * HOURS_PER_DAY;
    private static final int SECONDS_PER_HOUR = 3600;
    private static final int SEVEN_DAY_STEP_HOURS = 7 * HOURS_PER_DAY;

    private final CapacityReservationService reservationService;
    private final CapacityBlockExtensionDao extensionDao;
    private final CapacityReservationCloudFacade cloudFacade;
    private final CapacityReservationApprovalService approvalService;
    private final MessageHelper messageHelper;
    private final AuthManager authManager;

    /**
     * Searches current AWS offerings to extend a purchased capacity block.
     *
     * @param reservationId the capacity block reservation to extend
     * @param durationHours how many hours to extend it by; must be a duration {@link #isValidExtensionDuration}
     *                       allows
     * @param findNearestAvailable when {@code true} and AWS has no offering at {@code durationHours} itself, falls
     *                             back to the longest shorter duration AWS does still offer, instead of returning
     *                             nothing
     * @return the offerings found - for {@code durationHours} itself, or, with {@code findNearestAvailable} set,
     *         for the nearest shorter duration found below it when none exist at {@code durationHours}
     */
    public List<CapacityBlockOffering> listOfferings(final Long reservationId, final Integer durationHours,
                                                      final boolean findNearestAvailable) {
        Assert.notNull(durationHours, messageHelper.getMessage(
                MessageConstants.ERROR_CAPACITY_BLOCK_EXTENSION_DURATION_REQUIRED));
        final CapacityReservation reservation = reservationService.load(reservationId);
        requireExtendable(reservation, durationHours);
        final List<CapacityBlockOffering> offerings =
                cloudFacade.listCapacityBlockExtensionOfferings(reservation, durationHours);
        return offerings.isEmpty() && findNearestAvailable
                ? nearestAvailableOfferings(reservation, durationHours)
                : offerings;
    }

    @Transactional
    public CapacityBlockExtension requestExtension(final Long reservationId, final String offeringId,
                                                   final Integer durationHours, final Long originId) {
        Assert.isTrue(StringUtils.isNotBlank(offeringId), messageHelper.getMessage(
                MessageConstants.ERROR_CAPACITY_BLOCK_EXTENSION_OFFERING_ID_REQUIRED));
        Assert.notNull(durationHours, messageHelper.getMessage(
                MessageConstants.ERROR_CAPACITY_BLOCK_EXTENSION_DURATION_REQUIRED));
        final CapacityReservation reservation = reservationService.loadForUpdate(reservationId);
        requireExtendable(reservation, durationHours);

        final CapacityBlockExtension extension = CapacityBlockExtension.builder()
                .reservationId(reservationId)
                .originId(originId)
                .extensionOfferingId(offeringId)
                .durationHours(durationHours)
                .requestedBy(authManager.getAuthorizedUser())
                .created(DateUtils.nowUTC())
                .updated(DateUtils.nowUTC())
                .status(resolveApprovalStatus(reservation, durationHours, originId))
                .build();
        final CapacityBlockExtension created = extensionDao.create(extension);
        log.debug("Capacity block extension {} requested for reservation {}, {}h, status {}",
                created.getId(), reservationId, durationHours, created.getStatus());
        return CapacityBlockExtensionStatus.APPROVED == created.getStatus()
                ? purchase(created, reservation) : created;
    }

    /**
     * Re-checks the status after taking the lock, not just before: otherwise two concurrent approvals could both
     * pass the check and double-purchase a non-refundable extension.
     */
    @Transactional
    public CapacityBlockExtension approve(final Long extensionId) {
        final Long reservationId = load(extensionId).getReservationId();
        final CapacityReservation reservation = reservationService.loadForUpdate(reservationId);
        final CapacityBlockExtension extension = load(extensionId);
        Assert.state(CapacityBlockExtensionStatus.REQUIRED_APPROVE == extension.getStatus(),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_BLOCK_EXTENSION_ILLEGAL_TRANSITION,
                        extensionId, extension.getStatus(), CapacityBlockExtensionStatus.APPROVED));
        return purchase(extension, reservation);
    }

    private CapacityBlockExtension load(final Long extensionId) {
        return extensionDao.find(extensionId)
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_CAPACITY_BLOCK_EXTENSION_NOT_FOUND, extensionId)));
    }

    private CapacityBlockExtensionStatus resolveApprovalStatus(final CapacityReservation reservation,
                                                              final int durationHours, final Long originId) {
        if (inheritsApproval(reservation.getId(), originId, durationHours)) {
            return CapacityBlockExtensionStatus.APPROVED;
        }
        final CapacityReservationStatus decision = approvalService.evaluate(reservation.toBuilder()
                .originId(null)
                .commitmentDuration((long) durationHours * SECONDS_PER_HOUR)
                .build());
        return CapacityReservationStatus.APPROVED == decision
                ? CapacityBlockExtensionStatus.APPROVED : CapacityBlockExtensionStatus.REQUIRED_APPROVE;
    }

    /**
     * Mirrors {@code CapacityReservationApprovalService#inheritsApproval}, over the extension's own retry chain.
     */
    private boolean inheritsApproval(final Long reservationId, final Long originId, final int durationHours) {
        return Optional.ofNullable(originId)
                .flatMap(extensionDao::find)
                .filter(origin -> CapacityBlockExtensionStatus.FAILED == origin.getStatus())
                .filter(origin -> reservationId.equals(origin.getReservationId()))
                .filter(origin -> durationHours <= origin.getDurationHours())
                .isPresent();
    }

    /**
     * AWS gives this call no idempotency token, and an uncertain outcome (e.g. a timeout) is recorded as
     * {@link CapacityBlockExtensionStatus#FAILED} the same as a definitive refusal - a risk left open for now.
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private CapacityBlockExtension purchase(final CapacityBlockExtension extension,
                                            final CapacityReservation reservation) {
        extension.setStatus(CapacityBlockExtensionStatus.APPROVED);
        final CloudCapacityBlockExtension purchased;
        try {
            purchased = cloudFacade.purchaseCapacityBlockExtension(reservation, extension.getExtensionOfferingId());
        } catch (Exception e) {
            log.warn("Purchasing capacity block extension {} for reservation {} failed: {}",
                    extension.getId(), reservation.getId(), e.getMessage());
            extension.setStatus(CapacityBlockExtensionStatus.FAILED);
            extension.setStatusReason(e.getMessage());
            extension.setUpdated(DateUtils.nowUTC());
            return extensionDao.update(extension);
        }
        applyPurchaseResult(extension, reservation, purchased);
        extension.setUpdated(DateUtils.nowUTC());
        return extensionDao.update(extension);
    }

    /**
     * {@code durationHours} shall be overwritten with what AWS actually granted, not what was requested.
     */
    private void applyPurchaseResult(final CapacityBlockExtension extension, final CapacityReservation reservation,
                                     final CloudCapacityBlockExtension purchased) {
        if (CloudCapacityReservationState.ACTIVE == purchased.getState()) {
            extension.setStatus(CapacityBlockExtensionStatus.SUCCEEDED);
            extension.setStartDate(purchased.getStartDate());
            extension.setEndDate(purchased.getEndDate());
            extension.setDurationHours((int) Duration.between(
                    purchased.getStartDate(), purchased.getEndDate()).toHours());
            reservation.setEndDate(purchased.getEndDate());
            reservationService.recordExtendedEndDate(reservation);
            log.debug("Capacity block extension {} succeeded for reservation {}; new end date {}",
                    extension.getId(), reservation.getId(), purchased.getEndDate());
        } else {
            extension.setStatus(CapacityBlockExtensionStatus.FAILED);
            extension.setStatusReason(purchased.getStateReason());
        }
    }

    private void requireExtendable(final CapacityReservation reservation, final int durationHours) {
        Assert.state(reservation.isCapacityBlock(), messageHelper.getMessage(
                MessageConstants.ERROR_CAPACITY_BLOCK_EXTENSION_TYPE_NOT_SUPPORTED, reservation.getId()));
        Assert.state(reservation.isWithinExtensionLeadWindow(), messageHelper.getMessage(
                MessageConstants.ERROR_CAPACITY_BLOCK_EXTENSION_NOT_ACTIVE, reservation.getId()));
        Assert.isTrue(isValidExtensionDuration(durationHours), messageHelper.getMessage(
                MessageConstants.ERROR_CAPACITY_BLOCK_EXTENSION_DURATION_INVALID,
                reservation.getId(), durationHours));
    }

    /**
     * AWS steps a single extension by 1 day up to 14 days, then by 7 days up to 182 days.
     */
    static boolean isValidExtensionDuration(final int requestedHours) {
        if (requestedHours <= 0) {
            return false;
        }
        if (requestedHours <= MAX_ONE_DAY_STEP_TOTAL_HOURS) {
            return requestedHours % HOURS_PER_DAY == 0;
        }
        return requestedHours <= MAX_TOTAL_EXTENSION_HOURS && requestedHours % SEVEN_DAY_STEP_HOURS == 0;
    }

    private List<CapacityBlockOffering> nearestAvailableOfferings(final CapacityReservation reservation,
                                                                  final int requestedHours) {
        final List<Integer> candidates = getAvailableDurationsSeries(requestedHours);
        List<CapacityBlockOffering> nearest = Collections.emptyList();
        int lowerBound = 0;
        int upperBound = candidates.size() - 1;
        while (lowerBound <= upperBound) {
            final int midIndex = lowerBound + (upperBound - lowerBound) / 2;
            final List<CapacityBlockOffering> offerings = cloudFacade.listCapacityBlockExtensionOfferings(
                    reservation, candidates.get(midIndex));
            if (offerings.isEmpty()) {
                upperBound = midIndex - 1;
            } else {
                nearest = offerings;
                lowerBound = midIndex + 1;
            }
        }
        return nearest;
    }

    /**
     * The durations below {@code requestedHours} that {@link #isValidExtensionDuration} itself allows, ascending -
     * the 1-day step up to 14 days followed by the 7-day step up to 182 days,
     * glued into the one list {@link #nearestAvailableOfferings} searches, so
     * a search that crosses from one step to the other needs no case of its own.
     */
    static List<Integer> getAvailableDurationsSeries(final int requestedHours) {
        final List<Integer> durations = new ArrayList<>();
        for (int hours = HOURS_PER_DAY; hours < requestedHours && hours <= MAX_ONE_DAY_STEP_TOTAL_HOURS;
             hours += HOURS_PER_DAY) {
            durations.add(hours);
        }
        for (int hours = MAX_ONE_DAY_STEP_TOTAL_HOURS + SEVEN_DAY_STEP_HOURS;
             hours < requestedHours && hours <= MAX_TOTAL_EXTENSION_HOURS; hours += SEVEN_DAY_STEP_HOURS) {
            durations.add(hours);
        }
        return durations;
    }
}
