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

import UsageCreditsEventsFilter from './UsageCreditsEventsFilter';

const MOCK_EVENTS = [
  {
    userId: 1,
    ruleId: 1,
    entity: {id: 123, class: 'PIPELINE_RUN'},
    message: 'Run IDLE for >48h with no activity',
    incidentType: 'DEDUCTION',
    value: -50,
    timestamp: '2026-07-01 09:14:32'
  },
  {
    userId: 2,
    ruleId: 2,
    entity: {id: 124, class: 'PIPELINE_RUN'},
    message: 'User launched a SPOT instance',
    incidentType: 'INCREASE',
    value: 20,
    timestamp: '2026-07-01 11:05:17'
  },
  {
    userId: 3,
    ruleId: 3,
    entity: {id: 125, class: 'PIPELINE_RUN'},
    message: 'GPU node used for single-threaded workload',
    incidentType: 'DEDUCTION',
    value: -150,
    timestamp: '2026-07-02 08:44:01'
  },
  {
    userId: 3,
    ruleId: 4,
    entity: {id: 126, class: 'PIPELINE_RUN'},
    message: 'Spot run IDLE for >72h with no activity',
    incidentType: 'DEDUCTION',
    value: -50,
    timestamp: '2026-07-03 14:22:55'
  },
  {
    userId: 2,
    ruleId: 5,
    entity: null,
    message: 'Admin correction: credits restored',
    incidentType: 'INCREASE',
    value: 200,
    timestamp: '2026-07-04 10:00:00'
  },
  {
    userId: 1,
    ruleId: 2,
    entity: {id: 127, class: 'PIPELINE_RUN'},
    message: 'User launched a SPOT instance',
    incidentType: 'INCREASE',
    value: 20,
    timestamp: '2026-07-05 09:00:00'
  },
  {
    userId: 1,
    ruleId: 1,
    entity: {id: 128, class: 'PIPELINE_RUN'},
    message: 'Run IDLE for >48h with no activity',
    incidentType: 'DEDUCTION',
    value: -50,
    timestamp: '2026-07-05 12:30:00'
  },
  {
    userId: 1,
    ruleId: 3,
    entity: {id: 129, class: 'PIPELINE_RUN'},
    message: 'Long running job penalty',
    incidentType: 'DEDUCTION',
    value: -10,
    timestamp: '2026-07-06 08:00:00'
  },
  {
    userId: 1,
    ruleId: 2,
    entity: {id: 130, class: 'PIPELINE_RUN'},
    message: 'User launched a SPOT instance',
    incidentType: 'INCREASE',
    value: 20,
    timestamp: '2026-07-06 15:00:00'
  },
  {
    userId: 1,
    ruleId: 4,
    entity: {id: 131, class: 'PIPELINE_RUN'},
    message: 'Spot run IDLE for >72h with no activity',
    incidentType: 'DEDUCTION',
    value: -50,
    timestamp: '2026-07-07 10:00:00'
  },
  {
    userId: 1,
    ruleId: 5,
    entity: null,
    message: 'Manual adjustment',
    incidentType: 'INCREASE',
    value: 100,
    timestamp: '2026-07-07 16:00:00'
  },
  {
    userId: 1,
    ruleId: 1,
    entity: {id: 132, class: 'PIPELINE_RUN'},
    message: 'Run IDLE for >48h with no activity',
    incidentType: 'DEDUCTION',
    value: -50,
    timestamp: '2026-07-08 09:00:00'
  },
  {
    userId: 1,
    ruleId: 2,
    entity: {id: 133, class: 'PIPELINE_RUN'},
    message: 'User launched a SPOT instance',
    incidentType: 'INCREASE',
    value: 20,
    timestamp: '2026-07-08 11:00:00'
  },
  {
    userId: 1,
    ruleId: 3,
    entity: {id: 134, class: 'PIPELINE_RUN'},
    message: 'Long running job penalty',
    incidentType: 'DEDUCTION',
    value: -10,
    timestamp: '2026-07-08 14:00:00'
  },
  {
    userId: 1,
    ruleId: 2,
    entity: {id: 135, class: 'PIPELINE_RUN'},
    message: 'User launched a SPOT instance',
    incidentType: 'INCREASE',
    value: 20,
    timestamp: '2026-07-09 08:00:00'
  },
  {
    userId: 1,
    ruleId: 1,
    entity: {id: 136, class: 'PIPELINE_RUN'},
    message: 'Run IDLE for >48h with no activity',
    incidentType: 'DEDUCTION',
    value: -50,
    timestamp: '2026-07-09 10:00:00'
  },
  {
    userId: 1,
    ruleId: 4,
    entity: {id: 137, class: 'PIPELINE_RUN'},
    message: 'Spot run IDLE for >72h with no activity',
    incidentType: 'DEDUCTION',
    value: -50,
    timestamp: '2026-07-09 12:00:00'
  },
  {
    userId: 1,
    ruleId: 2,
    entity: {id: 138, class: 'PIPELINE_RUN'},
    message: 'User launched a SPOT instance',
    incidentType: 'INCREASE',
    value: 20,
    timestamp: '2026-07-10 09:00:00'
  },
  {
    userId: 1,
    ruleId: 5,
    entity: null,
    message: 'Admin correction: credits restored',
    incidentType: 'INCREASE',
    value: 200,
    timestamp: '2026-07-10 11:00:00'
  },
  {
    userId: 1,
    ruleId: 3,
    entity: {id: 139, class: 'PIPELINE_RUN'},
    message: 'Long running job penalty',
    incidentType: 'DEDUCTION',
    value: -10,
    timestamp: '2026-07-11 08:00:00'
  },
  {
    userId: 1,
    ruleId: 1,
    entity: {id: 140, class: 'PIPELINE_RUN'},
    message: 'Run IDLE for >48h with no activity',
    incidentType: 'DEDUCTION',
    value: -50,
    timestamp: '2026-07-11 10:00:00'
  },
  {
    userId: 1,
    ruleId: 2,
    entity: {id: 141, class: 'PIPELINE_RUN'},
    message: 'User launched a SPOT instance',
    incidentType: 'INCREASE',
    value: 20,
    timestamp: '2026-07-12 09:00:00'
  },
  {
    userId: 1,
    ruleId: 4,
    entity: {id: 142, class: 'PIPELINE_RUN'},
    message: 'Spot run IDLE for >72h with no activity',
    incidentType: 'DEDUCTION',
    value: -50,
    timestamp: '2026-07-12 14:00:00'
  },
  {
    userId: 1,
    ruleId: 2,
    entity: {id: 143, class: 'PIPELINE_RUN'},
    message: 'User launched a SPOT instance',
    incidentType: 'INCREASE',
    value: 20,
    timestamp: '2026-07-13 09:00:00'
  },
  {
    userId: 1,
    ruleId: 1,
    entity: {id: 144, class: 'PIPELINE_RUN'},
    message: 'Run IDLE for >48h with no activity',
    incidentType: 'DEDUCTION',
    value: -50,
    timestamp: '2026-07-13 16:00:00'
  }
];

