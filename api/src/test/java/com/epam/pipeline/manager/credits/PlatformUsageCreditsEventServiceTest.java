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
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateEventEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.vo.SecuredEntityVO;
import com.epam.pipeline.mapper.credits.PlatformUsageCreditsEventMapper;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsEventRepository;
import com.opencsv.CSVReader;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.Before;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unchecked")
public class PlatformUsageCreditsEventServiceTest {

    private static final Long USER_ID_1 = 1L;
    private static final Long USER_ID_2 = 2L;
    private static final String USERNAME = "testUser";
    private static final int VALUE = 100;
    private static final LocalDateTime DATE = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

    private final PlatformUsageCreditsEventRepository repository =
            mock(PlatformUsageCreditsEventRepository.class);
    private final PlatformUsageCreditsEventMapper mapper =
            Mappers.getMapper(PlatformUsageCreditsEventMapper.class);
    private final AuthManager authManager = mock(AuthManager.class);
    private final UserManager userManager = mock(UserManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final PlatformUsageCreditsUserBalanceService userBalanceService =
            mock(PlatformUsageCreditsUserBalanceService.class);

    private final PlatformUsageCreditsEventService service =
            new PlatformUsageCreditsEventService(repository, mapper, authManager, userManager, messageHelper,
                    userBalanceService);

    @Before
    public void setUp() {
        doAnswer(invocation -> invocation.getArguments()[0])
                .when(userBalanceService).updateByEvent(any(PlatformUsageCreditsUpdateEvent.class));
    }

    @Test
    public void processFiltersOutZeroValueRequests() {
        doReturn(Collections.<String>emptySet()).when(repository).findExistingIds(anyCollectionOf(String.class));

        service.process(Collections.singletonList(event(USER_ID_1, 0)));

        verify(repository, never()).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));
    }

    @Test
    public void processSavesNonZeroRequestsAsEntities() {
        doReturn(Collections.<String>emptySet()).when(repository).findExistingIds(anyCollectionOf(String.class));
        doReturn(Collections.singletonList(entity(USER_ID_1, VALUE)))
                .when(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));

        final List<PlatformUsageCreditsUpdateEvent> result =
                service.process(Collections.singletonList(event(USER_ID_1, VALUE)));

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
    public void processSkipsDuplicateEventById() {
        final String id = UUID.randomUUID().toString();
        doReturn(Collections.singleton(id)).when(repository).findExistingIds(anyCollectionOf(String.class));

        final List<PlatformUsageCreditsUpdateEvent> result =
                service.process(Collections.singletonList(eventWithId(USER_ID_1, VALUE, id)));

        verify(repository, never()).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));
        assertThat(result).isEmpty();
    }

    @Test
    public void processAllowsNewEventById() {
        doReturn(Collections.<String>emptySet()).when(repository).findExistingIds(anyCollectionOf(String.class));
        doReturn(Collections.singletonList(entity(USER_ID_1, VALUE)))
                .when(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));

        service.process(Collections.singletonList(event(USER_ID_1, VALUE)));

        verify(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));
    }

    @Test
    public void processPreservesCreatedDateWhenPresent() {
        final PlatformUsageCreditsUpdateEvent ev = event(USER_ID_1, VALUE);
        ev.setCreatedDate(DATE);
        doReturn(Collections.<String>emptySet()).when(repository).findExistingIds(anyCollectionOf(String.class));
        doReturn(Collections.singletonList(entity(USER_ID_1, VALUE)))
                .when(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));

        service.process(Collections.singletonList(ev));

        final ArgumentCaptor<List<PlatformUsageCreditsUpdateEventEntity>> captor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().get(0).getCreatedDate()).isEqualTo(DATE);
    }

    @Test
    public void processEventWithoutIdIsAlwaysNew() {
        final PlatformUsageCreditsUpdateEvent ev = event(USER_ID_1, VALUE);
        assertThat(ev.getId()).isNull();
        doReturn(Collections.singletonList(entity(USER_ID_1, VALUE)))
                .when(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));

        service.process(Collections.singletonList(ev));

        verify(repository).save(anyListOf(PlatformUsageCreditsUpdateEventEntity.class));
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

    private static PlatformUsageCreditsUpdateEvent event(final Long userId, final int value) {
        return PlatformUsageCreditsUpdateEvent.builder()
                .userId(userId)
                .incidentType(PlatformUsageCreditsUpdateAction.ActionType.DEDUCTION)
                .value(value)
                .build();
    }

    private static PlatformUsageCreditsUpdateEvent eventWithId(final Long userId, final int value,
                                                                final String id) {
        return PlatformUsageCreditsUpdateEvent.builder()
                .id(id)
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
                .createdDate(DATE)
                .build();
    }

    // export

    @Test
    public void exportWritesHeaderAndEvents() {
        doReturn(true).when(authManager).isAdmin();
        doReturn(new PageImpl<>(Collections.singletonList(entity(USER_ID_1, VALUE))))
                .when(repository).findAll(any(Specification.class), any(Pageable.class));
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.export(PlatformUsageCreditsEventFilterVO.builder().build(), output);

        final List<String[]> rows = parseCsv(output.toByteArray());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly(
                "Timestamp", "User ID", "Rule ID", "Entity Class", "Entity ID", "Type", "Value", "Message");
        assertThat(rows.get(1)[1]).isEqualTo(String.valueOf(USER_ID_1));
    }

    @Test
    public void exportPaginatesUntilPartialPage() {
        doReturn(true).when(authManager).isAdmin();
        final List<PlatformUsageCreditsUpdateEventEntity> fullPage =
                Collections.nCopies(1000, entity(USER_ID_1, VALUE));
        doReturn(new PageImpl<>(fullPage))
                .doReturn(new PageImpl<>(Collections.emptyList()))
                .when(repository).findAll(any(Specification.class), any(Pageable.class));

        service.export(PlatformUsageCreditsEventFilterVO.builder().build(), new ByteArrayOutputStream());

        verify(repository, times(2)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    public void exportNonAdminRestrictsToCurrentUser() {
        doReturn(false).when(authManager).isAdmin();
        doReturn(USERNAME).when(authManager).getAuthorizedUser();
        doReturn(user(USER_ID_1)).when(userManager).loadUserByName(USERNAME);
        doReturn("error").when(messageHelper).getMessage(any(String.class), any(Object[].class));
        doReturn(new PageImpl<>(Collections.emptyList()))
                .when(repository).findAll(any(Specification.class), any(Pageable.class));

        service.export(PlatformUsageCreditsEventFilterVO.builder().build(), new ByteArrayOutputStream());

        verify(userManager).loadUserByName(USERNAME);
    }

    @SneakyThrows
    private static List<String[]> parseCsv(final byte[] bytes) {
        return new CSVReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)).readAll();
    }

    private static PipelineUser user(final Long id) {
        final PipelineUser user = new PipelineUser();
        user.setId(id);
        return user;
    }
}
