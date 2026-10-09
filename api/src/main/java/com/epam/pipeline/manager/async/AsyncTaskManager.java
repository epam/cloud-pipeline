/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.async;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.async.AsyncTaskFilterVO;
import com.epam.pipeline.dto.async.AsyncTask;
import com.epam.pipeline.dto.async.AsyncTaskParams;
import com.epam.pipeline.dto.async.AsyncTaskResult;
import com.epam.pipeline.dto.async.AsyncTaskStatus;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.entity.async.AsyncTaskEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.mapper.async.AsyncTaskMapper;
import com.epam.pipeline.repository.async.AsyncTaskRepository;
import com.epam.pipeline.repository.async.AsyncTaskSpecification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AsyncTaskManager {

    private static final String CREATED_FIELD = "created";
    private static final String RESULT_FIELD = "result";
    private static final String ERROR_MESSAGE_FIELD = "errorMessage";

    private final AsyncTaskRepository repository;
    private final AsyncTaskMapper asyncTaskMapper;
    private final MessageHelper messageHelper;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public AsyncTask create(final AsyncTaskType type, final String owner, final AsyncTaskParams params) {
        Assert.notNull(type, messageHelper.getMessage(MessageConstants.ERROR_ASYNC_TASK_TYPE_REQUIRED));
        Assert.hasText(owner, messageHelper.getMessage(MessageConstants.ERROR_ASYNC_TASK_OWNER_REQUIRED));
        assertPayloadMatches(type, params == null ? null : params.getType());
        final LocalDateTime now = DateUtils.nowUTC();
        final AsyncTaskEntity entity = AsyncTaskEntity.builder()
                .type(type)
                .owner(owner)
                .status(AsyncTaskStatus.PENDING)
                .params(params)
                .created(now)
                .statusChanged(now)
                .build();
        return asyncTaskMapper.toDto(repository.save(entity));
    }

    public AsyncTask load(final String id) {
        return asyncTaskMapper.toDto(find(id));
    }

    public Optional<AsyncTask> findById(final String id) {
        return repository.findById(id).map(asyncTaskMapper::toDto);
    }

    /**
     * Lists tasks matching the filter, newest first. Paging applies only for a positive page size;
     * zero or absent means every match, because that is what an untouched form sends.
     */
    public List<AsyncTask> loadByFilter(final AsyncTaskFilterVO filter) {
        final Sort newestFirst = Sort.by(Sort.Direction.DESC, CREATED_FIELD);
        final List<AsyncTaskEntity> found = isPaged(filter)
                ? repository.findAll(AsyncTaskSpecification.of(filter), pageable(filter, newestFirst))
                        .getContent()
                : repository.findAll(AsyncTaskSpecification.of(filter), newestFirst);
        return found.stream().map(asyncTaskMapper::toDto).collect(Collectors.toList());
    }

    public long countActive(final String owner) {
        return repository.countByOwnerAndStatusIn(owner, AsyncTaskStatus.activeStatuses());
    }

    /**
     * Hands out the oldest pending task of the type and marks it running. The row is locked before it
     * is updated, so one task never goes to two callers.
     *
     * @return the claimed task, empty when the queue holds nothing of that type
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<AsyncTask> claimNext(final AsyncTaskType type) {
        Assert.notNull(type, messageHelper.getMessage(MessageConstants.ERROR_ASYNC_TASK_TYPE_REQUIRED));
        return repository.lockNextPending(type.name())
                .filter(id -> repository.moveStatus(id, AsyncTaskStatus.PENDING.name(),
                        AsyncTaskStatus.RUNNING.name(), null, null, DateUtils.nowUTC()) == 1)
                .flatMap(repository::findById)
                .map(asyncTaskMapper::toDto);
    }

    /**
     * Applies a status change reported by an executor. Repeating a report the task already reflects
     * answers with the task as it is, so an executor that lost the first response can retry.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AsyncTask updateStatus(final String id, final AsyncTaskStatus target,
                                  final AsyncTaskResult result, final String errorMessage) {
        Assert.notNull(target, messageHelper.getMessage(MessageConstants.ERROR_ASYNC_TASK_STATUS_REQUIRED));
        Assert.isTrue(target.isReportable(), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_STATUS_NOT_REPORTABLE, target));
        assertPayloadForStatus(target, result, errorMessage);
        final AsyncTaskEntity entity = find(id);
        assertPayloadMatches(entity.getType(), result == null ? null : result.getType());
        final AsyncTaskStatus current = entity.getStatus();
        if (current == target) {
            return asyncTaskMapper.toDto(entity);
        }
        Assert.isTrue(current.canMoveTo(target), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_TRANSITION_NOT_ALLOWED, id, current, target));
        final int updated = repository.moveStatus(id, current.name(), target.name(), toJson(result),
                errorMessage, DateUtils.nowUTC());
        Assert.isTrue(updated == 1, messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_CHANGED_MEANWHILE, id, current));
        return load(id);
    }

    /**
     * Cancels a task in one statement: one nobody has taken is cancelled at once, a running one is
     * asked to stop and confirms that itself once cleaned up. Repeating the call changes nothing.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AsyncTask cancel(final String id) {
        if (repository.cancelTask(id, DateUtils.nowUTC()) == 1) {
            return load(id);
        }
        final AsyncTaskEntity entity = find(id);
        Assert.isTrue(entity.getStatus().isActive(), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_ALREADY_FINISHED, id, entity.getStatus()));
        return asyncTaskMapper.toDto(entity);
    }

    private boolean isPaged(final AsyncTaskFilterVO filter) {
        return filter.getPageSize() != null && filter.getPageSize() > 0;
    }

    /**
     * Pages are counted from 1, as they are elsewhere in this API.
     */
    private Pageable pageable(final AsyncTaskFilterVO filter, final Sort sort) {
        final int page = filter.getPage() == null ? 0 : Math.max(filter.getPage() - 1, 0);
        return PageRequest.of(page, filter.getPageSize(), sort);
    }

    private AsyncTaskEntity find(final String id) {
        Assert.hasText(id, messageHelper.getMessage(MessageConstants.ERROR_ASYNC_TASK_ID_REQUIRED));
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException(
                messageHelper.getMessage(MessageConstants.ERROR_ASYNC_TASK_NOT_FOUND, id)));
    }

    /**
     * A report carries exactly what its status is about - a result on success, an error text on
     * failure, nothing otherwise - so success without a size cannot be stored.
     */
    private void assertPayloadForStatus(final AsyncTaskStatus target, final AsyncTaskResult result,
                                        final String errorMessage) {
        if (AsyncTaskStatus.SUCCESS == target) {
            Assert.notNull(result, messageHelper.getMessage(
                    MessageConstants.ERROR_ASYNC_TASK_PAYLOAD_REQUIRED, RESULT_FIELD, target));
        } else {
            Assert.isNull(result, messageHelper.getMessage(
                    MessageConstants.ERROR_ASYNC_TASK_PAYLOAD_UNEXPECTED, RESULT_FIELD, target));
        }
        if (AsyncTaskStatus.FAILED == target) {
            Assert.hasText(errorMessage, messageHelper.getMessage(
                    MessageConstants.ERROR_ASYNC_TASK_PAYLOAD_REQUIRED, ERROR_MESSAGE_FIELD, target));
        } else {
            Assert.isTrue(!StringUtils.hasText(errorMessage), messageHelper.getMessage(
                    MessageConstants.ERROR_ASYNC_TASK_PAYLOAD_UNEXPECTED, ERROR_MESSAGE_FIELD, target));
        }
    }

    private void assertPayloadMatches(final AsyncTaskType taskType, final AsyncTaskType payloadType) {
        Assert.isTrue(payloadType == null || payloadType == taskType, messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_PAYLOAD_TYPE_MISMATCH, payloadType, taskType));
    }

    private String toJson(final Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(messageHelper.getMessage(
                    MessageConstants.ERROR_ASYNC_TASK_RESULT_INVALID, e.getMessage()), e);
        }
    }
}
