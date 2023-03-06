/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import ChartJS from 'chart.js';
import Chart from './base';
import {
  BarchartDataLabelPlugin,
  ChartClickPlugin
} from './extensions';
import Export from '../export';
import {discounts} from '../discounts';
import {costTickFormatter} from '../utilities';
import {quotaGroupSpendingNames} from '../../quotas/utilities/quota-groups';
import {periodNamesAdjective} from '../../quotas/utilities/quota-periods';
import {
  getAffectiveQuotaPeriods,
  getQuotaGroup
} from './quotas';

ChartJS.Tooltip.positioners.custom = function (elements, eventPosition) {
  const [chart] = elements.map(element => element._chart);
  const chartWidth = chart?.width || 0;
  const chartHeight = chart?.height || 0;
  const width = Math.max(0, ...elements.map(element => element._view?.width || 0));
  const x = Math.max(0, ...elements.map(element => element._view?.x || 0));
  return {
    x: x > chartWidth / 2.0 ? (x - width / 2.0) : (x + width / 2.0),
    y: chartHeight / 2.0
  };
};

function toValueFormat (value) {
  return Math.round((+value || 0) * 100.0) / 100.0;
}

function getValues (data, propertyName = 'value') {
  return data.map(({item}) => toValueFormat(item[propertyName]));
}

function getValuesFromObject (data, groups = [], propertyName = 'value') {
  return groups.map(group => data && data[group] ? data[group][propertyName] : undefined);
}

function getMaximum (...values) {
  const trueMaximum = Math.max(...values.filter(v => !isNaN(v)), 0);
  const extended = trueMaximum * 1.2; // + 20%
  const step = trueMaximum / 10.0;
  const basis = 10 ** Math.floor(Math.log10(step));
  return Math.ceil(extended / basis) * basis;
}

function filterTopData (data, top, dataSample = 'value') {
  const sortedData = Object.keys(data || {})
    .map((key) => ({name: key, item: data[key]}));
  sortedData
    .sort((a, b) => +b.item[dataSample] - (+a.item[dataSample]));
  if (top) {
    return sortedData.filter((o, i) => i < top);
  }
  return sortedData;
}

function getQuotaDatasets (options = {}) {
  const {
    quotas,
    quotaType,
    dataSample,
    groups,
    requestsWithDiscounts = []
  } = options;
  if (!quotas || !quotaType) {
    return [];
  }
  return requestsWithDiscounts
    .map(({request, discount}) => ({
      request,
      discount,
      quotaGroup: getQuotaGroup(request)
    }))
    .filter(dataset => dataset.discount && dataset.quotaGroup)
    .map(dataset => ({
      ...dataset,
      value: discounts.applyDiscountsToObjectProperties(dataset.request.value, dataset.discount),
      quotas: quotas.getSubjectQuotasByTypeAndGroup(quotaType, dataset.quotaGroup),
      quotaPeriods: getAffectiveQuotaPeriods(dataset.request)
    }))
    .filter(dataset => dataset.value && dataset.quotaPeriods.length > 0)
    .map(quotaDatasetCollection => {
      const datasets = [];
      const {
        quotas: quotasData,
        quotaGroup,
        value,
        quotaPeriods
      } = quotaDatasetCollection;
      const title = quotaGroupSpendingNames[quotaGroup];
      const isEmptyData = data => data.filter(o => !!o).length === 0;
      const mainDataset = {
        title,
        data: getValuesFromObject(value, groups, dataSample),
        group: quotaGroup,
        quota: false
      };
      if (!isEmptyData(mainDataset.data)) {
        const quotaDatasets = [];
        quotaPeriods.forEach(period => {
          const quotaDataset = getValuesFromObject(quotasData, groups, period);
          if (!isEmptyData(quotaDataset)) {
            quotaDatasets.push({
              title: `${title} ${periodNamesAdjective[period].toLowerCase()} quota`,
              data: quotaDataset,
              quota: true,
              group: quotaGroup
            });
          }
        });
        for (let i = 0; i < mainDataset.data.length; i++) {
          const hasQuotas = quotaDatasets.some(qDataset => qDataset.data[i]);
          if (!hasQuotas) {
            // no quotas for subject
            mainDataset.data[i] = undefined;
          } else if (!mainDataset.data[i]) {
            // subject has quotas, but doesn't have value
            quotaDatasets.forEach(qDataset => {
              qDataset.data[i] = undefined;
            });
          }
        }
        const filteredQuotaDatasets = quotaDatasets.filter(qDataset => !isEmptyData(qDataset.data));
        if (filteredQuotaDatasets.length > 0 && !isEmptyData(mainDataset.data)) {
          datasets.push(
            mainDataset,
            ...filteredQuotaDatasets
          );
        }
      }
      return datasets;
    })
    .reduce((r, c) => ([...r, ...c]), [])
    .sort((a, b) => Number(b.quota) - Number(a.quota));
}

