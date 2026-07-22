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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsEventFilterVO;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsResetRequest;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateAction;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateRequest;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateEventEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.vo.SecuredEntityVO;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.mapper.credits.PlatformUsageCreditsEventMapper;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsEventRepository;
import org.junit.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.manager.credits.PlatformUsageCreditsEventService.computeEventId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unchecked")
public class PlatformUsageCreditsEventServiceTest {

    private static final Long USER_ID_1 = 1L;
    private static final Long USER_ID_2 = 2L;
    private static final String USERNAME = "testUser";
    private static final int VALUE = 100;

    private final PlatformUsageCreditsEventRepository repository =
            mock(PlatformUsageCreditsEventRepository.class);
    private final PlatformUsageCreditsEventMapper mapper =
            Mappers.getMapper(PlatformUsageCreditsEventMapper.class);
    private final AuthManager authManager = mock(AuthManager.class);
    private final UserManager userManager = mock(UserManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);

    private final PlatformUsageCreditsEventService service =
            new PlatformUsageCreditsEventService(repository, mapper, authManager, userManager, messageHelper);

    @Test
    public void processFiltersOutZeroValueRequests() {
        service.process(Collections.singletonList(request(USER_ID_1, 0)));

        verify(repository, never()).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));
    }

    @Test
    public void processSavesNonZeroRequestsAsEntities() {
        doReturn(Collections.singletonList(entity(USER_ID_1, VALUE)))
                .when(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));

        final List<PlatformUsageCreditsUpdateEvent> result =
                service.process(Collections.singletonList(request(USER_ID_1, VALUE)));

        final ArgumentCaptor<List<PlatformUsageCreditsUpdateEventEntity>> captor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getUserId()).isEqualTo(USER_ID_1);
        assertThat(captor.getValue().get(0).getValue()).isEqualTo(VALUE);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(USER_ID_1);
    }

    @Test
    public void processSetsCreatedDateWhenNull() {
        final PlatformUsageCreditsUpdateRequest req = request(USER_ID_1, VALUE);
        req.setCreatedDate(null);
        doReturn(Collections.singletonList(entity(USER_ID_1, VALUE)))
                .when(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));

        service.process(Collections.singletonList(req));

        final ArgumentCaptor<List<PlatformUsageCreditsUpdateEventEntity>> captor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().get(0).getCreatedDate()).isNotNull();
    }

    @Test
    public void processSkipsDuplicateEventById() {
        final String id = computeEventId(entity(USER_ID_1, VALUE));
        doReturn(true).when(repository).exists(id);

        final List<PlatformUsageCreditsUpdateEvent> result =
                service.process(Collections.singletonList(request(USER_ID_1, VALUE)));

        verify(repository, never()).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));
        assertThat(result).isEmpty();
    }

    @Test
    public void processAllowsNewEventById() {
        final String id = computeEventId(entity(USER_ID_1, VALUE));
        doReturn(false).when(repository).exists(id);
        doReturn(Collections.singletonList(entity(USER_ID_1, VALUE)))
                .when(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));

        service.process(Collections.singletonList(request(USER_ID_1, VALUE)));

        verify(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));
    }

    @Test
    public void processPreservesCreatedDateWhenPresent() {
        final LocalDateTime createdDate = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        final PlatformUsageCreditsUpdateRequest req = request(USER_ID_1, VALUE);
        req.setCreatedDate(createdDate);
        doReturn(Collections.singletonList(entity(USER_ID_1, VALUE)))
                .when(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));

        service.process(Collections.singletonList(req));

        final ArgumentCaptor<List<PlatformUsageCreditsUpdateEventEntity>> captor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().get(0).getCreatedDate()).isEqualTo(createdDate);
    }

    // filter

    @Test
    public void filterAdminPassesFilterUnchanged() {
        doReturn(true).when(authManager).isAdmin();
        doReturn(new PageImpl<>(Collections.emptyList()))
                .when(repository).findAll(any(Specification.class), any(Pageable.class));

        final PagedResult<List<PlatformUsageCreditsUpdateEvent>> result =
                service.filter(PlatformUsageCreditsEventFilterVO.builder()
                        .userIds(Arrays.asList(USER_ID_1, USER_ID_2))
                        .page(1).pageSize(10).build());

        assertThat(result.getElements()).isEmpty();
        assertThat(result.getTotalCount()).isZero();
        verify(userManager, never()).loadUserByName(any(String.class));
    }

    @Test
    public void filterNonAdminWithNoUserIdsDefaultsToCurrentUser() {
        doReturn(false).when(authManager).isAdmin();
        doReturn(USERNAME).when(authManager).getAuthorizedUser();
        doReturn(user(USER_ID_1)).when(userManager).loadUserByName(USERNAME);
        doReturn("error").when(messageHelper).getMessage(any(String.class), any(Object[].class));
        doReturn(new PageImpl<>(Collections.emptyList()))
                .when(repository).findAll(any(Specification.class), any(Pageable.class));

        service.filter(PlatformUsageCreditsEventFilterVO.builder().page(1).pageSize(10).build());

        verify(userManager).loadUserByName(USERNAME);
    }

    @Test
    public void filterNonAdminOverridesOtherUserIdsToCurrentUser() {
        doReturn(false).when(authManager).isAdmin();
        doReturn(USERNAME).when(authManager).getAuthorizedUser();
        doReturn(user(USER_ID_1)).when(userManager).loadUserByName(USERNAME);
        doReturn("error").when(messageHelper).getMessage(any(String.class), any(Object[].class));
        doReturn(new PageImpl<>(Collections.emptyList()))
                .when(repository).findAll(any(Specification.class), any(Pageable.class));

        service.filter(PlatformUsageCreditsEventFilterVO.builder()
                .userIds(Collections.singletonList(USER_ID_2))
                .page(1).pageSize(10).build());

        verify(userManager).loadUserByName(USERNAME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void filterRejectsWithoutEntityLinkCombinedWithEntities() {
        doReturn(true).when(authManager).isAdmin();

        service.filter(PlatformUsageCreditsEventFilterVO.builder()
                .entities(Collections.singletonList(new SecuredEntityVO()))
                .withoutEntityLink(true)
                .page(1).pageSize(10).build());
    }

    @Test
    public void filterWithoutEntityLinkPassesThrough() {
        doReturn(true).when(authManager).isAdmin();
        doReturn(new PageImpl<>(Collections.emptyList()))
                .when(repository).findAll(any(Specification.class), any(Pageable.class));

        service.filter(PlatformUsageCreditsEventFilterVO.builder()
                .withoutEntityLink(true)
                .page(1).pageSize(10).build());

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    // reset

    @Test
    public void resetWithNullUserIdsLoadsAllUsers() {
        doReturn(Collections.singletonList(user(USER_ID_1))).when(userManager).loadAllUsers();
        doReturn(Collections.singletonList(entity(USER_ID_1, VALUE)))
                .when(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));

        service.reset(PlatformUsageCreditsResetRequest.builder().value(VALUE).build());

        verify(userManager).loadAllUsers();
    }

    @Test
    public void resetWithEmptyUserIdsLoadsAllUsers() {
        doReturn(Arrays.asList(user(USER_ID_1), user(USER_ID_2))).when(userManager).loadAllUsers();
        doReturn(Arrays.asList(entity(USER_ID_1, VALUE), entity(USER_ID_2, VALUE)))
                .when(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));

        service.reset(PlatformUsageCreditsResetRequest.builder()
                .userIds(Collections.emptyList()).value(VALUE).build());

        final ArgumentCaptor<List<PlatformUsageCreditsUpdateEventEntity>> captor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).extracting("userId")
                .containsExactlyInAnyOrder(USER_ID_1, USER_ID_2);
    }

    @Test
    public void resetSkipsNonExistentUsers() {
        final List<Long> userIds = Arrays.asList(USER_ID_1, USER_ID_2);
        doReturn(Collections.singletonList(user(USER_ID_1)))
                .when(userManager).loadUsersById(userIds);
        doReturn(Collections.singletonList(entity(USER_ID_1, VALUE)))
                .when(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));

        service.reset(PlatformUsageCreditsResetRequest.builder().userIds(userIds).value(VALUE).build());

        final ArgumentCaptor<List<PlatformUsageCreditsUpdateEventEntity>> captor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).extracting("userId")
                .containsOnly(USER_ID_1);
    }

    @Test
    public void resetGeneratesOneResetEventPerUser() {
        doReturn(Arrays.asList(user(USER_ID_1), user(USER_ID_2))).when(userManager).loadAllUsers();
        doReturn(Arrays.asList(entity(USER_ID_1, VALUE), entity(USER_ID_2, VALUE)))
                .when(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));

        service.reset(PlatformUsageCreditsResetRequest.builder().value(VALUE).build());

        final ArgumentCaptor<List<PlatformUsageCreditsUpdateEventEntity>> captor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(repository).save(captor.capture());
        final List<PlatformUsageCreditsUpdateEventEntity> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting("incidentType")
                .containsOnly(PlatformUsageCreditsUpdateAction.ActionType.RESET);
        assertThat(saved).extracting("value")
                .containsOnly(VALUE);
        assertThat(saved).extracting("userId")
                .containsExactlyInAnyOrder(USER_ID_1, USER_ID_2);
    }

    private static PlatformUsageCreditsUpdateRequest request(final Long userId, final int value) {
        return PlatformUsageCreditsUpdateRequest.builder()
                .userId(userId)
                .incidentType(PlatformUsageCreditsUpdateAction.ActionType.DEDUCTION)
                .value(value)
                .build();
    }

    private static PlatformUsageCreditsUpdateEventEntity entity(final Long userId, final int value) {
        return PlatformUsageCreditsUpdateEventEntity.builder()
                .userId(userId)
                .incidentType(PlatformUsageCreditsUpdateAction.ActionType.DEDUCTION)
                .value(value)
                .createdDate(LocalDateTime.of(2026, 1, 1, 0, 0, 0))
                .build();
    }

    private static PipelineUser user(final Long id) {
        final PipelineUser user = new PipelineUser();
        user.setId(id);
        return user;
    }
}
