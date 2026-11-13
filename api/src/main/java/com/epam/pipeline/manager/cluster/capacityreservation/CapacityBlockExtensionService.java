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

    public List<CapacityBlockOffering> listOfferings(final Long reservationId, final Integer durationHours) {
        Assert.notNull(durationHours, messageHelper.getMessage(
                MessageConstants.ERROR_CAPACITY_BLOCK_EXTENSION_DURATION_REQUIRED));
        final CapacityReservation reservation = reservationService.load(reservationId);
        requireExtendable(reservation, durationHours);
        return cloudFacade.listCapacityBlockExtensionOfferings(reservation, durationHours);
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
                .status(decide(reservation, durationHours, originId))
                .build();
        final CapacityBlockExtension created = extensionDao.create(extension);
        log.debug("Capacity block extension {} requested for reservation {}, {}h, status {}",
                created.getId(), reservationId, durationHours, created.getStatus());
        return CapacityBlockExtensionStatus.APPROVED == created.getStatus()
                ? purchase(created, reservation) : created;
    }

    /**
     * The status check has to run again once the lock below is held, not just before it: two concurrent approvals
     * of the same extension could otherwise both pass it before either reaches the lock, and the second would then
     * purchase an extension already purchased by the first - a non-refundable payment, so not safe to risk.
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

    /**
     * An extension is a non-refundable up-front payment, so this puts it through the same
     * {@link CapacityReservationApprovalService} policies as the original purchase: a throwaway, unpersisted copy
     * of the reservation carries the extension's own duration as its {@code commitmentDuration}, so the existing
     * policy engine evaluates it exactly like a new request, with no changes to the condition DSL it reads. A
     * retry naming a {@code FAILED} {@code originId} skips that policy call entirely instead, the same idea as
     * {@code CapacityReservationApprovalService#inheritsApproval} for the reservation itself: asking for no more
     * than the origin did is not a bigger commitment than an administrator already approved once.
     *
     * <p>{@code originId} is cleared on the throwaway copy passed to {@link CapacityReservationApprovalService
     * #evaluate}: left as the reservation's own, it would make that call treat the extension as inheriting the
     * approval of whatever retry chain the reservation itself came from, bypassing every policy an administrator
     * configured for the extension itself. The extension's own retry chain - {@code originId} as this method's own
     * parameter, not the reservation's - is handled separately by {@link #inheritsApproval}, below.
     */
    private CapacityBlockExtensionStatus decide(final CapacityReservation reservation, final int durationHours,
                                                 final Long originId) {
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
     * Whether this request may reuse the approval of the extension it retries - only from a {@link
     * CapacityBlockExtensionStatus#FAILED} original belonging to the same reservation, and only when it asks for no
     * more than that original did: a smaller (or equal) ask is not a bigger commitment than an administrator
     * already approved once. Mirrors {@code CapacityReservationApprovalService#inheritsApproval}, but over the
     * extension's own retry chain rather than the reservation's.
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
     * Where the {@code CapacityReservationCloudFacade} call happens - today synchronous, so a request that
     * auto-approves is purchased inline rather than waiting for a monitor cycle. AWS gives that call no idempotency
     * token, and nothing here retries an uncertain outcome by checking what AWS actually holds first - an outcome
     * other than a definitive success or failure is still recorded as {@link CapacityBlockExtensionStatus#FAILED},
     * a risk left open for a caller to close once it needs to.
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
     * {@code durationHours} is overwritten with what the window AWS actually granted spans, not what was
     * requested, so the stored record reflects the capacity the block actually ended up with rather than what was
     * merely asked for.
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
     * AWS sells a single capacity block extension only in 1-day steps up to
     * {@value #MAX_ONE_DAY_STEP_TOTAL_HOURS} hours, then 7-day steps up to {@value #MAX_TOTAL_EXTENSION_HOURS}
     * hours - the longest a single extension can span. There is no limit on how many extensions a block can take
     * over its lifetime, so this looks only at {@code requestedHours} itself, not at anything the block has
     * already been extended by.
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
}
