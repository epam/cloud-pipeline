/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cluster.pool;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.cluster.pool.CapacityReservationRequest;
import com.epam.pipeline.controller.vo.cluster.pool.NodePoolFilterVO;
import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.dao.cluster.pool.NodePoolDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.NodePoolLaunchConfig;
import com.epam.pipeline.entity.cluster.pool.NodePoolType;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.capacityreservation.CapacityReservationService;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import com.epam.pipeline.mapper.cluster.pool.NodePoolMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NodePoolManager implements SecuredEntityManager {

    @Autowired
    private NodePoolDao poolDao;

    @Autowired
    private NodePoolMapper poolMapper;

    @Autowired
    private NodePoolValidator validator;

    @Autowired
    private NotificationManager notificationManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private KubernetesPoolService kubernetesPoolService;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private CapacityReservationService reservationService;

    @Autowired
    private NodePoolLaunchConfigBuilder launchConfigBuilder;

    public List<NodePool> getActivePools() {
        final LocalDateTime timestamp = DateUtils.nowUTC();
        return ListUtils.emptyIfNull(poolDao.loadAll())
                .stream()
                .filter(pool -> pool.isActive(timestamp))
                .collect(Collectors.toList());
    }


    public List<NodePool> loadAll(final boolean loadStatus) {
        final List<NodePool> allPools = poolDao.loadAll();
        return loadStatus && CollectionUtils.isNotEmpty(allPools)
                ? kubernetesPoolService.attachUsage(allPools)
                : allPools;
    }

    /**
     * The pools matching a filter. Unset fields are ignored, so an empty filter returns everything the caller may
     * see - the authorization layer, not this method, decides what that is.
     */
    public List<NodePool> filter(final NodePoolFilterVO filter) {
        final LocalDateTime timestamp = DateUtils.nowUTC();
        return ListUtils.emptyIfNull(poolDao.loadAll()).stream()
                .filter(pool -> matches(filter.getInstanceType(), pool.getInstanceType()))
                .filter(pool -> matches(filter.getRegionId(), pool.getRegionId()))
                .filter(pool -> matches(filter.getPriceType(), pool.getPriceType()))
                .filter(pool -> matches(filter.getPoolType(), pool.getPoolType()))
                .filter(pool -> matches(filter.getCapacityReservation(), pool.isCapacityReservation()))
                .filter(pool -> filter.getActive() == null
                        || filter.getActive() == pool.isActive(timestamp))
                .collect(Collectors.toList());
    }

    private static boolean matches(final Object requested, final Object actual) {
        return requested == null || requested.equals(actual);
    }

    public Optional<NodePool> find(final Long poolId) {
        return poolDao.find(poolId);
    }

    public NodePool load(final Long poolId) {
        return find(poolId)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_NOT_FOUND, poolId)));
    }

    /**
     * Persists the columns the reservation lifecycle owns - the node count and the window it is usable in, plus
     * the kube labels its pods select on.
     *
     * <p>Separate from {@link #update(Long, NodePoolVO)} on purpose: that path is driven by a caller's VO and
     * validates it, neither of which applies to the monitor writing back what a provider decided.
     */
    @Transactional
    public NodePool applyReservationState(final NodePool pool) {
        poolDao.update(pool);
        return pool;
    }

    private Long reservationIdOf(final NodePool pool) {
        return reservationService.findByNodePoolId(pool.getId())
                .map(CapacityReservation::getId)
                .orElse(null);
    }

    @Transactional
    public NodePool delete(final Long poolId) {
        final NodePool node = load(poolId);
        // Refuses the delete while a reservation is still live, and removes a terminal one in this same
        // transaction - the reservation row cannot outlive the pool it points at.
        reservationService.onPoolDeletion(node);
        poolDao.delete(poolId);
        notificationManager.removeNotificationTimestamps(poolId, NotificationType.FULL_NODE_POOL);
        return node;
    }

    /**
     * Creates a pool, and - when the request carries one - the capacity reservation backing it.
     *
     * <p>Both rows are written in one transaction, so a pool is never left claiming to be reservation-backed
     * with no reservation to show. The pool is persisted with {@code count = 0}: it exists and is visible
     * immediately, but is inert until its reservation becomes active, which is what makes the reservation's
     * multi-day approval and procurement invisible to the caller. Nothing here contacts a cloud provider.
     */
    @Transactional
    public NodePool create(final NodePoolVO vo) {
        validator.validate(vo);
        final NodePool nodePool = poolMapper.toEntity(vo);
        nodePool.setCreated(DateUtils.nowUTC());
        // Every pool gets an owner, reservation-backed or not: a pool is a secured entity now, and parts of
        // the permission layer dereference the owner without a null check. Leaving it null here would
        // reintroduce, for every new pool, exactly the state this change's migration backfills away.
        nodePool.setOwner(authManager.getAuthorizedUser());

        final CapacityReservationRequest request = vo.getCapacityReservationRequest();
        if (request != null) {
            nodePool.setCapacityReservation(true);
            nodePool.setCount(0);
        }

        final NodePool created = poolDao.create(nodePool);
        if (request != null) {
            final CapacityReservation reservation = reservationService.create(created, vo, request);
            applyLaunchConfig(created, vo.getLaunchConfig(), reservation.getId());
        }
        return load(created.getId());
    }

    /**
     * Writes a sharable pool's launch config, once the reservation exists.
     *
     * <p>It has to be a second statement rather than part of the insert: the config's pod selector is keyed on
     * the reservation's id, and the reservation cannot be created until the pool it points at has one. Both
     * writes are in the caller's transaction, so a pool is never visible as sharable without its config.
     */
    private void applyLaunchConfig(final NodePool pool,
                                   final NodePoolLaunchConfig requested,
                                   final Long reservationId) {
        if (NodePoolType.SHARABLE_NODE != pool.getPoolType()) {
            return;
        }
        pool.setLaunchConfig(launchConfigBuilder.build(pool, requested, reservationId));
        poolDao.updateLaunchConfig(pool);
    }

    /**
     * @param id the pool being updated, taken from the path rather than the payload so that authorization
     *           cannot be bypassed by pointing the body at a different pool
     */
    @Transactional
    public NodePool update(final Long id, final NodePoolVO vo) {
        final NodePool existing = load(id);
        vo.setId(id);
        validator.validate(vo);
        reservationService.assertPoolEditable(existing, vo);

        final NodePool updated = poolMapper.toEntity(vo);
        // Neither of these is writable through an ordinary edit: ownership changes through the permission
        // layer, and a pool cannot gain or lose a reservation after it is created.
        updated.setOwner(existing.getOwner());
        updated.setCapacityReservation(existing.isCapacityReservation());
        poolDao.update(updated);

        // Replaced only when the caller actually sent one, so an edit that says nothing about the launch config
        // leaves it alone rather than clearing it.
        if (vo.getLaunchConfig() != null) {
            applyLaunchConfig(existing, vo.getLaunchConfig(), reservationIdOf(existing));
        }
        return load(id);
    }

    @Override
    public AclClass getSupportedClass() {
        return AclClass.NODE_POOL;
    }

    @Transactional
    @Override
    public AbstractSecuredEntity changeOwner(final Long id, final String owner) {
        final NodePool pool = load(id);
        pool.setOwner(owner);
        return poolDao.updateOwner(pool);
    }

    @Override
    public AbstractSecuredEntity loadByNameOrId(final String identifier) {
        return findByNameOrId(identifier)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_NOT_FOUND, identifier)));
    }

    /**
     * A node pool is neither hierarchical nor browsable through the entity tree, so the three methods below
     * have nothing meaningful to answer - matching how {@code CloudRegionManager} treats them for the
     * platform's other root-level secured entity.
     */
    @Override
    public Integer loadTotalCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends AbstractSecuredEntity> loadAllWithParents(final Integer page,
                                                                          final Integer pageSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AbstractSecuredEntity loadWithParents(final Long id) {
        throw new UnsupportedOperationException();
    }

    private Optional<NodePool> findByNameOrId(final String identifier) {
        if (NumberUtils.isDigits(identifier)) {
            return find(Long.parseLong(identifier));
        }
        return ListUtils.emptyIfNull(poolDao.loadAll()).stream()
                .filter(pool -> identifier.equals(pool.getName()))
                .findFirst();
    }
}