function parseTimestamp (value) {
  if (!value) {
    return null;
  }
  return new Date(String(value).replace(' ', 'T')).getTime();
}

function buildElements (body = {}, from, to) {
  const {
    userIds = [],
    ruleIds = [],
    incidentTypes = [],
    entities = [],
    page = 0,
    pageSize = 20
  } = body;
  let elements = MOCK_EVENTS.slice();
  if (userIds.length) {
    const matched = elements.filter((event) =>
      userIds.some((id) => Number(id) === Number(event.userId))
    );
    elements = matched.length
      ? matched
      : elements.map((event) => ({...event, userId: userIds[0]}));
  }
  if (ruleIds.length) {
    elements = elements.filter((event) =>
      ruleIds.some((id) => Number(id) === Number(event.ruleId))
    );
  }
  if (incidentTypes.length) {
    elements = elements.filter((event) =>
      incidentTypes.some((type) => type === event.incidentType)
    );
  }
  if (entities.length) {
    elements = elements.filter((event) =>
      event.entity &&
      entities.some((entity) =>
        Number(entity.id) === Number(event.entity.id) &&
        (!entity.class || entity.class === event.entity.class)
      )
    );
  }
  const fromTs = parseTimestamp(from);
  const toTs = parseTimestamp(to);
  if (fromTs !== null) {
    elements = elements.filter((event) => parseTimestamp(event.timestamp) >= fromTs);
  }
  if (toTs !== null) {
    elements = elements.filter((event) => parseTimestamp(event.timestamp) <= toTs);
  }
  const totalCount = elements.length;
  const size = Math.max(1, pageSize);
  const start = Math.max(0, page) * size;
  return {
    elements: elements.slice(start, start + size),
    totalCount
  };
}

// Delete this class when /usage/credits/events/filter backend is ready.
export default class UsageCreditsEventsFilterMock extends UsageCreditsEventsFilter {
  constructor (from, to) {
    super(from, to);
    this.from = from;
    this.to = to;
  }

  async send (body = {}) {
    this._pending = true;
    this.failed = false;
    this.error = undefined;
    this.networkError = undefined;
    try {
      this.update({
        status: 'OK',
        payload: buildElements(body, this.from, this.to)
      });
    } finally {
      this._pending = false;
    }
  }
}
