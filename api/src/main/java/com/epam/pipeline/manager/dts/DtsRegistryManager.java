/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.dts;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.dts.DtsRegistryPreferencesRemovalVO;
import com.epam.pipeline.controller.vo.dts.DtsRegistryPreferencesUpdateVO;
import com.epam.pipeline.controller.vo.dts.DtsRegistryVO;
import com.epam.pipeline.dao.dts.DtsRegistryDao;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.entity.dts.DtsStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.DtsRegistryMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link DtsRegistryManager} provides CRUD methods for Data Transfer Service registry.
 */
@Service
@RequiredArgsConstructor
public class DtsRegistryManager {

    private static final String DTS_TUNNEL_ENABLED_KEY = "dts.tunnel.enabled";
    private static final String DTS_TUNNEL_HOST_KEY = "dts.tunnel.host";
    private static final String DTS_TUNNEL_INPUT_PORT_KEY = "dts.tunnel.input.port";
    private static final String DTS_TUNNEL_OUTPUT_PORT_KEY = "dts.tunnel.output.port";
    private static final String FALLBACK_DTS_TUNNEL_URL_TEMPLATE = "http://%s:%s/dts/restapi";
    private static final String TRUE_STRING = "true";

    private final DtsRegistryDao dtsRegistryDao;
    private final DtsRegistryMapper dtsRegistryMapper;
    private final DtsTunnelResolver dtsTunnelResolver;
    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;

    /**
     * Loads all existing {@link DtsRegistry}s.
     *
     * @return list of existing {@link DtsRegistry}s
     */
    public List<DtsRegistry> loadAll() {
        return dtsRegistryDao.loadAll();
    }

    public DtsRegistry loadByNameOrId(String registryId) {
        if (NumberUtils.isDigits(registryId)) {
            return loadById(Long.parseLong(registryId));
        } else {
            return loadByName(registryId);
        }
    }

    /**
     * Loads {@link DtsRegistry} specified by ID.
     *
     * @param registryId a {@link DtsRegistry} ID
     * @return existing {@link DtsRegistry} or error if required registry does not exist.
     */
    public DtsRegistry loadById(Long registryId) {
        validateDtsRegistryId(registryId);
        return loadOrThrow(registryId);
    }

