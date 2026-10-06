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
import com.epam.pipeline.controller.vo.cluster.pool.CapacityReservationRequest;
import com.epam.pipeline.dao.cluster.capacityreservation.CapacityReservationDao;
import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class CapacityReservationService {

    private final CapacityReservationDao reservationDao;
    private final CapacityReservationApprovalService approvalService;
    private final CapacityReservationStatusService statusService;
    private final CapacityReservationCloudFacade cloudFacade;
    private final CloudRegionManager regionManager;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;

    @Autowired
    @Lazy
    private NodePoolManager poolManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public CapacityReservation create(final CapacityReservationRequest request) {
        final CapacityReservation reservation = build(request);
        final CapacityReservationStatus status = approvalService.evaluate(reservation);
        reservation.setStatus(status);

        final CapacityReservation created = reservationDao.create(reservation);
        log.debug("Created capacity reservation {} for node pool {} in status {}",
                created.getId(), request.getNodePoolId(), status);
        statusService.notifyCreated(created);
        return created;
    }

    public Optional<CapacityReservation> find(final Long id) {
        return reservationDao.find(id);
    }

    public Optional<CapacityReservation> findByNodePoolId(final Long nodePoolId) {
        return reservationDao.findByNodePoolId(nodePoolId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CapacityReservation loadForUpdate(final Long id) {
        poolManager.loadForUpdate(load(id).getNodePoolId());
        return load(id);
    }

    public CapacityReservation load(final Long id) {
        return reservationDao.find(id)
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_CAPACITY_RESERVATION_NOT_FOUND, id)));
    }

    public List<CapacityReservation> loadAll() {
        return reservationDao.loadAll();
    }

    public List<CapacityReservation> loadByOwner(final String owner) {
        return reservationDao.loadByOwner(owner);
    }

    public List<CapacityReservation> loadByStatus(final CapacityReservationStatus status) {
        return reservationDao.loadByStatus(status);
    }

    /**
     * Prepares the pool behind a scheduled reservation: the window it is usable in, and the reservation's target,
     * zone and subnet in its launch configuration. The pool's count stays 0.
     */
    @Transactional
    public void schedulePool(final CapacityReservation reservation) {
        final Map<String, Object> launchSpecification = launchSpecificationOf(reservation);
        final String zone = reservation.getAvailabilityZone();
        final String subnet = allowedNetworks(regionCodeOf(reservation)).get(zone);
        poolManager.applyReservationState(reservation.getNodePoolId(), pool -> {
            pool.setStartDate(reservation.getStartDate());
            pool.setEndDate(reservation.getEndDate());
            pool.setAmiConfiguration(withReservation(pool.getAmiConfiguration(), launchSpecification, zone, subnet));
        });
        log.debug("Prepared node pool {} for capacity reservation {} from {} to {}",
                reservation.getNodePoolId(), reservation.getId(), reservation.getStartDate(), reservation.getEndDate());
    }

    @Transactional
    public void activatePool(final CapacityReservation reservation) {
        poolManager.applyReservationState(reservation.getNodePoolId(),
            pool -> pool.setCount(reservation.getInstanceCount()));
        log.debug("Activated node pool {} with {} nodes for capacity reservation {}",
                reservation.getNodePoolId(), reservation.getInstanceCount(), reservation.getId());
    }

    @Transactional
    public void deactivatePool(final CapacityReservation reservation) {
        final Set<String> reservationKeys = launchSpecificationOf(reservation).keySet();
        poolManager.applyReservationState(reservation.getNodePoolId(), pool -> {
            pool.setCount(0);
            pool.setAmiConfiguration(withoutReservation(pool.getAmiConfiguration(), reservationKeys));
        });
        log.debug("Deactivated node pool {} after capacity reservation {} ended",
                reservation.getNodePoolId(), reservation.getId());
    }

    @Transactional
    public CapacityReservation approve(final Long id) {
        return statusService.transition(loadForUpdate(id), CapacityReservationStatus.APPROVED, null);
    }

    @Transactional
    public CapacityReservation cancel(final Long id) {
        final CapacityReservation reservation = loadForUpdate(id);
        cloudFacade.cancel(reservation);
        final CapacityReservation cancelled =
                statusService.transition(reservation, CapacityReservationStatus.CANCELLED, null);
        deactivatePool(cancelled);
        return cancelled;
    }

    private Map<String, Object> launchSpecificationOf(final CapacityReservation reservation) {
        return MapUtils.emptyIfNull(cloudFacade.launchSpecification(reservation));
    }

    private String regionCodeOf(final CapacityReservation reservation) {
        return regionManager.load(reservation.getRegionId()).getRegionCode();
    }

    private static AMIConfiguration withReservation(final AMIConfiguration configuration,
                                                    final Map<String, Object> launchSpecification,
                                                    final String zone, final String subnet) {
        final AMIConfiguration updated = Optional.ofNullable(configuration).orElseGet(AMIConfiguration::new);
        final Map<String, Object> spec = new HashMap<>(MapUtils.emptyIfNull(updated.getAdditionalSpec()));
        spec.putAll(launchSpecification);
        updated.setAdditionalSpec(spec.isEmpty() ? null : spec);
        updated.setAvailabilityZone(zone);
        updated.setSubnet(subnet);
        return updated;
    }

    private static AMIConfiguration withoutReservation(final AMIConfiguration configuration,
                                                       final Set<String> reservationKeys) {
        if (configuration == null) {
            return null;
        }
        final Map<String, Object> spec = new HashMap<>(MapUtils.emptyIfNull(configuration.getAdditionalSpec()));
        reservationKeys.forEach(spec::remove);
        configuration.setAdditionalSpec(spec.isEmpty() ? null : spec);
        configuration.setAvailabilityZone(null);
        configuration.setSubnet(null);
        return configuration;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onPoolDeletion(final NodePool pool) {
        if (!pool.isCapacityReservation()) {
            return;
        }
        final CapacityReservation reservation = findByNodePoolId(pool.getId()).orElse(null);
        if (reservation == null) {
            return;
        }
        if (!reservation.getStatus().isTerminal()) {
            throw new IllegalStateException(messageHelper.getMessage(
                    MessageConstants.ERROR_CAPACITY_RESERVATION_NOT_TERMINAL,
                    pool.getId(), reservation.getStatus()));
        }
        reservationDao.delete(reservation.getId());
    }

    private CapacityReservation build(final CapacityReservationRequest request) {
        final AbstractCloudRegion region = regionManager.load(request.getRegionId());
        return CapacityReservation.builder()
                .nodePoolId(request.getNodePoolId())
                .originId(request.getOriginId())
                .name(request.getName())
                .regionId(request.getRegionId())
                .cloudProvider(region.getProvider())
                .reservationType(request.getReservationType())
                .owner(request.getOwner())
                .created(DateUtils.nowUTC())
                .updated(DateUtils.nowUTC())
                .instanceType(request.getInstanceType())
                .instanceCount(request.getInstanceCount())
                .requestedStartDate(request.getRequestedStartDate())
                .requestedEndDate(request.getRequestedEndDate())
                .durationHours(request.getDurationHours())
                .instancePlatform(StringUtils.defaultIfBlank(request.getInstancePlatform(),
                        CapacityReservation.DEFAULT_INSTANCE_PLATFORM))
                .build();
    }

    private Map<String, String> allowedNetworks(final String regionCode) {
        return Optional.ofNullable(preferenceManager.getPreference(SystemPreferences.CLUSTER_NETWORKS_CONFIG))
                .map(configuration -> configuration.allowedNetworks(regionCode))
                .orElseGet(Collections::emptyMap);
    }
}
