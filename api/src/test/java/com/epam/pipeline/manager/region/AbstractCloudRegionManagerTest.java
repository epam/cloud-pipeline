/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.region;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.vo.region.AbstractCloudRegionDTO;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.mapper.region.CloudRegionMapper;
import org.junit.Before;
import org.junit.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomMatchers.isEmpty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("PMD.TooManyStaticImports")
public abstract class AbstractCloudRegionManagerTest {

    static final long ID = 1L;
    static final String REGION_NAME = "name";

    final CloudRegionMapper cloudRegionMapper = Mappers.getMapper(CloudRegionMapper.class);
    final CloudRegionDao cloudRegionDao = mock(CloudRegionDao.class);
    final MessageHelper messageHelper = mock(MessageHelper.class);
    final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    final AuthManager authManager = mock(AuthManager.class);
    final FileShareMountManager fileShareMountManager = mock(FileShareMountManager.class);
    final CloudRegionManager cloudRegionManager =
            new CloudRegionManager(cloudRegionDao, cloudRegionMapper, fileShareMountManager,
                    messageHelper, preferenceManager, authManager, helpers());

    @Before
    public void initJsonMapper() {
        new JsonMapper().init();
    }

    @Before
    public void initDefaultPreferences() {
        doReturn(defaultProvider().name())
                .when(preferenceManager).getPreference(SystemPreferences.CLOUD_DEFAULT_PROVIDER);
    }

    @Before
    public void initCommonRegion() {
        doReturn(Optional.of(commonRegion())).when(cloudRegionDao).loadById(ID);
        doReturn(Optional.ofNullable(credentials())).when(cloudRegionDao).loadCredentials(ID);
    }

    @Test
    public void loadByIdShouldRetrieveRegionFromDao() {
        final AbstractCloudRegion actualRegion = cloudRegionManager.load(ID);
        assertRegionEquals(commonRegion(), actualRegion);
    }

    @Test
    public void loadByIdShouldThrowIfThereIsNoRegionInDao() {
        doReturn(Optional.empty()).when(cloudRegionDao).loadById(eq(ID));

        assertThrows(IllegalArgumentException.class, () -> cloudRegionManager.load(ID));
    }

    @Test
    public void loadAllShouldReturnEmptyListIfThereIsNoEntitiesInDao() {
        doReturn(Collections.emptyList()).when(cloudRegionDao).loadAll();

        assertThat(cloudRegionManager.loadAll(), isEmpty());
    }

    @Test
    public void loadAllShouldReturnAllEntitiesFromDao() {
        final AbstractCloudRegion firstRegion = commonRegion();
        final AbstractCloudRegion secondRegion = commonRegion();
        doReturn(Arrays.asList(firstRegion, secondRegion)).when(cloudRegionDao).loadAll();
        assertThat(cloudRegionManager.loadAll(), containsInAnyOrder(firstRegion, secondRegion));
    }

    @Test
    public void updateShouldThrowIfEntityDoesNotExist() {
        doReturn(Optional.empty()).when(cloudRegionDao).loadById(ID);

        assertThrows(IllegalArgumentException.class,
            () -> cloudRegionManager.update(ID, createRegionDTO()));
    }

    @Test
    public void updateShouldThrowIfNameIsMissing() {
        assertThrows(IllegalArgumentException.class,
            () -> {
                final AbstractCloudRegionDTO regionDTO = updateRegionDTO();
                regionDTO.setName(null);
                cloudRegionManager.update(ID, regionDTO);
            });
    }

    @Test
    public void updateShouldSaveRegionIdFromTheOldValue() {
        final AbstractCloudRegionDTO regionDTO = updateRegionDTO();
        regionDTO.setRegionCode("another region id");
        cloudRegionManager.update(ID, regionDTO);

        final ArgumentCaptor<AbstractCloudRegion> regionCaptor = ArgumentCaptor.forClass(AbstractCloudRegion.class);
        verify(cloudRegionDao).update(regionCaptor.capture(), eq(credentials()));
        assertThat(regionCaptor.getValue().getRegionCode(), is(validRegionId()));
    }

    @Test
    public void updateShouldSaveProviderFromTheOldValue() {
        final AbstractCloudRegionDTO regionDTO = updateRegionDTO();
        regionDTO.setProvider(null);
        cloudRegionManager.update(ID, regionDTO);

        final ArgumentCaptor<AbstractCloudRegion> regionCaptor = ArgumentCaptor.forClass(AbstractCloudRegion.class);
        verify(cloudRegionDao).update(regionCaptor.capture(), eq(credentials()));
        assertThat(regionCaptor.getValue().getProvider(), is(defaultProvider()));
    }

