/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import {Link} from 'react-router';
import FileSaver from 'file-saver';
import UsageCreditsEventsExport from '../../../../../models/usage/UsageCreditsEventsExport';
import checkBlob from '../../../../../utils/check-blob';

export const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss';

export const ENTITY_CLASS_PIPELINE_RUN = 'PIPELINE_RUN';

export const INCIDENT_TYPES = ['INCREASE', 'DEDUCTION'];

export const DEFAULT_PAGE_SIZE = 15;

export const EMPTY_FILTERS = {
  from: undefined,
  to: undefined,
  ruleIds: [],
  incidentTypes: [],
  entityId: '',
  showEmpty: true
};

export const EMPTY_DRAFT_FILTERS = {
  from: undefined,
  to: undefined,
  ruleIds: [],
  incidentTypes: [],
  entityId: '',
  showEmpty: true
};

export function formatDate (value) {
  return value ? value.format(DATE_FORMAT) : undefined;
}

export function isEmptyEntity (entity) {
  return !entity || (entity.id == null && !entity.class);
}

export function hasActiveEntityFilter (filters = {}) {
  const entityId = filters.entityId != null
    ? String(filters.entityId).trim()
    : '';
  return !!(entityId || filters.showEmpty === false);
}

export function hasActiveCreditsDetailsFilters (filters = {}) {
  return !!(
    filters.from ||
    filters.to ||
    (filters.ruleIds && filters.ruleIds.length) ||
    (filters.incidentTypes && filters.incidentTypes.length) ||
    hasActiveEntityFilter(filters)
  );
}

export function getEntitiesFilterPayload (filters = {}) {
  const entityId = filters.entityId != null
    ? String(filters.entityId).trim()
    : '';
  if (!entityId) {
    return undefined;
  }
  const id = Number(entityId);
  return [{
    id: Number.isNaN(id) ? entityId : id,
    class: ENTITY_CLASS_PIPELINE_RUN
  }];
}

export function getEventsFilterPayload ({
  user,
  filters = {},
  page,
  pageSize
} = {}) {
  const {
    ruleIds,
    incidentTypes
  } = filters;
  const entities = getEntitiesFilterPayload(filters);
  return {
    userIds: user && user.id ? [user.id] : [],
    ruleIds,
    incidentTypes,
    ...(entities ? {entities} : {}),
    ...(page !== undefined ? {page: page - 1} : {}),
    ...(pageSize !== undefined ? {pageSize} : {})
  };
}

export async function exportCreditsEventsToCSV ({filters = {}, payload} = {}) {
  const request = new UsageCreditsEventsExport(
    formatDate(filters.from),
    formatDate(filters.to)
  );
  await request.send(payload);
  if (request.value instanceof Blob) {
    const error = await checkBlob(request.value, 'Error exporting usage credits');
    if (error) {
      throw new Error(error);
    }
    FileSaver.saveAs(request.value, 'usage-credits-events.csv');
    return;
  }
  throw new Error(request.error || 'Error exporting usage credits');
}

export function applyClientEntityFilters (elements = [], showEmpty) {
  if (showEmpty !== false) {
    return elements;
  }
  return elements.filter((item) => !isEmptyEntity(item.entity));
}

export function formatEntity (entity) {
  if (!entity || entity.id === undefined || entity.id === null) {
    return '';
  }
  if (entity.class === ENTITY_CLASS_PIPELINE_RUN) {
    return (
      <Link to={`/run/${entity.id}`}>
        {`Run ${entity.id}`}
      </Link>
    );
  }
  return `${entity.class}:${entity.id}`;
}

export function getIncidentTypeClassName (incidentType) {
  if (incidentType === 'DEDUCTION') {
    return 'cp-error';
  }
  if (incidentType === 'INCREASE') {
    return 'cp-success';
  }
  return undefined;
}
