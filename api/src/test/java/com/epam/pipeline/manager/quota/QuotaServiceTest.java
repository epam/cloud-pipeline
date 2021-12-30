/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.quota;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.quota.Quota;
import com.epam.pipeline.dto.quota.QuotaActionType;
import com.epam.pipeline.dto.quota.QuotaGroup;
import com.epam.pipeline.dto.quota.QuotaPeriod;
import com.epam.pipeline.dto.quota.QuotaType;
import com.epam.pipeline.entity.quota.QuotaActionEntity;
import com.epam.pipeline.entity.quota.QuotaEntity;
import com.epam.pipeline.mapper.quota.QuotaMapper;
import com.epam.pipeline.repository.quota.QuotaActionRepository;
import com.epam.pipeline.repository.quota.QuotaRepository;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.quota.QuotaCreatorsUtils.quota;
import static com.epam.pipeline.test.creator.quota.QuotaCreatorsUtils.quotaActionEntity;
import static com.epam.pipeline.test.creator.quota.QuotaCreatorsUtils.quotaEntity;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomAssertions.notInvoked;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class QuotaServiceTest {
    private final QuotaRepository quotaRepository = mock(QuotaRepository.class);
    private final QuotaActionRepository quotaActionRepository = mock(QuotaActionRepository.class);
    private final QuotaMapper quotaMapper = mock(QuotaMapper.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final QuotaService quotaService = new QuotaService(quotaRepository, quotaActionRepository, quotaMapper,
            messageHelper);

    @Test
    public void shouldFailCreateIfQuotaGroupNotSpecified() {
        final Quota quota = quota(null);
        quota.setQuotaGroup(null);
        assertThrows(IllegalArgumentException.class, () -> quotaService.create(quota));
    }

    @Test
    public void shouldFailCreateIfQuotaValueNotSpecified() {
        final Quota quota = quota(null);
        quota.setValue(null);
        assertThrows(IllegalArgumentException.class, () -> quotaService.create(quota));
    }

    @Test
    public void shouldFailCreateIfQuotaTypeNotSpecifiedForNonGlobalGroup() {
        final Quota quota = quota(null);
        quota.setType(null);
        assertThrows(IllegalArgumentException.class, () -> quotaService.create(quota));
    }

    @Test
    public void shouldFailCreateIfQuotaSubjectNotSpecifiedForNonGlobalGroup() {
        final Quota quota = quota(null);
        quota.setSubject(null);
        assertThrows(IllegalStateException.class, () -> quotaService.create(quota));
    }

    @Test
    public void shouldFailCreateIfGlobalQuotaAlreadyExists() {
        final Quota quota = quota(null);
        quota.setQuotaGroup(QuotaGroup.GLOBAL);
        doReturn(quotaEntity(null)).when(quotaRepository).findByQuotaGroup(QuotaGroup.GLOBAL);
        assertThrows(IllegalArgumentException.class, () -> quotaService.create(quota));
    }

    @Test
    public void shouldFailCreateIfOverallQuotaAlreadyExists() {
        final Quota quota = quota(null);
        quota.setQuotaGroup(QuotaGroup.STORAGE);
        quota.setType(QuotaType.OVERALL);
        doReturn(quotaEntity(null)).when(quotaRepository)
                .findByQuotaGroupAndType(QuotaGroup.STORAGE, QuotaType.OVERALL);
        assertThrows(IllegalArgumentException.class, () -> quotaService.create(quota));
    }

    @Test
    public void shouldFailCreateIfSuchQuotaAlreadyExists() {
        final Quota quota = quota(null);
        doReturn(quotaEntity(null)).when(quotaRepository)
                .findByTypeAndSubjectAndQuotaGroup(quota.getType(), quota.getSubject(), quota.getQuotaGroup());
        assertThrows(IllegalArgumentException.class, () -> quotaService.create(quota));
    }

    @Test
    public void shouldFailCreateIfActionIsNotAllowedForStorage() {
        final Quota dto = quota(null);
        final QuotaEntity entity = quotaEntity(null);
        final QuotaActionEntity quotaActionEntity = quotaActionEntity(null);
        quotaActionEntity.getActions().add(QuotaActionType.READ_MODE_AND_DISABLE_NEW_JOBS);
        entity.setActions(Collections.singletonList(quotaActionEntity));
        entity.setQuotaGroup(QuotaGroup.STORAGE);
        doReturn(entity).when(quotaMapper).quotaToEntity(dto);

        assertThrows(IllegalStateException.class, () -> quotaService.create(dto));
    }

    @Test
    public void shouldFailCreateIfActionIsNotAllowedForComputeInstance() {
        final Quota dto = quota(null);
        final QuotaEntity entity = quotaEntity(null);
        final QuotaActionEntity quotaActionEntity = quotaActionEntity(null);
        quotaActionEntity.getActions().add(QuotaActionType.READ_MODE);
        entity.setActions(Collections.singletonList(quotaActionEntity));
        entity.setQuotaGroup(QuotaGroup.COMPUTE_INSTANCE);
        doReturn(entity).when(quotaMapper).quotaToEntity(dto);

        assertThrows(IllegalStateException.class, () -> quotaService.create(dto));
    }

    @Test
    public void shouldCreateStorageQuota() {
        final Quota dto = quota(null);
        final QuotaEntity entity = quotaEntity(null);
        final QuotaActionEntity quotaActionEntity = quotaActionEntity(null);
        entity.setActions(Collections.singletonList(quotaActionEntity));
        entity.setQuotaGroup(QuotaGroup.STORAGE);
        doReturn(entity).when(quotaMapper).quotaToEntity(dto);

        quotaService.create(dto);

        final ArgumentCaptor<Quota> mapperCaptor = ArgumentCaptor.forClass(Quota.class);
        verify(quotaMapper).quotaToEntity(mapperCaptor.capture());
        final Quota mapperResult = mapperCaptor.getValue();
        assertThat(mapperResult.getType(), notNullValue());
        assertThat(mapperResult.getQuotaGroup(), is(QuotaGroup.STORAGE));
        assertThat(mapperResult.getSubject(), notNullValue());
        assertThat(mapperResult.getValue(), notNullValue());

        final ArgumentCaptor<QuotaEntity> repoCaptor = ArgumentCaptor.forClass(QuotaEntity.class);
        verify(quotaRepository).save(repoCaptor.capture());
        final QuotaEntity repoResult = repoCaptor.getValue();
        assertThat(repoResult.getId(), nullValue());
        assertThat(repoResult.getPeriod(), is(QuotaPeriod.MONTH));
        assertThat(repoResult.getActions().size(), is(1));
        final QuotaActionEntity resultAction = repoResult.getActions().get(0);
        assertThat(resultAction.getQuota(), notNullValue());
        assertThat(resultAction.getThreshold(), notNullValue());
        assertThat(resultAction.getActions().size(), is(1));
    }

    @Test
    public void shouldCreateGlobalQuota() {
        final Quota dto = quota(null);
        dto.setQuotaGroup(QuotaGroup.GLOBAL);
        final QuotaEntity entity = quotaEntity(null);
        final QuotaActionEntity quotaActionEntity = quotaActionEntity(null);
        entity.setActions(Collections.singletonList(quotaActionEntity));
        entity.setQuotaGroup(QuotaGroup.GLOBAL);
        doReturn(entity).when(quotaMapper).quotaToEntity(dto);

        quotaService.create(dto);

        final ArgumentCaptor<Quota> mapperCaptor = ArgumentCaptor.forClass(Quota.class);
        verify(quotaMapper).quotaToEntity(mapperCaptor.capture());
        final Quota mapperResult = mapperCaptor.getValue();
        assertThat(mapperResult.getType(), nullValue());
        assertThat(mapperResult.getQuotaGroup(), is(QuotaGroup.GLOBAL));
        assertThat(mapperResult.getSubject(), nullValue());
        assertThat(mapperResult.getValue(), notNullValue());

        final ArgumentCaptor<QuotaEntity> repoCaptor = ArgumentCaptor.forClass(QuotaEntity.class);
        verify(quotaRepository).save(repoCaptor.capture());
        final QuotaEntity repoResult = repoCaptor.getValue();
        assertThat(repoResult.getId(), nullValue());
        assertThat(repoResult.getPeriod(), is(QuotaPeriod.MONTH));
        assertThat(repoResult.getActions(), nullValue());
    }

    @Test
    public void shouldFailUpdateIfQuotaEntityNotFound() {
        final Quota dto = quota(null);
        assertThrows(IllegalArgumentException.class, () -> quotaService.update(ID, dto));
    }

    @Test
    public void shouldFailUpdateIfQuotaValueNotSpecified() {
        final Quota dto = quota(null);
        dto.setValue(null);
        doReturn(quotaEntity(null)).when(quotaRepository).findOne(ID);

        assertThrows(IllegalArgumentException.class, () -> quotaService.update(ID, dto));
    }

    @Test
    public void shouldFailUpdateIfQuotaSubjectNotSpecifiedForNonGlobal() {
        final Quota dto = quota(null);
        dto.setSubject(null);
        doReturn(quotaEntity(null)).when(quotaRepository).findOne(ID);

        assertThrows(IllegalStateException.class, () -> quotaService.update(ID, dto));
    }

    @Test
    public void shouldFailUpdateIfActionIsNotAllowedForComputeInstance() {
        final Quota dto = quota(null);
        final QuotaEntity entity = quotaEntity(null);
        final QuotaActionEntity quotaActionEntity = quotaActionEntity(null);
        quotaActionEntity.getActions().add(QuotaActionType.READ_MODE);
        entity.setActions(Collections.singletonList(quotaActionEntity));
        entity.setQuotaGroup(QuotaGroup.COMPUTE_INSTANCE);
        doReturn(entity).when(quotaRepository).findOne(ID);
        doReturn(entity).when(quotaMapper).quotaToEntity(dto);

        assertThrows(IllegalStateException.class, () -> quotaService.update(ID, dto));
    }

    @Test
    public void shouldFailUpdateIfActionIsNotAllowedForStorage() {
        final Quota dto = quota(null);
        final QuotaEntity entity = quotaEntity(null);
        final QuotaActionEntity quotaActionEntity = quotaActionEntity(null);
        quotaActionEntity.getActions().add(QuotaActionType.READ_MODE_AND_DISABLE_NEW_JOBS);
        entity.setActions(Collections.singletonList(quotaActionEntity));
        entity.setQuotaGroup(QuotaGroup.STORAGE);
        doReturn(entity).when(quotaRepository).findOne(ID);
        doReturn(entity).when(quotaMapper).quotaToEntity(dto);

        assertThrows(IllegalStateException.class, () -> quotaService.update(ID, dto));
    }

    @Test
    public void shouldUpdateQuotaWithObsoleteActions() {
        final Quota dto = quota(null);
        final QuotaEntity oldEntity = quotaEntity(null);
        final QuotaActionEntity oldAction = quotaActionEntity(null);
        oldEntity.setActions(Collections.singletonList(oldAction));
        doReturn(oldEntity).when(quotaRepository).findOne(ID);

        final QuotaEntity newEntity = quotaEntity(null);
        final QuotaActionEntity newAction = quotaActionEntity(null);
        newAction.setId(ID_2);
        newEntity.setActions(Collections.singletonList(newAction));
        doReturn(newEntity).when(quotaMapper).quotaToEntity(any());

        quotaService.update(ID, dto);

        verify(quotaActionRepository).delete(ID);

        final ArgumentCaptor<QuotaEntity> repoCaptor = ArgumentCaptor.forClass(QuotaEntity.class);
        verify(quotaRepository).save(repoCaptor.capture());
        final QuotaEntity repoResult = repoCaptor.getValue();
        assertThat(repoResult.getId(), is(ID));
        assertThat(repoResult.getQuotaGroup(), is(oldEntity.getQuotaGroup()));
        assertThat(repoResult.getType(), is(oldEntity.getType()));
        assertThat(repoResult.getPeriod(), is(QuotaPeriod.MONTH));
        assertThat(repoResult.getActions().size(), is(1));
        final QuotaActionEntity resultAction = repoResult.getActions().get(0);
        assertThat(resultAction.getQuota(), notNullValue());
        assertThat(resultAction.getThreshold(), notNullValue());
        assertThat(resultAction.getActions().size(), is(1));
    }

    @Test
    public void shouldUpdateQuotaWithoutActions() {
        final Quota dto = quota(null);
        final QuotaEntity oldEntity = quotaEntity(null);
        doReturn(oldEntity).when(quotaRepository).findOne(ID);

        final QuotaEntity newEntity = quotaEntity(null);
        doReturn(newEntity).when(quotaMapper).quotaToEntity(any());

        quotaService.update(ID, dto);
        notInvoked(quotaActionRepository).delete(ID);

        final ArgumentCaptor<QuotaEntity> repoCaptor = ArgumentCaptor.forClass(QuotaEntity.class);
        verify(quotaRepository).save(repoCaptor.capture());
        final QuotaEntity repoResult = repoCaptor.getValue();
        assertThat(repoResult.getId(), is(ID));
        assertThat(repoResult.getQuotaGroup(), is(oldEntity.getQuotaGroup()));
        assertThat(repoResult.getType(), is(oldEntity.getType()));
        assertThat(repoResult.getPeriod(), is(QuotaPeriod.MONTH));
        assertThat(repoResult.getActions(), nullValue());
    }
}
