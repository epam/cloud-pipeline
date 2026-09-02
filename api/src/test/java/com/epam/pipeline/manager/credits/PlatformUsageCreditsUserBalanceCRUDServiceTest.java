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

package com.epam.pipeline.manager.credits;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsMode;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalanceFilterVO;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUserBalanceEntity;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.credits.PlatformUsageCreditsUserBalanceMapper;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsUserBalanceRepository;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.BALANCE_VALUE;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.DEFAULT_BALANCE;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.USER_ID;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.balanceDto;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.balanceEntity;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.filterVO;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@SuppressWarnings("unchecked")
public class PlatformUsageCreditsUserBalanceCRUDServiceTest {

    private final PlatformUsageCreditsUserBalanceRepository repository =
            mock(PlatformUsageCreditsUserBalanceRepository.class);
    private final PlatformUsageCreditsUserBalanceMapper mapper =
            mock(PlatformUsageCreditsUserBalanceMapper.class);
    private final PreferenceManager preferenceManager =
            mock(PreferenceManager.class);
    private final PlatformUsageCreditsUserBalanceCRUDService service =
            new PlatformUsageCreditsUserBalanceCRUDService(repository, mapper, preferenceManager);

    @Test
    public void shouldReturnPagedBalancesOnFilter() {
        final PlatformUsageCreditsUserBalanceEntity entity = balanceEntity();
        final PlatformUsageCreditsUserBalance dto = balanceDto();
        doReturn(new PageImpl<>(Collections.singletonList(entity)))
                .when(repository).findAll(any(Specification.class), any(Pageable.class));
        doReturn(dto).when(mapper).toDto(entity);

        final PagedResult<List<PlatformUsageCreditsUserBalance>> result = service.filter(filterVO());

        assertThat(result.getElements().size(), is(1));
        assertThat(result.getElements().get(0), is(dto));
        assertThat(result.getTotalCount(), is(1));
    }

    @Test
    public void shouldPassPageRequestToRepository() {
        final PlatformUsageCreditsUserBalanceFilterVO filter = filterVO();
        doReturn(new PageImpl<>(Collections.emptyList()))
                .when(repository).findAll(any(Specification.class), any(Pageable.class));

        service.filter(filter);

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageNumber(), is(filter.getPage() - 1));
        assertThat(captor.getValue().getPageSize(), is(filter.getPageSize()));
    }

    @Test
    public void shouldFilterByBalanceGreaterThan() {
        final PlatformUsageCreditsUserBalanceFilterVO filter = PlatformUsageCreditsUserBalanceFilterVO.builder()
                .value(BALANCE_VALUE)
                .operation(">")
                .page(1)
                .pageSize(10)
                .build();
        doReturn(new PageImpl<>(Collections.emptyList()))
                .when(repository).findAll(any(Specification.class), any(Pageable.class));

        service.filter(filter);

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    public void shouldFilterByBalanceLessThan() {
        final PlatformUsageCreditsUserBalanceFilterVO filter = PlatformUsageCreditsUserBalanceFilterVO.builder()
                .value(BALANCE_VALUE)
                .operation("<")
                .page(1)
                .pageSize(10)
                .build();
        doReturn(new PageImpl<>(Collections.emptyList()))
                .when(repository).findAll(any(Specification.class), any(Pageable.class));

        service.filter(filter);

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    public void shouldFilterByBalanceEqualTo() {
        final PlatformUsageCreditsUserBalanceFilterVO filter = PlatformUsageCreditsUserBalanceFilterVO.builder()
                .value(BALANCE_VALUE)
                .operation("=")
                .page(1)
                .pageSize(10)
                .build();
        doReturn(new PageImpl<>(Collections.emptyList()))
                .when(repository).findAll(any(Specification.class), any(Pageable.class));

        service.filter(filter);

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    public void shouldReturnEmptyOptionalFromFindByUserIdWhenModeIsOff() {
        doReturn(PlatformUsageCreditsMode.OFF)
                .when(preferenceManager).getPreference(SystemPreferences.USAGE_CREDITS_MODE);

        final Optional<PlatformUsageCreditsUserBalance> result = service.findByUserId(USER_ID);

        assertFalse(result.isPresent());
        verify(repository, never()).findByUserId(any());
    }

    @Test
    public void shouldReturnEmptyMapFromFindAllAsMapWhenModeIsOff() {
        doReturn(PlatformUsageCreditsMode.OFF)
                .when(preferenceManager).getPreference(SystemPreferences.USAGE_CREDITS_MODE);

        final Map<Long, PlatformUsageCreditsUserBalance> result = service.findAllAsMap();

        assertTrue(result.isEmpty());
        verify(repository, never()).findAll();
    }

    @Test
    public void shouldSkipCreateDefaultBalanceWhenModeIsOff() {
        doReturn(PlatformUsageCreditsMode.OFF)
                .when(preferenceManager).getPreference(SystemPreferences.USAGE_CREDITS_MODE);

        service.createDefaultBalance(USER_ID);

        verify(repository, never()).findByUserId(any());
        verify(repository, never()).save(any(PlatformUsageCreditsUserBalanceEntity.class));
    }

    @Test
    public void shouldSkipCreateDefaultBalanceWhenBalanceAlreadyExists() {
        doReturn(PlatformUsageCreditsMode.BALANCE_ONLY)
                .when(preferenceManager).getPreference(SystemPreferences.USAGE_CREDITS_MODE);
        doReturn(Optional.of(balanceEntity())).when(repository).findByUserId(USER_ID);

        service.createDefaultBalance(USER_ID);

        verify(repository, never()).save(any(PlatformUsageCreditsUserBalanceEntity.class));
    }

    @Test
    public void shouldCreateDefaultBalanceWithDefaultPreferenceValue() {
        doReturn(PlatformUsageCreditsMode.BALANCE_ONLY)
                .when(preferenceManager).getPreference(SystemPreferences.USAGE_CREDITS_MODE);
        doReturn(DEFAULT_BALANCE)
                .when(preferenceManager).getPreference(SystemPreferences.USAGE_CREDITS_DEFAULT);
        doReturn(Optional.empty()).when(repository).findByUserId(USER_ID);

        service.createDefaultBalance(USER_ID);

        final ArgumentCaptor<PlatformUsageCreditsUserBalanceEntity> captor =
                ArgumentCaptor.forClass(PlatformUsageCreditsUserBalanceEntity.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getCurrentValue(), is(DEFAULT_BALANCE));
        assertThat(captor.getValue().getUserId(), is(USER_ID));
    }
}
