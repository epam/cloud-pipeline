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
  BarchartDataLabelPlugin,
  ChartClickPlugin,
  HighlightTicksPlugin
} from './extensions';
import Export from '../export';
import {discounts} from '../discounts';
import {costTickFormatter, renderCustomTooltip} from '../utilities';
import QuotaSummaryChartsTitle from './quota-summary-chart';

function toValueFormat (value) {
  if (value === undefined || Number.isNaN(Number(value))) {
    return 0;
  }
  return Math.round((+value || 0) * 100.0) / 100.0;
}

function getItemValue (item, ...keys) {
  const [key, ...rest] = keys;
  if (!item || !key || !Object.prototype.hasOwnProperty.call(item, key)) {
    return undefined;
  }
  const value = item[key];
  if (!Number.isNaN(Number(value))) {
    return value;
  }
  if (typeof value === 'object' && rest.length > 0) {
    return getItemValue(value, ...rest);
  }
  return undefined;
}

function getItemSampleValue (item, sample) {
  if (typeof sample === 'string') {
    return getItemValue(item, ...sample.split('.'));
  }
  if (Array.isArray(sample)) {
    const values = sample.map((singleSample) => getItemSampleValue(item, singleSample));
    if (!values.some((aValue) => aValue !== undefined && !Number.isNaN(Number(aValue)))) {
      return undefined;
    }
    return values.reduce((r, c) => (r || 0) + (c || 0), 0);
  }
  if (typeof sample === 'function') {
    return sample(item);
  }
  return undefined;
}

function getValues (data, propertyName = 'value') {
  return data.map(({item}) => toValueFormat(getItemSampleValue(item, propertyName)));
}

function getMaximum (...values) {
  const trueMaximum = Math.max(...values.filter(v => !isNaN(v)), 0);
  const extended = trueMaximum * 1.2; // + 20%
  const step = trueMaximum / 10.0;
  if (step === 0) {
    return 1.0;
  }
  const basis = 10 ** Math.floor(Math.log10(step));
  return Math.ceil(extended / basis) * basis;
}

function filterTopData (data, top, dataSample = 'value') {
  const sortedData = Object.keys(data || {})
    .map((key) => ({
      name: key,
      item: data[key],
      value: getItemSampleValue(data[key], dataSample)
    }))
    .map((item) => ({
      ...item,
      value: item.value === undefined ? -Infinity : item.value
    }));
  sortedData
    .sort((a, b) => b.value - a.value);
  if (top) {
    return sortedData.filter((o, i) => i < top);
  }
  return sortedData;
}

const DefaultCurrentDataset = {
  sample: 'value'
};
const DefaultPreviousDataset = {
  sample: 'previous',
  isPrevious: true
};

