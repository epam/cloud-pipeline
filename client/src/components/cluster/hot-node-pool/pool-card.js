/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {
  Button,
  Icon,
  Progress
} from 'antd';
import moment from 'moment-timezone';
import classNames from 'classnames';
import DockerImageDetails from './docker-image-details';
import {parseDay} from './edit-hot-node-pool/controls/ScheduleControl';
import {getCapacityReservationStatus} from './edit-hot-node-pool/helpers';
import {
  RESERVATION_STATUS_PENDING,
  RESERVATION_STATUS_APPROVED
} from './edit-hot-node-pool/constants';
import PoolShortDescription from './pool-short-description';
import styles from './pool-card.css';

function capitalized (string) {
  if (!string) {
    return string;
  }
  return `${string[0].toUpperCase()}${string.substring(1).toLowerCase()}`;
}

function displayTime (time) {
  if (!time) {
    return '';
  }
  const localTime = moment.utc(time, 'HH:mm:ss').toDate();
  return moment(localTime).format('HH:mm');
}

const countPostfixes = ['', 'K', 'M', 'G', 'T', 'P'];

function displayCount (count) {
  if (isNaN(count)) {
    return count;
  }
  let countValue = +count;
  let index = 0;
  while (countValue > 1024 && index < countPostfixes.length - 1) {
    index += 1;
    countValue /= 1024;
  }
  if (index === 0) {
    return `${countValue}${countPostfixes[index]}`;
  }
  return `${countValue.toFixed(1)}${countPostfixes[index]}`;
}

function scheduleEntryString (scheduleEntry) {
  if (!scheduleEntry) {
    return null;
  }
  const {
    from,
    fromTime,
    to,
    toTime
  } = scheduleEntry;
  if (!from || !fromTime || !to || !toTime) {
    return null;
  }
  const fromString = `${capitalized(parseDay(from, fromTime))}, ${displayTime(fromTime)}`;
  const toString = `${capitalized(parseDay(to, toTime))}, ${displayTime(toTime)}`;
  return `${fromString} - ${toString}`;
}

function Schedule ({schedule}) {
  if (!schedule) {
    return null;
  }
  const {
    scheduleEntries = []
  } = schedule;
  if (!scheduleEntries.length) {
    return null;
  }
  const entries = scheduleEntries.map(scheduleEntryString);
  return (
    <div>
      {
        entries.map((entry, index) => (
          <div key={index} className={styles.scheduleRow}>
            <span className={classNames(styles.schedule, 'cp-text-not-important')}>
              {entry}
            </span>
          </div>
        ))
      }
    </div>
  );
}

const CP_USE_NODES_COUNT_INFORMATION = 'CP_USE_NODES_COUNT_INFORMATION';

// Keyed by `getCapacityReservationStatus`'s return value, so a status's color and label
// stay in lockstep instead of being picked by two separate expressions.
const RESERVATION_STATUS_PRESENTATION = {
  [RESERVATION_STATUS_PENDING]: {tag: 'warning', label: 'Pending approval'},
  [RESERVATION_STATUS_APPROVED]: {tag: 'success', label: 'Approved'}
};

function PoolCard ({
  awsRegions,
  disabled,
  readOnly = false,
  pool,
  onEdit,
  onRemove,
  onClick,
  nodes,
  router
}) {
  if (!pool) {
    return null;
  }
  const {
    id,
    name,
    schedule,
    count: nodeCount,
    dockerImages = [],
    usage = 0,
    kubeLabels = {}
  } = pool;
  const reservationStatus = getCapacityReservationStatus(pool);
  const {
    [CP_USE_NODES_COUNT_INFORMATION]: cpUseNodesCountInformationLabelValue
  } = kubeLabels;
  const {
    value: cpUseNodesCountInformationLabel = true
  } = cpUseNodesCountInformationLabelValue || {};
  const cpUseNodesCountInformation = String(cpUseNodesCountInformationLabel)
    .toLowerCase() === 'true';
  const poolNodes = (nodes || [])
    .filter(node => node.labels &&
      node.labels.hasOwnProperty('pool_id') &&
      `${node.labels.pool_id}` === `${pool.id}`
    );
  const runs = usage;
  const total = Math.max(usage, poolNodes.length);
  const runsCountLabel = displayCount(runs);
  const totalLabel = displayCount(total);
  const fontSize = total >= 100 ? 10 : 12;
  const navigate = (path) => {
    if (!router) {
      return;
    }
    if (path) {
      router.push(path);
    }
  };
  return (
    <div
      className={
        classNames(
          styles.container,
          'cp-panel-card',
          'cp-hot-node-pool',
          {
            [styles.poolDisabled]: nodeCount === 0,
            'cp-disabled': nodeCount === 0
          }
        )
      }
      onClick={onClick}
    >
      <div className={styles.headerContainer}>
        <div
          className={
            classNames(
              styles.infoBlock,
              'cp-text-not-important',
              {
                'cp-success': runs > 0
              }
            )
          }
          style={{fontSize: `${fontSize}pt`}}
        >
          <div className={styles.progress}>
            <Progress
              type="circle"
              status={runs > 0 ? 'success' : 'active'}
              percent={(runs / (total || 1)) * 100.0}
              width={55}
              strokeWidth={8}
              showInfo={false}
            />
          </div>
          {
            cpUseNodesCountInformation && (<span>
              {runsCountLabel}
            </span>)
          }
          {
            cpUseNodesCountInformation && (<span style={{fontWeight: 'normal', margin: '0 2px'}}>
              /
            </span>)
          }
          <span>
            {totalLabel}
          </span>
        </div>
        <div className={styles.nameBlock}>
          <div className={styles.header}>
            <div className={styles.title}>
              <span className={styles.main}>{name}</span>
              {
                reservationStatus && (
                  <span
                    className={
                      classNames(
                        styles.reservationStatus,
                        'cp-tag',
                        RESERVATION_STATUS_PRESENTATION[reservationStatus].tag
                      )
                    }
                  >
                    {RESERVATION_STATUS_PRESENTATION[reservationStatus].label}
                  </span>
                )
              }
              {
                nodeCount === 0 && (
                  <span className={styles.disabledLabel}>(disabled)</span>
                )
              }
            </div>
            <div className={styles.actions}>
              {!readOnly && (
                <Button
                  disabled={disabled}
                  size="small"
                  onClick={onEdit}
                >
                  <Icon type="edit" />
                </Button>
              )}
              <Button
                disabled={disabled}
                size="small"
                onClick={(e) => {
                  e && e.stopPropagation();
                  navigate(`/cluster/usage?pool=${id}`);
                }}
              >
                <Icon type="area-chart" />
              </Button>
              {!readOnly && (
                <Button
                  disabled={disabled}
                  size="small"
                  type="danger"
                  onClick={onRemove}
                >
                  <Icon type="delete" />
                </Button>
              )}
            </div>
          </div>
          <PoolShortDescription pool={pool} />
        </div>
      </div>
      <div className={styles.images}>
        {
          dockerImages.map((d, index) => (
            <DockerImageDetails
              key={index}
              className={styles.image}
              docker={d}
              onlyImage
            />
          ))
        }
      </div>
      <Schedule schedule={schedule} />
    </div>
  );
}

export default inject('awsRegions')(observer(PoolCard));
