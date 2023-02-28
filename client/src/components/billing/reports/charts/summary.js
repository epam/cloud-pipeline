/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import Chart from './base';
import {
  SummaryChart,
  DataLabelPlugin,
  PointDataLabelPlugin,
  VerticalLinePlugin
} from './extensions';
import Export from '../export';
import {costTickFormatter} from '../utilities';
import {discounts} from '../discounts';
import {getCurrentDate} from '../../../special/periods';
import getQuotaDatasets from './get-quotas-datasets';
import moment from 'moment-timezone';

const Display = {
  accumulative: 'accumulative',
  fact: 'fact'
};

function dataIsEmpty (data) {
  const itemIsEmpty = item => {
    if (item === undefined) {
      return true;
    }
    if (!Number.isNaN(Number(item))) {
      return false;
    }
    return Number.isNaN(Number(item.y));
  };
  return !data || data.filter((d) => !itemIsEmpty(d)).length === 0;
}

function generateEmptySet (filters) {
  if (!filters) {
    return null;
  }
  const {
    start: initial,
    end,
    tick
  } = filters;
  const emptySet = [];
  let start = moment(initial);
  let unit = 'day';
  if (tick === '1M') {
    unit = 'M';
    start = moment(start).startOf('M');
  }
  while (start <= end) {
    emptySet.push({
      dateValue: moment(start),
      key: moment(start).format('YYYY-MM-DD')
    });
    start = start.add(1, unit);
  }
  return emptySet;
}

function fillSet (filters, data) {
  const set = generateEmptySet(filters);
  if (!set || !set.length || !data || !data.length) {
    return data;
  }
  for (let i = 0; i < data.length; i++) {
    const item = data[i];
    const key = item.dateValue.format('YYYY-MM-DD');
    const [index] = set
      .map(({key}, index) => ({key, index}))
      .filter(e => e.key === key)
      .map(e => e.index);
    if (index >= 0) {
      set.splice(index, 1, item);
    }
  }
  set.forEach(e => delete e.key);
  return set;
}

function generateLabels (data, filters = {}) {
  if (!data || !data.length) {
    return {labels: []};
  }
  const {
    tick
  } = filters;
  const currentDate = getCurrentDate();
  let currentDateIndex;
  const checkUnit = (test, unit) => test.get(unit) === currentDate.get(unit);
  const checkUnits = (test, ...units) =>
    units.map(u => checkUnit(test, u)).reduce((r, c) => r && c, true);
  let isCurrentDateFn = (test) => checkUnits(test, 'Y', 'M', 'D');
  let format = 'DD MMM';
  let fullFormat = 'DD MMM YYYY';
  let tooltipFormat = 'MMMM DD, YYYY';
  let previousDateFn = date => moment(date).add(-1, 'M');
  if (tick === '1M') {
    format = 'MMM';
    fullFormat = 'MMM YYYY';
    tooltipFormat = 'MMMM YYYY';
    isCurrentDateFn = (test) => checkUnits(test, 'Y', 'M');
    previousDateFn = date => moment(date).add(-1, 'Y');
  }
  const labels = [];
  let year;
  for (let i = 0; i < data.length; i++) {
    const date = data[i].dateValue;
    if (isCurrentDateFn(date)) {
      currentDateIndex = i;
    }
    let label = date.format(format);
    if (!year) {
      year = date.get('y');
    } else if (year !== date.get('y')) {
      year = date.get('y');
      label = date.format(fullFormat);
    }
    if (labels.indexOf(label) >= 0) {
      label = false;
    }
    labels.push({
      text: label,
      date,
      tooltip: date.format(tooltipFormat),
      previousTooltip: previousDateFn(date).format(tooltipFormat)
    });
  }
  return {
    labels,
    currentDateIndex
  };
}

/**
 * @typedef {Object} DatasetOptions
 * @property {boolean} [showPoints=true]
 * @property {number} currentDateIndex
 * @property {number} [borderWidth=2]
 * @property {boolean} [dashed=false]
 * @property {boolean} [fill=false]
 * @property {string} [borderColor]
 * @property {string} [backgroundColor=transparent]
 * @property {boolean} [isPrevious=false]
 * @property {boolean} [showTooltip=true]
 */

/**
 * @param {number[]|{x: number, y: number}[]} data
 * @param {string} title
 * @param {string} type
 * @param {string} color
 * @param {DatasetOptions} options
 * @returns {*|boolean}
 */
