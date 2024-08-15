/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import React from 'react';
import {COLORS} from './utils';
import displayDate from '../../../utils/displayDate';
import {percentToHexAlpha} from '../../special/heat-map-chart/utils';

const MAX_CELLS_IN_ROW = 3;

const getColorWithAlpha = (hexColor, value, maximum = 100) => {
  const percent = (value / maximum) * 100;
  return `${hexColor}${percentToHexAlpha(percent)}`;
};

const convertKilobytesToGb = (kb = 0) => {
  const minimalValue = 0.1;
  const Gb = kb / 1024;
  if (Gb > 0 && Gb < minimalValue) {
    return `< ${minimalValue}`;
  }
  return Gb.toFixed(1);
};

function renderCircle (className, style) {
  return (
    <svg style={style} className={className} height="10" width="10">
      <circle cx="5" cy="5" r="4"
        strokeWidth={1}
        fill="currentColor"
      />
    </svg>
  );
};

function renderStatisticsGrid ({
  record,
  measure,
  hoveredItem = {},
  themeConfiguration = {},
  hideDatasets = []
}) {
  const {gpuId, key} = hoveredItem;
  const {gpuUsage = {}, gpuDetails = {}} = record || {};
  const columns = Math.min(MAX_CELLS_IN_ROW, Math.max(1, Object.keys(gpuDetails).length));
  const primaryColor = themeConfiguration['@primary-color'] || '#108ee9';
  const greyColor = themeConfiguration['@color-grey-semi-transparent'] || '#777';
  const renderGrid = ({
    title,
    dataKey,
    valueFn
  }) => {
    if (hideDatasets.includes(dataKey)) {
      return null;
    }
    return (
      <div style={{display: 'flex', flexDirection: 'column', gap: '2px', marginBottom: 5}}>
        <div style={{display: 'flex', justifyContent: 'space-between'}}>
          <span>
            {renderCircle('cp-success', {marginRight: 5})}
            {title}
          </span>
          <span>{Math.round((gpuUsage[dataKey] || {})[measure] || 0)}%</span>
        </div>
        <div style={{
          display: 'grid',
          gridTemplateColumns: `repeat(${columns}, 1fr)`
        }}>
          {Object.entries(gpuDetails)
            .map(([gpuKey, details], index) => {
              const data = details[dataKey];
              if (!data) {
                return;
              }
              const highlighted = key === dataKey && gpuKey === gpuId;
              const borderColor = highlighted ? primaryColor : greyColor;
              return (
                <div
                  key={index}
                  style={{
                    minWidth: 85,
                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center',
                    marginRight: 1,
                    marginBottom: 1,
                    background: getColorWithAlpha(
                      COLORS[dataKey],
                      data[measure]
                    ),
                    border: `${highlighted ? '2px' : '1px'} solid ${borderColor}`
                  }}
                >
                  {valueFn ? valueFn(details, measure) : `${Math.round(data[measure])}%`}
                </div>
              );
            })}
        </div>
      </div>
    );
  };
  return (
    <div>
      {renderGrid({
        title: 'GPU Utilization',
        dataKey: 'gpuUtilization'
      })}
      {renderGrid({
        title: 'GPU Memory',
        dataKey: 'gpuMemoryUtilization',
        valueFn: (details = {}, measure) => {
          const {gpuMemoryUtilization = {}, gpuMemoryUsed = {}} = details;
          const utilization = Math.round(gpuMemoryUtilization[measure] || 0);
          return (
            <div style={{display: 'flex', flexWrap: 'nowrap'}}>
              <span>
                {utilization}%
              </span>
              <span style={{padding: '0 3px'}}>/</span>
              <span>
                {convertKilobytesToGb(Math.round(gpuMemoryUsed[measure] || 0))}Gb
              </span>
            </div>
          );
        }
      })}
    </div>
  );
}

export const renderTimelineTooltip = ({
  hoveredItems,
  measure,
  themeConfiguration,
  hideDatasets = []
}) => {
  const hoveredItem = hoveredItems[0];
  if (!hoveredItem?.item?.record) {
    return null;
  }
  const {record} = hoveredItem.item;
  return (
    <div style={{display: 'flex', flexDirection: 'column', gap: '5px'}}>
      <div>
        <span style={{minWidth: 35, display: 'inline-block'}}>From:</span>
        <span>{displayDate(record.startTime)}</span>
      </div>
      <div>
        <span style={{minWidth: 35, display: 'inline-block'}}>To:</span>
        <span>
          {displayDate(record.endTime)}
        </span>
      </div>
      {renderStatisticsGrid({
        record,
        measure,
        themeConfiguration,
        hideDatasets
      })}
    </div>
  );
};

export const renderHeatmapTooltip = ({
  hoveredItem,
  measure,
  metrics,
  themeConfiguration,
  hideDatasets
}) => {
  const record = (metrics.charts || [])[hoveredItem.recordIdx];
  if (!record) {
    return null;
  }
  return (
    <div style={{display: 'flex', flexDirection: 'column'}}>
      <div>
        <span style={{minWidth: 35, display: 'inline-block'}}>From:</span>
        <span>{displayDate(record.startTime)}</span>
      </div>
      <div>
        <span style={{minWidth: 35, display: 'inline-block'}}>To:</span>
        <span>
          {displayDate(record.endTime)}
        </span>
      </div>
      {renderStatisticsGrid({
        record,
        measure,
        hoveredItem,
        themeConfiguration,
        hideDatasets
      })}
    </div>
  );
};
