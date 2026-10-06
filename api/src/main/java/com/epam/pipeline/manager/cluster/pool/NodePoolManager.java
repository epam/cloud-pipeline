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
import com.epam.pipeline.entity.cluster.AMIConfiguration;
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
import com.epam.pipeline.manager.security.acl.AclSync;
import com.epam.pipeline.mapper.cluster.pool.NodePoolMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@AclSync
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

    public List<NodePool> filter(final NodePoolFilterVO filter) {
        final LocalDateTime timestamp = DateUtils.nowUTC();
        return ListUtils.emptyIfNull(poolDao.loadAll()).stream()
                .filter(pool -> matches(filter.getInstanceType(), pool.getInstanceType()))
                .filter(pool -> matches(filter.getRegionId(), pool.getRegionId()))
                .filter(pool -> matches(filter.getPriceType(), pool.getPriceType()))
                .filter(pool -> matches(filter.getPoolType(), pool.getPoolType()))
                .filter(pool -> matches(filter.getIsCapacityReservation(), pool.isCapacityReservation()))
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
                .orElseThrow(() -> notFound(poolId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public NodePool loadForUpdate(final Long poolId) {
        return poolDao.findForUpdate(poolId)
                .orElseThrow(() -> notFound(poolId));
    }

    private IllegalArgumentException notFound(final Long poolId) {
        return new IllegalArgumentException(
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_NOT_FOUND, poolId));
    }

    @Transactional
    public NodePool applyReservationState(final Long poolId, final Consumer<NodePool> change) {
        final NodePool pool = loadForUpdate(poolId);
        change.accept(pool);
        return poolDao.updateReservationState(pool);
    }

    @Transactional
    public NodePool delete(final Long poolId) {
        final NodePool node = loadForUpdate(poolId);
        reservationService.onPoolDeletion(node);
        poolDao.delete(poolId);
        notificationManager.removeNotificationTimestamps(poolId, NotificationType.FULL_NODE_POOL);
        return node;
    }

    /**
     * Creates a pool, and - when the request carries one - the capacity reservation backing it.
     */
    @Transactional
    public NodePool create(final NodePoolVO vo) {
        validator.validate(vo);
        vo.setId(poolDao.createId());
        vo.setAmiConfiguration(resolveAmiConfiguration(vo, null));
        vo.setLaunchConfig(resolveLaunchConfig(vo, vo.getPoolType(), null));

        final NodePool nodePool = poolMapper.toEntity(vo);
        nodePool.setCreated(DateUtils.nowUTC());
        nodePool.setOwner(authManager.getAuthorizedUser());
        final CapacityReservationRequest request = vo.getCapacityReservationRequest();
        if (request != null) {
            nodePool.setCapacityReservation(true);
            nodePool.setCount(0);
        }
        final NodePool created = poolDao.create(nodePool);
        if (request != null) {
            reservationService.create(buildReservationRequest(request, created, vo));
        }
        return load(created.getId());
    }

    private CapacityReservationRequest buildReservationRequest(final CapacityReservationRequest request,
                                                               final NodePool pool,
                                                               final NodePoolVO vo) {
        return request.toBuilder()
                .nodePoolId(pool.getId())
                .name(pool.getName())
                .regionId(pool.getRegionId())
                .owner(pool.getOwner())
                .instanceType(pool.getInstanceType())
                .instanceCount(vo.getCount())
                .build();
    }

    @Transactional
    public NodePool update(final Long id, final NodePoolVO vo) {
        final NodePool existing = loadForUpdate(id);
        vo.setId(id);
        validator.validate(vo);
        if (existing.isCapacityReservation()) {
            validator.validateReservationPoolUpdate(existing, vo);
        }
        vo.setAmiConfiguration(resolveAmiConfiguration(vo, existing));
        vo.setLaunchConfig(resolveLaunchConfig(vo, existing.getPoolType(), existing.getLaunchConfig()));

        final NodePool updated = poolMapper.toEntity(vo);
        updated.setOwner(existing.getOwner());
        updated.setCapacityReservation(existing.isCapacityReservation());
        poolDao.update(updated);
        return load(id);
    }

    /**
     * The launch configuration a pool gets: the one the request sends, otherwise the pool's current one - none for a
     * new pool, whose nodes then launch as the region's {@code amis} rules say. The deprecated {@code instanceImage} is
     * its image when it names none, and an edit of that field changes the image.
     */
    @SuppressWarnings("deprecation")
    private AMIConfiguration resolveAmiConfiguration(final NodePoolVO vo, final NodePool existing) {
        final AMIConfiguration requested = vo.getAmiConfiguration();
        if (requested == null) {
            return existing == null ? null : withImageEdit(existing, vo.getInstanceImage());
        }
        if (StringUtils.isBlank(requested.getAmi()) && StringUtils.isNotBlank(vo.getInstanceImage())) {
            requested.setAmi(vo.getInstanceImage());
        }
        if (existing != null && requested.equals(existing.getAmiConfiguration())) {
            return requested;
        }
        validator.validateAmiConfiguration(vo.getRegionId(), requested);
        return requested;
    }

    @SuppressWarnings("deprecation")
    private static AMIConfiguration withImageEdit(final NodePool existing, final String instanceImage) {
        final AMIConfiguration configuration = existing.getAmiConfiguration();
        if (configuration != null && StringUtils.isNotBlank(instanceImage)
                && !StringUtils.equals(instanceImage, existing.getInstanceImage())) {
            configuration.setAmi(instanceImage);
        }
        return configuration;
    }

    /**
     * How runs divide a node of a sharable pool: the config the request sends; otherwise the pool's current one, or
     * one derived from its instance type. Other pools have none.
     */
    private NodePoolLaunchConfig resolveLaunchConfig(final NodePoolVO vo,
                                                     final NodePoolType poolType,
                                                     final NodePoolLaunchConfig current) {
        if (NodePoolType.SHARABLE_NODE != poolType) {
            return current;
        }
        if (vo.getLaunchConfig() == null && current != null) {
            return current;
        }
        return launchConfigBuilder.build(vo);
    }

    @Override
    public AclClass getSupportedClass() {
        return AclClass.NODE_POOL;
    }

    @Transactional
    @Override
    public AbstractSecuredEntity changeOwner(final Long id, final String owner) {
        final NodePool pool = loadForUpdate(id);
        pool.setOwner(owner);
        return poolDao.updateOwner(pool);
    }

    @Override
    public AbstractSecuredEntity loadByNameOrId(final String identifier) {
        return findByNameOrId(identifier)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_NOT_FOUND, identifier)));
    }

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