function extractDataSet (data, title, type, color, options = {}) {
  if (dataIsEmpty(data)) {
    return false;
  }
  const {
    showPoints = true,
    currentDateIndex,
    borderWidth = 2,
    fill = false,
    borderColor = color,
    backgroundColor = 'transparent',
    isPrevious = false,
    showTooltip = true,
    dashed = false,
    ...restOptions
  } = options;
  const mapItem = (item, index) => {
    if (typeof item === 'number') {
      return {y: item, x: index};
    }
    return item;
  };
  return {
    ...restOptions,
    [DataLabelPlugin.noDataIgnoreOption]: options[DataLabelPlugin.noDataIgnoreOption],
    label: title,
    type,
    isPrevious,
    showTooltip,
    data: (data || []).map(mapItem),
    fill,
    backgroundColor,
    borderColor,
    borderWidth,
    borderDash: dashed ? [4, 4] : undefined,
    pointRadius: data.map((e, index) => showPoints && index === currentDateIndex ? 2 : 0),
    pointBackgroundColor: color,
    cubicInterpolationMode: 'monotone'
  };
}

function extractDatasetData (dataset, data) {
  const mapValue = (value) => {
    if (value === undefined || value === null || Number.isNaN(Number(value))) {
      return Number.NaN;
    }
    return Number(value);
  };
  return (data || [])
    .map(item => typeof dataset.value === 'function' ? dataset.value(item) : undefined)
    .map(mapValue);
}

function getProcessedDatasetIsPrevious (dataset = {}) {
  const {
    isPrevious = false
  } = dataset.options || {};
  return isPrevious;
}

function getProcessedDatasetTitle (dataset = {}) {
  const {
    isPrevious = false,
    title = !isPrevious ? 'Current period' : 'Previous period',
    subTitle
  } = dataset.options || {};
  if (!subTitle) {
    return title;
  }
  return `${title} (${subTitle})`;
}

function getProcessedDatasetType (dataset = {}) {
  const {
    isPrevious = false,
    datasetType = isPrevious ? SummaryChart.previous : SummaryChart.current
  } = dataset.options || {};
  return datasetType;
}

function getProcessedDatasetColor (dataset = {}, reportThemes = {}) {
  const {
    isPrevious = false,
    color = (isPrevious ? reportThemes.previous : reportThemes.current)
  } = dataset.options || {};
  return color;
}

const DefaultCurrentDataset = {
  accumulative: {
    value: (item) => item.value,
    options: {
      borderWidth: 3
    }
  },
  fact: {
    value: (item) => item.cost,
    options: {
      subTitle: 'cost'
    }
  }
};

const DefaultPreviousDataset = {
  accumulative: {
    value: (item) => item.previous,
    options: {
      isPrevious: true
    }
  },
  fact: {
    value: (item) => item.previousCost,
    options: {
      isPrevious: true,
      subTitle: 'cost'
    }
  }
};