    /**
     * Loads {@link DtsRegistry} specified by name.
     *
     * @param registryName a {@link DtsRegistry} name
     * @return existing {@link DtsRegistry} or error if required registry does not exist.
     */
    public DtsRegistry loadByName(final String registryName) {
        Assert.state(StringUtils.isNotBlank(registryName),
                     messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_NAME_IS_EMPTY));
        return dtsRegistryDao.loadByName(registryName)
            .orElseThrow(() -> new IllegalArgumentException(
                messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_NAME_DOES_NOT_EXIST, registryName)));
    }

    /**
     * Creates a new {@link DtsRegistry}.
     *
     * @param dtsRegistryVO a {@link DtsRegistryVO} to create
     * @return created {@link DtsRegistry}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public DtsRegistry create(DtsRegistryVO dtsRegistryVO) {
        validateDtsRegistryVO(dtsRegistryVO);
        validateDtsRegistryDoesNotExist(dtsRegistryVO.getName());
        DtsRegistry dtsRegistry = dtsRegistryMapper.toDtsRegistry(dtsRegistryVO);
        dtsRegistry.setStatus(DtsStatus.OFFLINE);
        return dtsRegistryDao.create(StringUtils.isBlank(dtsRegistryVO.getUrl())
                ? withTunnelUrl(dtsRegistry)
                : dtsRegistry);
    }

    private DtsRegistry withTunnelUrl(final DtsRegistry dts) {
        return dtsTunnelResolver.resolve()
                .map(address -> withTunnelUrl(dts, address))
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_DTS_REGISTRY_TUNNEL_URL_UNAVAILABLE)));
    }

    private DtsRegistry withTunnelUrl(final DtsRegistry dts, final DtsTunnelAddress address) {
        dts.setUrl(getTunnelUrl(address));
        dts.setPreferences(getTunnelPreferences(address));
        return dts;
    }

    private String getTunnelUrl(final DtsTunnelAddress address) {
        return String.format(getTunnelUrlTemplate(), address.getHost(), address.getInputPort());
    }

    private String getTunnelUrlTemplate() {
        return getSystemPreference(SystemPreferences.DTS_TUNNEL_URL_TEMPLATE)
                .orElse(FALLBACK_DTS_TUNNEL_URL_TEMPLATE);
    }

    private Map<String, String> getTunnelPreferences(final DtsTunnelAddress address) {
        final Map<String, String> preferences = new HashMap<>();
        preferences.put(DTS_TUNNEL_ENABLED_KEY, TRUE_STRING);
        preferences.put(DTS_TUNNEL_HOST_KEY, address.getHost());
        preferences.put(DTS_TUNNEL_INPUT_PORT_KEY, address.getInputPort().toString());
        preferences.put(DTS_TUNNEL_OUTPUT_PORT_KEY, address.getOutputPort().toString());
        return preferences;
    }

    private <T> Optional<T> getSystemPreference(final AbstractSystemPreference<T> preference) {
        return Optional.of(preference).map(preferenceManager::getPreference);
    }

    /**
     * Updates a {@link DtsRegistry} specified by ID.
     *
     * If required {@link DtsRegistry} does not exist an error will be thrown.
     *
     * @param registryId a {@link DtsRegistry} ID to update
     * @param dtsRegistryVO a {@link DtsRegistryVO} to update
     * @return updated {@link DtsRegistry}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public DtsRegistry update(final String registryId, final DtsRegistryVO dtsRegistryVO) {
        final DtsRegistry originalDtsRegistry = loadByNameOrId(registryId);
        validateDtsRegistryVO(dtsRegistryVO);
        final DtsRegistry dtsRegistry = dtsRegistryMapper.toDtsRegistry(dtsRegistryVO);
        dtsRegistry.setId(originalDtsRegistry.getId());
        dtsRegistryDao.update(dtsRegistry);
        return loadById(dtsRegistry.getId());
    }

    /**
     * Updates a {@link DtsRegistry} heartbeat specified by ID.
     *
     * If required {@link DtsRegistry} does not exist an error will be thrown.
     *
     * @param registryId a {@link DtsRegistry} ID to update heartbeat
     * @return updated {@link DtsRegistry}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public DtsRegistry updateHeartbeat(final String registryId) {
        final DtsRegistry dtsRegistry = loadByNameOrId(registryId);
        dtsRegistry.setHeartbeat(DateUtils.nowUTC());
        dtsRegistry.setStatus(DtsStatus.ONLINE);
        dtsRegistryDao.updateHeartbeat(dtsRegistry.getId(), dtsRegistry.getHeartbeat(), dtsRegistry.getStatus());
        return dtsRegistry;
    }

    /**
     * Updates a {@link DtsRegistry} status specified by ID.
     *
     * If required {@link DtsRegistry} does not exist an error will be thrown.
     *
     * @param registryId a {@link DtsRegistry} ID to update heartbeat
     * @param status a {@link DtsRegistry} status to set
     * @return updated {@link DtsRegistry}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public DtsRegistry updateStatus(final Long registryId, final DtsStatus status) {
        final DtsRegistry dtsRegistry = loadById(registryId);
        dtsRegistry.setStatus(status);
        dtsRegistryDao.updateStatus(dtsRegistry.getId(), dtsRegistry.getStatus());
        return dtsRegistry;
    }

    /**
     * Deletes a {@link DtsRegistry} specified by ID.
     *
     * If required {@link DtsRegistry} does not exist an error will be thrown.
     *
     * @param registryId a {@link DtsRegistry} ID to delete
     * @return deleted {@link DtsRegistry}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public DtsRegistry delete(final String registryId) {
        final DtsRegistry dtsRegistry = loadByNameOrId(registryId);
        dtsRegistryDao.delete(dtsRegistry.getId());
        return dtsRegistry;
    }

    /**
     * Creates new or updates existing preferences in a {@link DtsRegistry} specified by ID or name.
     *
     * If required {@link DtsRegistry} does not exist an error will be thrown.
     *
     * @param registryId a {@link DtsRegistry} ID or name of a registry to update
     * @param preferencesVO preferences, that need to be set for a registry
     * @return updated {@link DtsRegistry}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public DtsRegistry upsertPreferences(final String registryId, final DtsRegistryPreferencesUpdateVO preferencesVO) {
        final DtsRegistry dtsRegistry = loadByNameOrId(registryId);
        final Map<String, String> preferencesToUpdate = preferencesVO.getPreferencesToUpdate();
        Assert.isTrue(MapUtils.isNotEmpty(preferencesToUpdate),
                      messageHelper.getMessage(MessageConstants.ERROR_DTS_PREFERENCES_UPDATE_EMPTY));
        dtsRegistryDao.upsertPreferences(dtsRegistry.getId(), preferencesToUpdate);
        dtsRegistry.getPreferences().putAll(preferencesToUpdate);
        return dtsRegistry;
    }

    /**
     * Removes preferences in a {@link DtsRegistry} specified by ID or name.
     *
     * If required {@link DtsRegistry} does not exist an error will be thrown.
     * If any key specified for removal, is not presented in a registry's preferences, an error will be thrown.
     *
     * @param registryId a {@link DtsRegistry} ID or name of a registry to update
     * @param preferencesVO list of keys indicating which preferences need to be removed from a registry
     * @return updated {@link DtsRegistry}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public DtsRegistry deletePreferences(final String registryId, final DtsRegistryPreferencesRemovalVO preferencesVO) {
        final DtsRegistry dtsRegistry = loadByNameOrId(registryId);
        final List<String> keysToRemove = preferencesVO.getPreferenceKeysToRemove();
        Assert.isTrue(CollectionUtils.isNotEmpty(keysToRemove),
                      messageHelper.getMessage(MessageConstants.ERROR_DTS_PREFERENCES_DELETE_EMPTY));
        final Set<String> existingKeys = dtsRegistry.getPreferences().keySet();
        final String listOfNonExistentPreferences = keysToRemove.stream()
            .filter(preference -> !existingKeys.contains(preference))
            .collect(Collectors.joining(","));
        Assert.isTrue(StringUtils.isEmpty(listOfNonExistentPreferences), messageHelper
            .getMessage(MessageConstants.ERROR_DTS_PREFERENCES_DOESNT_EXIST, registryId, listOfNonExistentPreferences));
        dtsRegistryDao.deletePreferences(dtsRegistry.getId(), keysToRemove);
        existingKeys.removeAll(keysToRemove);
        return dtsRegistry;
    }

    private DtsRegistry loadOrThrow(Long registryId) {
        return dtsRegistryDao.loadById(registryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_ID_DOES_NOT_EXIST, registryId)));
    }

    private void validateDtsRegistryVO(DtsRegistryVO dtsRegistryVO) {
        Assert.notNull(dtsRegistryVO, messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_IS_EMPTY));
        Assert.state(CollectionUtils.isNotEmpty(dtsRegistryVO.getPrefixes()),
                messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_PREFIXES_ARE_EMPTY));
        final String dtsName = dtsRegistryVO.getName();
        Assert.state(StringUtils.isNotBlank(dtsName),
                     messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_NAME_IS_EMPTY));
        Assert.state(!NumberUtils.isDigits(dtsName),
                     messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_NAME_CONSIST_OF_NUMBERS));
    }

    private void validateDtsRegistryId(Long registryId) {
        Assert.notNull(registryId, messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_ID_IS_EMPTY));
    }

    private void validateDtsRegistryDoesNotExist(final String registryId) {
        Assert.isTrue(!dtsRegistryDao.loadByName(registryId).isPresent(),
                messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_NAME_ALREADY_EXISTS, registryId));
    }
}
