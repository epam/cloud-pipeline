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
import moment from 'moment';
import FileSaver from 'file-saver';
import UsageCreditsEventsExport from '../../../../../models/usage/UsageCreditsEventsExport';
import checkBlob from '../../../../../utils/check-blob';

export const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss';

export const API_DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss.SSS';

export const ENTITY_CLASS_PIPELINE_RUN = 'PipelineRun';

export const INCIDENT_TYPES = ['INCREASE', 'DEDUCTION', 'RESET'];

export const DEFAULT_PAGE_SIZE = 15;

export const EMPTY_FILTERS = {
  from: undefined,
  to: undefined,
  ruleIds: [],
  incidentTypes: [],
  entityIds: [],
  onlyEmpty: false
};

export const EMPTY_DRAFT_FILTERS = {
  from: undefined,
  to: undefined,
  ruleIds: [],
  incidentTypes: [],
  entityIds: [],
  onlyEmpty: false
};

export function formatDate (value) {
  return value ? moment(value).utc().format(API_DATE_FORMAT) : undefined;
}

export function normalizeRunIds (values = []) {
  return values
    .map((value) => String(value).trim())
    .filter((value) => /^\d+$/.test(value));
}

export function hasActiveEntityFilter (filters = {}) {
  return !!(normalizeRunIds(filters.entityIds).length || filters.onlyEmpty);
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
  const entities = normalizeRunIds(filters.entityIds)
    .map((entityId) => ({
      id: Number(entityId),
      class: ENTITY_CLASS_PIPELINE_RUN
    }));
  return entities.length ? entities : undefined;
}

export function getEventsFilterPayload ({
  user,
  filters = {},
  page,
  pageSize
} = {}) {
  const {
    ruleIds,
    incidentTypes,
    onlyEmpty
  } = filters;
  const entities = getEntitiesFilterPayload(filters);
  const ruleId = ruleIds && ruleIds.length
    ? Number(ruleIds[0])
    : undefined;
  const withoutEntityLink = !entities && onlyEmpty
    ? true
    : undefined;
  return {
    userIds: user && user.id ? [user.id] : [],
    incidentTypes,
    ...(ruleId !== undefined && !Number.isNaN(ruleId) ? {ruleId} : {}),
    ...(withoutEntityLink !== undefined ? {withoutEntityLink} : {}),
    ...(entities ? {entities} : {}),
    ...(filters.from ? {from: formatDate(filters.from)} : {}),
    ...(filters.to ? {to: formatDate(filters.to)} : {}),
    ...(page !== undefined ? {page} : {}),
    ...(pageSize !== undefined ? {pageSize} : {})
  };
}

export async function exportCreditsEventsToCSV ({payload} = {}) {
  const request = new UsageCreditsEventsExport();
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

export function formatIncidentValue (value, incidentType) {
  if (value === undefined || value === null || value === '') {
    return '';
  }
  const numeric = Number(value);
  if (Number.isNaN(numeric) || numeric === 0 || incidentType === 'RESET') {
    return `${value}`;
  }
  const sign = incidentType === 'DEDUCTION' ? '-' : '+';
  return `${sign}${Math.abs(numeric)}`;
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
