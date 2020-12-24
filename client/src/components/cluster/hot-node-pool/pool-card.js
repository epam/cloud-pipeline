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
import {getSpotTypeName} from '../../special/spot-instance-names';
import AWSRegionTag from '../../special/AWSRegionTag';
import DockerImageDetails from './docker-image-details';
import {parseDay} from './schedule-control';
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
            <span className={styles.schedule}>
              {entry}
            </span>
          </div>
        ))
      }
    </div>
  );
}

function PoolCard ({
  awsRegions,
  disabled,
  pool,
  onEdit,
  onRemove,
  onClick,
  nodes
}) {
  if (!pool) {
    return null;
  }
  const regions = awsRegions.loaded ? awsRegions.value : [];
  const {
    name,
    schedule,
    count: nodeCount,
    instanceType,
    instanceDisk,
    regionId,
    priceType,
    dockerImages = []
  } = pool;
  const region = (regions || []).find(r => r.id === regionId);
  const provider = region ? region.provider : undefined;
  const isSpot = /^spot$/i.test(priceType);
  const poolNodes = (nodes || [])
    .filter(node => node.labels &&
      node.labels.hasOwnProperty('pool_id') &&
      `${node.labels.pool_id}` === `${pool.id}`
    );
  const runNodes = poolNodes
    .filter(node => node.labels &&
      node.labels.hasOwnProperty('runid') &&
      !/^p-/i.test(`${node.labels.runid}`)
    );
  const runs = runNodes.length;
  const total = poolNodes.length;
  const runsCountLabel = displayCount(runs);
  const totalLabel = displayCount(total);
  const fontSize = total >= 100 ? 10 : 12;
  return (
    <div
      className={styles.container}
      onClick={onClick}
    >
      <div className={styles.headerContainer}>
        <div
          className={
            classNames(
              styles.infoBlock,
              {[styles.hasRuns]: runs > 0}
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
          <span>
            {runsCountLabel}
          </span>
          <span style={{fontWeight: 'normal', margin: '0 2px'}}>
            /
          </span>
          <span>
            {totalLabel}
          </span>
        </div>
        <div className={styles.nameBlock}>
          <div className={styles.header}>
            <div className={styles.title}>
              <span className={styles.main}>{name}</span>
            </div>
            <div className={styles.actions}>
              <Button
                disabled={disabled}
                size="small"
                onClick={onEdit}
              >
                <Icon type="edit" />
              </Button>
              <Button
                disabled={disabled}
                size="small"
                type="danger"
                onClick={onRemove}
              >
                <Icon type="delete" />
              </Button>
            </div>
          </div>
          <div className={styles.instance}>
            <AWSRegionTag regionId={regionId} />
            <span className={styles.count}>
              {nodeCount} node{nodeCount > 1 ? 's' : ''}
            </span>
            <span className={styles.type}>
              {instanceType}
            </span>
            <span className={styles.priceType}>
              {getSpotTypeName(isSpot, provider)}
            </span>
            <span className={styles.count}>
              {instanceDisk} GB
            </span>
          </div>
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