function Summary (
  {
    title,
    style,
    compute,
    storages,
    computeDiscounts,
    storagesDiscounts,
    quotas,
    quota: showQuota = true,
    display = Display.accumulative,
    reportThemes,
    datasets = [DefaultCurrentDataset, DefaultPreviousDataset]
  }
) {
  const pending = compute?.pending || storages?.pending;
  const loaded = compute?.loaded && storages?.loaded;
  const error = compute?.error || storages?.error;
  const filters = compute?.filters || storages?.filters;
  const summary = discounts.joinSummaryDiscounts(
    [compute, storages],
    [computeDiscounts, storagesDiscounts]
  );
  const data = summary ? fillSet(filters, summary.values || []) : [];
  const {labels, currentDateIndex} = generateLabels(data, filters);
  const processedDatasets = datasets
    .map((aDataset) => display === Display.accumulative ? aDataset.accumulative : aDataset.fact)
    .map((aDataset) => ({
      dataset: aDataset,
      current: !aDataset.options || !aDataset.options.isPrevious,
      data: extractDatasetData(aDataset, data)
    }));
  const maximum = Math.max(
    ...processedDatasets.map((aDataset) => Math.max(
      ...(aDataset.data || []).filter((anItem) => !Number.isNaN(Number(anItem))),
      0
    )),
    0
  );
  const shouldDisplayQuotas = display === Display.accumulative && showQuota;
  const quotaDatasets = shouldDisplayQuotas
    ? getQuotaDatasets(compute, storages, quotas, data, maximum)
    : [];
  const disabled = !processedDatasets.some(aDataset => aDataset.data.length > 0);
  const loading = pending && !loaded;
  const chartDatasets = [
    ...processedDatasets.map((processed, index) => extractDataSet(
      processed.data,
      getProcessedDatasetTitle(processed.dataset),
      display === Display.fact ? 'bar' : getProcessedDatasetType(processed.dataset),
      getProcessedDatasetColor(processed.dataset, reportThemes),
      {
        currentDateIndex,
        borderWidth: 2,
        backgroundColor: display === Display.fact
          ? getProcessedDatasetColor(processed.dataset, reportThemes)
          : 'transparent',
        stack: `stack-${index}`,
        ...(processed.dataset.options || {}),
        isPrevious: getProcessedDatasetIsPrevious(processed.dataset)
      }
    )),
    ...quotaDatasets.map(quotaDataset => extractDataSet(
      quotaDataset.data,
      quotaDataset.title,
      SummaryChart.quota,
      reportThemes.quota,
      {
        showPoints: false,
        currentDateIndex,
        [DataLabelPlugin.noDataIgnoreOption]: true
      }
    ))
  ].filter(Boolean);
  const dataConfiguration = {
    labels: labels.map(l => l.text),
    datasets: chartDatasets
  };
  const options = {
    animation: {duration: 0},
    title: {
      display: !!title,
      text: title,
      fontColor: reportThemes.textColor
    },
    scales: {
      xAxes: [{
        gridLines: {
          drawOnChartArea: false,
          color: reportThemes.lineColor,
          zeroLineColor: reportThemes.lineColor
        },
        ticks: {
          display: true,
          maxRotation: 45,
          callback: (date) => date || '',
          fontColor: reportThemes.textColor
        },
        offset: true
      }],
      yAxes: [{
        gridLines: {
          display: !disabled,
          color: reportThemes.lineColor,
          zeroLineColor: reportThemes.lineColor
        },
        ticks: {
          min: 0,
          display: !disabled,
          callback: o => costTickFormatter(o),
          fontColor: reportThemes.textColor
        },
        stacked: display === Display.fact
      }]
    },
    legend: {
      display: false
    },
    tooltips: {
      intersect: false,
      mode: 'index',
      axis: 'x',
      filter: function ({yLabel}) {
        return !isNaN(yLabel);
      },
      callbacks: {
        title: function () {
          return undefined;
        },
        label: function (tooltipItem, chartData) {
          const {
            label,
            type,
            isPrevious,
            data: items,
            showTooltip,
            tooltipValue = (o) => o
          } = chartData.datasets[tooltipItem.datasetIndex];
          if (!showTooltip) {
            return undefined;
          }
          let value = costTickFormatter(tooltipItem.yLabel);
          if (type === SummaryChart.quota) {
            const {quota} = (items || [])[tooltipItem.index || 0];
            value = quota ? costTickFormatter(quota) : value;
            return `${label || 'Quota'}: ${value}`;
          }
          const {xLabel: defaultTitle, index} = tooltipItem;
          let displayLabel = label;
          if (index >= 0 && index < labels.length) {
            const {tooltip, previousTooltip} = labels[index];
            if (type === SummaryChart.previous || isPrevious) {
              displayLabel = previousTooltip || defaultTitle;
            } else {
              displayLabel = tooltip || defaultTitle;
            }
          }
          value = tooltipValue(value, data[index]);
          if (displayLabel) {
            return `${displayLabel}: ${value}`;
          }
          return value;
        }
      }
    },
    plugins: {
      [VerticalLinePlugin.id]: {
        index: currentDateIndex,
        color: reportThemes.previous
      },
      [PointDataLabelPlugin.id]: {
        index: currentDateIndex,
        textColor: reportThemes.textColor,
        background: reportThemes.backgroundColor
      }
    }
  };
  return (
    <Export.ImageConsumer
      style={style}
      order={1}
    >
      <Chart
        error={error}
        data={dataConfiguration}
        loading={loading}
        type="summary"
        options={options}
        plugins={[
          PointDataLabelPlugin.plugin,
          VerticalLinePlugin.plugin
        ]}
      />
    </Export.ImageConsumer>
  );
}

export default inject('reportThemes', 'quotas')(observer(Summary));
export {Display};
