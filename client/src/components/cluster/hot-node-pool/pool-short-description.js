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

import React from 'react';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import AWSRegionTag from '../../special/AWSRegionTag';
import {getSpotTypeName} from '../../special/spot-instance-names';
import styles from './pool-short-description.css';

function PoolShortDescription (
  {
    className,
    pool,
    cloudRegionsInfo,
    style
  }
) {
  if (!pool) {
    return null;
  }
  const regions = cloudRegionsInfo && cloudRegionsInfo.loaded
    ? cloudRegionsInfo.value
    : [];
  const {
    autoscaled,
    count: nodeCount,
    minSize,
    maxSize,
    instanceType,
    instanceDisk,
    regionId,
    priceType
  } = pool;
  const region = (regions || []).find(r => r.id === regionId);
  const provider = region ? region.provider : undefined;
  const isSpot = /^spot$/i.test(priceType);
  return (
    <div
      className={
        classNames(
          styles.instance,
          className
        )
      }
      style={style}
    >
      <AWSRegionTag regionId={regionId} />
      {
        !autoscaled && nodeCount > 0
          ? (
            <span className={styles.count}>
              {nodeCount} node{nodeCount === 1 ? '' : 's'}
            </span>
          )
          : undefined
      }
      {
        autoscaled && Number(maxSize) >= 1 && (
          <span className={styles.count}>
            Autoscaled ({minSize} - {maxSize} nodes)
          </span>
        )
      }
      {
        autoscaled && !maxSize && (
          <span className={styles.count}>
            Autoscaled ({minSize} - ... nodes)
          </span>
        )
      }
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
  );
}

export default inject('cloudRegionsInfo')(observer(PoolShortDescription));
