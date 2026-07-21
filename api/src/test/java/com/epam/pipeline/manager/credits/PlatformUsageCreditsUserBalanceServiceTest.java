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
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalanceFilterVO;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUserBalanceEntity;
import com.epam.pipeline.mapper.credits.PlatformUsageCreditsUserBalanceMapper;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsUserBalanceRepository;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.BALANCE_VALUE;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.RESET_VALUE;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.USER_ID;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.balanceDto;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.balanceEntity;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.filterVO;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unchecked")
public class PlatformUsageCreditsUserBalanceServiceTest {

    private final PlatformUsageCreditsUserBalanceRepository repository =
            mock(PlatformUsageCreditsUserBalanceRepository.class);
    private final PlatformUsageCreditsUserBalanceMapper mapper =
            mock(PlatformUsageCreditsUserBalanceMapper.class);
    private final PlatformUsageCreditsUserBalanceService service =
            new PlatformUsageCreditsUserBalanceService(repository, mapper);

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
        assertThat(captor.getValue().getPageNumber(), is(filter.getPage()));
        assertThat(captor.getValue().getPageSize(), is(filter.getPageSize()));
    }

    @Test
    public void shouldUpdateExistingEntityOnResetForUser() {
        final PlatformUsageCreditsUserBalanceEntity entity = balanceEntity();
        doReturn(Optional.of(entity)).when(repository).findByUserId(USER_ID);

        service.reset(RESET_VALUE, USER_ID);

        final ArgumentCaptor<PlatformUsageCreditsUserBalanceEntity> captor =
                ArgumentCaptor.forClass(PlatformUsageCreditsUserBalanceEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCurrentValue(), is(RESET_VALUE));
        assertThat(captor.getValue().getModifiedDate(), notNullValue());
        assertThat(captor.getValue().getUserId(), is(USER_ID));
    }

    @Test
    public void shouldCreateEntityWhenUserHasNoBalanceOnReset() {
        doReturn(Optional.empty()).when(repository).findByUserId(USER_ID);

        service.reset(RESET_VALUE, USER_ID);

        final ArgumentCaptor<PlatformUsageCreditsUserBalanceEntity> captor =
                ArgumentCaptor.forClass(PlatformUsageCreditsUserBalanceEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCurrentValue(), is(RESET_VALUE));
        assertThat(captor.getValue().getModifiedDate(), notNullValue());
        assertThat(captor.getValue().getUserId(), is(USER_ID));
    }

    @Test
    public void shouldBulkResetAllWhenUserIdIsNull() {
        service.reset(RESET_VALUE, null);

        verify(repository).resetAll(anyInt(), any());
        verify(repository, never()).findByUserId(any());
        verify(repository, never()).save(any(PlatformUsageCreditsUserBalanceEntity.class));
    }

    @Test
    public void shouldFilterByBalanceGreaterThan() {
        final PlatformUsageCreditsUserBalanceFilterVO filter = PlatformUsageCreditsUserBalanceFilterVO.builder()
                .value(BALANCE_VALUE)
                .operation(">")
                .page(0)
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
                .page(0)
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
                .page(0)
                .pageSize(10)
                .build();
        doReturn(new PageImpl<>(Collections.emptyList()))
                .when(repository).findAll(any(Specification.class), any(Pageable.class));

        service.filter(filter);

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }
}
