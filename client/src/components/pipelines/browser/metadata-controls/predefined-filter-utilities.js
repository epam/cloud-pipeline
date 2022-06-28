/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import classNames from 'classnames';
import moment from 'moment-timezone';

function parseClassName (className) {
  if (typeof className === 'string') {
    if (/^danger$/i.test(className)) {
      return 'danger';
    }
    if (/^(primary|action|success)$/i.test(className)) {
      return 'primary';
    }
  }
  return 'default';
}

export function parseScheme (scheme) {
  if (typeof scheme === 'string') {
    return {
      type: parseClassName(scheme),
      style: {}
    };
  }
  if (
    typeof scheme === 'object' &&
    (scheme.style || scheme.icon || scheme.type)
  ) {
    const {
      icon,
      type,
      style = {}
    } = scheme;
    return {
      icon,
      type: parseClassName(type),
      style
    };
  }
  if (typeof scheme === 'object') {
    return scheme;
  }
  return {
    style: {}
  };
}

export function getPredefinedFilterClassName (scheme, applied = false) {
  const {
    type
  } = scheme;
  let className;
  switch (type) {
    case 'primary':
      className = 'cp-primary';
      break;
    case 'danger':
      className = 'cp-danger';
      break;
    case 'default':
    default:
      className = applied ? 'cp-primary' : undefined;
      break;
  }
  return classNames(
    'cp-metadata-predefined-filter',
    {applied},
    className
  );
}

export function localDateToUTC (date) {
  if (!date) {
    return undefined;
  }
  const localTime = moment(date).toDate();
  return moment(localTime).utc();
}

function conditionMatchesItem (item, condition) {
  const getPropValue = property => {
    return item && item[property] ? item[property].value : undefined;
  };
  const matchSimpleCondition = (property, values = []) => {
    const value = getPropValue(property);
    if (values.includes(' ')) {
      return value && (typeof value !== 'string' || value.length > 0);
    }
    if (values.length === 0) {
      return value === undefined || value === '';
    }
    return values.includes(`${value}`);
  };
  const createdDateValue = getPropValue('createdDate');
  const createdDate = createdDateValue ? moment.utc(createdDateValue) : undefined;
  const {
    filters = {}
  } = condition || {};
  const {
    startDateFrom,
    endDateTo,
    ...properties
  } = filters;
  let match = true;
  if (startDateFrom) {
    match = match && createdDate && localDateToUTC(startDateFrom).isBefore(createdDate);
  }
  if (endDateTo && match) {
    match = match && createdDate && localDateToUTC(endDateTo).isAfter(createdDate);
  }
  if (match) {
    Object.entries(properties).forEach(([property, values]) => {
      match = match && matchSimpleCondition(property, values);
    });
  }
  return match;
}

export function getPredefinedFilterForItem (item, conditions) {
  const dangerIndex = o => (o.type || []).includes('danger') ? -1 : 0;
  return conditions
    .slice()
    .sort((a, b) => dangerIndex(a) - dangerIndex(b))
    .find(c => conditionMatchesItem(item, c));
}

export function getConditionStyles (condition, applied = false) {
  if (condition) {
    return {
      className: getPredefinedFilterClassName(condition.scheme, applied),
      style: (condition.scheme || {}).style
    };
  }
  return {};
}