function BarChart (
  {
    axisPosition = 'left',
    request,
    discounts: discountsFn,
    data: rawData,
    datasets = [DefaultCurrentDataset, DefaultPreviousDataset],
    onSelect,
    onScaleSelect,
    title,
    style,
    subChart,
    subChartTitleStyle,
    top = 10,
    topDescription,
    valueFormatter = costTickFormatter,
    useImageConsumer = true,
    onImageDataReceived,
    itemNameFn = o => o,
    reportThemes,
    displayQuotasSummary,
    quotaGroup,
    highlightTickFn,
    highlightTickStyle = {},
    extraTooltipForItem = ((o) => undefined),
    renderTooltipFn
  }
) {
  if (!request) {
    return null;
  }
  const loading = Array.isArray(request)
    ? (request.filter(r => r.loading).length > 0)
    : (request.pending && !request.loaded);
  const loaded = Array.isArray(request)
    ? (request.filter(r => !r.loaded).length === 0)
    : (request.loaded);
  const value = rawData ||
    (
      Array.isArray(request)
        ? (loaded ? request.map(r => r.value || {}) : {})
        : (loaded ? (request.value || {}) : {})
    );
  const data = discounts.applyGroupedDataDiscounts(
    value,
    discountsFn
  );
  const requests = Array.isArray(request) ? request : [request];
  const discountsArray = Array.isArray(discountsFn) ? discountsFn : [discountsFn];
  const [error] = Array.isArray(request)
    ? request.filter(r => r.error).map(r => r.error)
    : [request.error];
  const currentDataset = datasets.find((o) => o.main) ||
    datasets.find((o) => !o.isPrevious) ||
    datasets[0];
  const filteredData = filterTopData(data, top, currentDataset ? currentDataset.sample : 'value');
  const groups = filteredData.map(d => d.name);
  const displayGroups = groups.map(itemNameFn);
  const processedDatasets = datasets.map((dataset) => {
    const {
      isPrevious = false,
      sample,
      ...options
    } = dataset;
    const {
      title,
      ...rest
    } = options;
    const data = getValues(filteredData, sample);
    return {
      label: title || (isPrevious ? 'Previous' : 'Current'),
      type: isPrevious ? 'previous-line-bar' : 'bar',
      data,
      borderWidth: 2,
      borderColor: isPrevious ? reportThemes.blue : reportThemes.current,
      backgroundColor: isPrevious ? reportThemes.blue : reportThemes.lightCurrent,
      borderSkipped: '',
      textColor: reportThemes.textColor,
      flagColor: isPrevious ? reportThemes.blue : reportThemes.current,
      textBold: false,
      maxBarThickness: 70,
      borderDash: isPrevious ? [4, 4] : undefined,
      showDataLabels: isPrevious ? false : undefined,
      maximum: Math.max(0, ...data),
      isPrevious,
      ...rest
    };
  }).sort((a, b) => {
    if (a.isPrevious) {
      return -1;
    }
    if (b.isPrevious) {
      return 1;
    }
    return 0;
  });
  const maximum = getMaximum(
    ...processedDatasets.filter(dataset => dataset.data.length > 0).map(dataset => dataset.maximum)
  );
  const disabled = isNaN(maximum);
  const chartData = {
    labels: displayGroups,
    datasets: processedDatasets
  };
  const getTitle = () => {
    if (top && topDescription) {
      return `${title} (TOP ${top}, ${topDescription})`;
    }
    if (top) {
      return `${title} (TOP ${top})`;
    }
    return title;
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
        ticks: {
          fontColor: reportThemes.subTextColor,
          major: Object.assign({
            enabled: !!highlightTickFn,
            fontStyle: 'normal',
            fontColor: reportThemes.subTextColor
          }, highlightTickStyle)
        }
      }],
      yAxes: [{
        stacked: true,
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
      text: getTitle(),
      fontColor: reportThemes.textColor
    },
    legend: {
      display: false
    },
    tooltips: {
      intersect: false,
      mode: 'index',
      itemSort: function ({datasetIndex: a}, {datasetIndex: b}) {
        // reverse tooltip orders
        return b - a;
      },
      enabled: !renderTooltipFn,
      ...(renderTooltipFn && {
        custom: function (model) {
          const item = model.dataPoints && model.dataPoints.length > 0
            ? filteredData[model.dataPoints[0].index]
            : undefined;
          renderCustomTooltip(item, model, this._chart, renderTooltipFn);
        }
      }),
      callbacks: {
        label: function (tooltipItem, chart) {
          const dataset = chart.datasets[tooltipItem.datasetIndex];
          const {
            label,
            showTooltip = true,
            tooltipValue = (o) => o
          } = dataset || {};
          const {
            index
          } = tooltipItem;
          if (!showTooltip) {
            return false;
          }
          const value = valueFormatter(tooltipItem.yLabel);
          const item = filteredData[index];
          const display = tooltipValue(value, item ? item.item : undefined);
          if (label) {
            return `${label}: ${display}`;
          }
          return display;
        },
        beforeFooter: function (tooltips, chart) {
          if (!extraTooltipForItem) {
            return undefined;
          }
          const {
            index = 0
          } = tooltips[0] || {};
          const item = filteredData[index];
          return extraTooltipForItem(item ? item.item : undefined);
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
      [HighlightTicksPlugin.id]: {
        highlightTickFn,
        request,
        axis: 'x-axis'
      },
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
          {
            height: '100%',
            position: 'relative',
            display: 'flex',
            flexDirection: 'column'
          },
          style
        )
      }
    >
      <div style={{flex: 1, overflow: 'hidden'}}>
        <Chart
          data={chartData}
          error={error}
          loading={loading}
          type="bar"
          options={options}
          plugins={[
            BarchartDataLabelPlugin.plugin,
            ChartClickPlugin.plugin,
            HighlightTicksPlugin.plugin
          ]}
          useChartImageGenerator={useImageConsumer}
          onImageDataReceived={onImageDataReceived}
        />
      </div>
      {
        subChart && (
          <QuotaSummaryChartsTitle
            onClick={onScaleSelect}
            style={subChartTitleStyle}
            displayQuotasSummary={displayQuotasSummary}
            data={rawData}
            requests={requests}
            discounts={discountsArray}
            quotaGroup={quotaGroup}
          >
            {title}
          </QuotaSummaryChartsTitle>
        )
      }
    </Container>
  );
}

export default inject('reportThemes')(observer(BarChart));