    @Test
    public void updateShouldSaveCreatedDateFromTheOldValue() {
        final Date createdDate = new Date();
        final AbstractCloudRegion originalRegion = commonRegion();
        originalRegion.setCreatedDate(createdDate);
        doReturn(Optional.of(originalRegion)).when(cloudRegionDao).loadById(ID);
        cloudRegionManager.update(ID, updateRegionDTO());

        final ArgumentCaptor<AbstractCloudRegion> regionCaptor = ArgumentCaptor.forClass(AbstractCloudRegion.class);
        verify(cloudRegionDao).update(regionCaptor.capture(), eq(credentials()));
        final AbstractCloudRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getCreatedDate(), is(createdDate));
    }

    @Test
    public void updateShouldSaveOwnerFromTheOldValue() {
        final String ownerUserName = "ownerUserName";
        final AbstractCloudRegion originalRegion = commonRegion();
        originalRegion.setOwner(ownerUserName);
        doReturn(Optional.of(originalRegion)).when(cloudRegionDao).loadById(ID);
        cloudRegionManager.update(ID, updateRegionDTO());

        final ArgumentCaptor<AbstractCloudRegion> regionCaptor = ArgumentCaptor.forClass(AbstractCloudRegion.class);
        verify(cloudRegionDao).update(regionCaptor.capture(), eq(credentials()));
        final AbstractCloudRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getOwner(), is(ownerUserName));
    }

    @Test
    public void createShouldThrowIfNameIsMissing() {
        assertThrows(IllegalArgumentException.class,
            () -> {
                final AbstractCloudRegionDTO regionDTO = createRegionDTO();
                regionDTO.setName(null);
                cloudRegionManager.create(regionDTO);
            });
    }

    @Test
    public void createShouldThrowIfRegionIdIsMissing() {
        assertThrows(IllegalArgumentException.class,
            () -> {
                final AbstractCloudRegionDTO regionDTO = createRegionDTO();
                regionDTO.setRegionCode(null);
                cloudRegionManager.create(regionDTO);
            });
    }

    @Test
    public void createShouldThrowIfRegionIdIsInvalid() {
        assertThrows(IllegalArgumentException.class,
            () -> {
                final AbstractCloudRegionDTO regionDTO = createRegionDTO();
                regionDTO.setRegionCode("invalid");
                cloudRegionManager.create(regionDTO);
            });
    }

    @Test
    public void createShouldSetCreatedDateForCloudRegion() {
        cloudRegionManager.create(createRegionDTO());

        final ArgumentCaptor<AbstractCloudRegion> regionCaptor = ArgumentCaptor.forClass(AbstractCloudRegion.class);
        verify(cloudRegionDao).create(regionCaptor.capture(), eq(credentials()));
        assertNotNull(regionCaptor.getValue().getCreatedDate());
    }

    @Test
    public void createShouldSetOwnerForCloudRegion() {
        final String ownerUserName = "ownerUserName";
        doReturn(ownerUserName).when(authManager).getAuthorizedUser();
        cloudRegionManager.create(createRegionDTO());

        final ArgumentCaptor<AbstractCloudRegion> regionCaptor = ArgumentCaptor.forClass(AbstractCloudRegion.class);
        verify(cloudRegionDao).create(regionCaptor.capture(), eq(credentials()));
        assertThat(regionCaptor.getValue().getOwner(), is(ownerUserName));
    }

    @Test
    public void deleteShouldThrowIfEntityDoesNotExist() {
        doReturn(Optional.empty()).when(cloudRegionDao).loadById(ID);

        assertThrows(IllegalArgumentException.class,
            () -> cloudRegionManager.delete(ID));
    }

    @Test
    public void deleteShouldRemoveEntityFromDao() {
        cloudRegionManager.delete(ID);

        verify(cloudRegionDao).delete(ID);
    }

    abstract AbstractCloudRegion commonRegion();

    abstract AbstractCloudRegionDTO createRegionDTO();

    abstract AbstractCloudRegionDTO updateRegionDTO();

    abstract AbstractCloudRegionCredentials credentials();

    abstract String validRegionId();

    /**
     * The assert should ignore {@link AbstractCloudRegion#getId()} field.
     */
    abstract void assertRegionEquals(AbstractCloudRegion expectedRegion, AbstractCloudRegion actualRegion);

    abstract List<CloudRegionHelper> helpers();

    abstract CloudProvider defaultProvider();

}