function BarChartWithQuota (
  {
    axisPosition = 'left',
    requests = [],
    discounts: discountsFn,
    onSelect,
    onScaleSelect,
    title,
    style,
    subChart,
    top = 10,
    valueFormatter = costTickFormatter,
    useImageConsumer = true,
    onImageDataReceived,
    itemNameFn = o => o,
    reportThemes,
    quotas,
    quotaType
  }
) {
  if (!requests) {
    return null;
  }
  const dataSample = 'value';
  const previousDataSample = 'previous';
  const loading = requests.filter(r => r.loading).length > 0;
  const loaded = requests.filter(r => !r.loaded).length === 0;
  const value = loaded ? requests.map(r => r.value || {}) : {};

  const data = discounts.applyGroupedDataDiscounts(
    value,
    discountsFn
  );
  const [error] = requests.filter(r => r.error).map(r => r.error);
  const filteredData = filterTopData(data, top, dataSample);
  const groups = filteredData.map(d => d.name);

  const detailedDatasetsWithQuotas = getQuotaDatasets({
    quotas,
    quotaType,
    groups,
    dataSample,
    requestsWithDiscounts: loaded
      ? requests.map((request, index) => ({request, discount: (discountsFn || [])[index]}))
      : []
  });
  const quotaDatasets = detailedDatasetsWithQuotas.filter(dataset => dataset.quota);
  let detailedDatasets = detailedDatasetsWithQuotas.filter(dataset => !dataset.quota);
  if (detailedDatasets.length === 1) {
    detailedDatasets = [];
  }

  const displayGroups = groups.map(itemNameFn);
  const previousData = getValues(filteredData, previousDataSample);
  const currentData = getValues(filteredData, dataSample);
  const maximum = getMaximum(
    ...previousData,
    ...currentData
  );
  const disabled = isNaN(maximum);
  const chartData = {
    labels: displayGroups,
    datasets: [
      ...quotaDatasets.map((dataset) => ({
        type: 'quota-bar',
        group: dataset.group,
        quota: true,
        label: dataset.title,
        data: dataset.data,
        borderWidth: 2,
        borderColor: reportThemes.quota,
        backgroundColor: reportThemes.quota,
        borderSkipped: '',
        textColor: reportThemes.textColor,
        flagColor: reportThemes.quota,
        textBold: false,
        maxBarThickness: 70,
        yAxisID: 'y-axis',
        showDataLabel: false
      })),
      ...detailedDatasets.map((dataset, index) => ({
        type: 'quota-bar',
        group: dataset.group,
        quota: false,
        label: dataset.title,
        data: dataset.data,
        borderWidth: 2,
        borderColor: reportThemes.getOtherColorForIndex(index % 2),
        backgroundColor: reportThemes.getOtherColorForIndex(index % 2),
        borderSkipped: '',
        textColor: reportThemes.textColor,
        flagColor: reportThemes.getOtherColorForIndex(index % 2),
        textBold: false,
        maxBarThickness: 70,
        yAxisID: 'y-axis',
        showDataLabel: false
      })),
      {
        label: 'Previous',
        type: 'previous-line-bar',
        data: previousData,
        borderWidth: 2,
        borderDash: [4, 4],
        borderColor: reportThemes.blue,
        backgroundColor: reportThemes.blue,
        borderSkipped: '',
        textColor: reportThemes.textColor,
        flagColor: reportThemes.blue,
        textBold: false,
        showDataLabels: false,
        maxBarThickness: 70,
        yAxisID: 'y-axis'
      },
      {
        label: 'Current',
        data: currentData,
        borderWidth: 2,
        borderColor: reportThemes.current,
        backgroundColor: reportThemes.lightCurrent,
        borderSkipped: '',
        textColor: reportThemes.textColor,
        flagColor: reportThemes.current,
        textBold: false,
        maxBarThickness: 70
      }
    ]
  };
  const options = {
    animation: {duration: 0},
    scales: {
      xAxes: [{
        id: 'x-axis',
        stacked: true,
        gridLines: {
          drawOnChartArea: false,
          color: reportThemes.lineColor,
          zeroLineColor: reportThemes.lineColor
        },
        scaleLabel: {
          display: !disabled && subChart,
          labelString: title,
          fontColor: reportThemes.subTextColor
        },
        ticks: {
          fontColor: reportThemes.subTextColor
        }
      }],
      yAxes: [{
        id: 'y-axis',
        position: axisPosition,
        gridLines: {
          display: !disabled,
          color: reportThemes.lineColor,
          zeroLineColor: reportThemes.lineColor
        },
        ticks: {
          display: !disabled,
          beginAtZero: true,
          callback: value => {
            if (value === maximum) {
              return '';
            }
            return valueFormatter(value);
          },
          max: !disabled ? maximum : undefined,
          fontColor: reportThemes.subTextColor
        }
      }]
    },
    title: {
      display: !subChart && !!title,
      text: top ? `${title} (TOP ${top})` : title,
      fontColor: reportThemes.textColor
    },
    legend: {
      display: false
    },
    tooltips: {
      intersect: false,
      mode: 'index',
      position: 'custom',
      itemSort: function ({datasetIndex: a}, {datasetIndex: b}) {
        // reverse tooltip orders
        return b - a;
      },
      callbacks: {
        label: function (tooltipItem, data) {
          const {label} = data.datasets[tooltipItem.datasetIndex];
          const value = valueFormatter(tooltipItem.yLabel);
          if (label) {
            return `${label}: ${value}`;
          }
          return value;
        }
      }
    },
    hover: {
      onHover: function (e) {
        const point = this.getElementsAtXAxis(e);
        e.target.style.cursor = point.length && onSelect
          ? 'pointer'
          : 'default';
      }
    },
    plugins: {
      [BarchartDataLabelPlugin.id]: {
        valueFormatter
      },
      [ChartClickPlugin.id]: {
        handler: onSelect ? index => onSelect({key: groups[index]}) : undefined,
        scaleHandler: onScaleSelect,
        axis: 'x-axis'
      }
    }
  };

  const Container = ({style: cssStyle, children}) => {
    if (useImageConsumer) {
      return (
        <Export.ImageConsumer
          style={cssStyle}
          order={2}
        >
          {children}
        </Export.ImageConsumer>
      );
    }
    return (
      <div style={cssStyle}>
        {children}
      </div>
    );
  };

  return (
    <Container
      style={
        Object.assign(
          {height: '100%', position: 'relative', display: 'block'},
          style
        )
      }
    >
      <Chart
        data={chartData}
        error={error}
        loading={loading}
        type="bar"
        options={options}
        plugins={[
          BarchartDataLabelPlugin.plugin,
          ChartClickPlugin.plugin
        ]}
        useChartImageGenerator={useImageConsumer}
        onImageDataReceived={onImageDataReceived}
      />
    </Container>
  );
}

export default inject('reportThemes', 'quotas')(observer(BarChartWithQuota));
