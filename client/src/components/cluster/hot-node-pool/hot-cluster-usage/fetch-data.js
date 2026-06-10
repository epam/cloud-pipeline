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

import dayjs from '../../../../utils/dayjs';
import {Period, getPeriod} from '../../../special/periods';
import poolsRequest from '../../../../models/cluster/HotNodePools';
import HotNodePoolUsage from '../../../../models/cluster/HotNodePoolUsage';
import displayDate from '../../../../utils/displayDate';

function getNumber(o) {
  if (o === undefined) {
    return undefined;
  }
  const number = Number(o);
  if (Number.isNaN(number) || !Number.isFinite(number)) {
    return 0;
  }
  return number;
}

const resourceSharingKeys = ['activeRunsCount', 'pendingRunsCount', 'requestsStats'];

function isResourceSharingRecord(record) {
  if (typeof record === 'object') {
    return resourceSharingKeys.some((k) => Object.hasOwn(record, k));
  }
  return false;
}

function isResourceSharingRecords(records = []) {
  return records.some(isResourceSharingRecord);
}

function processPoolUsage(poolId, data, pools = [], options = {}) {
  const poolUsage = (data || []).find((o) => Number(o.poolId) === Number(poolId)) || {records: []};
  const {periodType = Period.day, start, end} = options;
  const {records = []} = poolUsage;
  const pool = pools.find((p) => Number(p.id) === Number(poolId));
  const format = periodType === Period.day ? 'YYYY-MM-DD HH:mm' : 'YYYY-MM-DD';
  const labelFormat = periodType === Period.day ? 'HH:mm' : 'D';
  const step = periodType === Period.day ? 'hour' : 'day';
  const tooltip = (record) => {
    if (record) {
      if (periodType === Period.day) {
        const f = (d) => displayDate(d, labelFormat);
        return `${f(record.periodStart)}-${f(record.periodEnd)}`;
      }
      return displayDate(record.periodStart, 'MMMM D, YYYY');
    }
    return undefined;
  };
  const ticks = [];
  const mainTicks = [];
  let tick = dayjs(start);
  while (tick <= end) {
    ticks.push({
      tick: dayjs(tick),
      show: true,
      display: tick.format(labelFormat),
    });
    mainTicks.push({
      tick: dayjs(tick),
      show: true,
      display: tick.format(labelFormat),
    });
    if (periodType === Period.day) {
      ticks.push({
        tick: dayjs(tick),
        show: false,
        display: dayjs(tick).add(0.5, step).format(labelFormat),
      });
    }
    tick = tick.add(1, step);
  }
  const processRecordForTick = (tick) => {
    const record = records.find(
      (o) => displayDate(o.periodStart, format) === tick.tick.format(format),
    );
    const extractRequestsStats = (field, properties) => {
      if (record && record.requestsStats) {
        const {
          active: activeProp = `${field}Active`,
          pending: pendingProp = `${field}Pending`,
          total: totalProp = `${field}Total`,
          converter = (o) => o,
        } = properties || {};
        const stats = record.requestsStats[field] || {};
        const {active = 0, pending = 0, total = 0} = stats || {};
        return {
          [activeProp]: converter(active),
          [pendingProp]: converter(pending),
          [totalProp]: converter(total),
        };
      }
      return {};
    };
    return {
      ...record,
      date: tick.tick,
      measureTime: tick.display,
      displayTick: tick.show,
      tooltip: tooltip(record),
      poolUtilization: record ? getNumber(record.utilization) : undefined,
      poolUsage: record ? getNumber(record.occupiedNodesCount) : undefined,
      poolLimit: record ? getNumber(record.nodesCount) : undefined,
      activeRunsCount: record ? getNumber(record.activeRunsCount) : undefined,
      pendingRunsCount: record ? getNumber(record.pendingRunsCount) : undefined,
      ...extractRequestsStats('cpu', {
        active: 'activeCPUCount',
        pending: 'pendingCPUCount',
        total: 'totalCPUCount',
      }),
      ...extractRequestsStats('memory', {
        active: 'activeMemoryCount',
        pending: 'pendingMemoryCount',
        total: 'totalMemoryCount',
      }),
      ...extractRequestsStats('nvidia.com/gpu', {
        active: 'activeGPUCount',
        pending: 'pendingGPUCount',
        total: 'totalGPUCount',
      }),
    };
  };
  const processedRecords = ticks.map(processRecordForTick);
  const mainProcessedRecords = mainTicks.map(processRecordForTick);
  return {
    poolId,
    pool,
    poolName: pool ? pool.name : `Pool #${poolId}`,
    records: processedRecords,
    originalRecords: mainProcessedRecords,
    resourceSharingPool: isResourceSharingRecords(records),
  };
}

export default async function fetchData(periodType, period) {
  const {start, end} = getPeriod(periodType, period);
  const utcDate = (date) => dayjs(date).utc().format('YYYY-MM-DD HH:mm:ss.SSS');
  const from = utcDate(start);
  const to = utcDate(end);
  const interval = periodType === Period.day ? 'HOURS' : 'DAYS';
  const request = new HotNodePoolUsage();
  await Promise.all([
    poolsRequest.fetch(),
    request.send({
      from,
      to,
      interval,
    }),
  ]);
  if (poolsRequest.error) {
    throw new Error(poolsRequest.error);
  }
  if (request.error) {
    throw new Error(request.error);
  }
  const pools = (poolsRequest.value || []).map((o) => ({...o}));
  const rawData = request.value || [];
  const poolIds = [
    ...new Set([...pools.map((o) => Number(o.id)), ...rawData.map((o) => Number(o.poolId))]),
  ];
  return poolIds
    .map((poolId) => processPoolUsage(poolId, rawData, pools, {periodType, start, end}))
    .sort((a, b) => a.poolName.localeCompare(b.poolName));
}
