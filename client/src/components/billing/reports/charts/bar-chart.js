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
import {costTickFormatter} from '../utilities';
import QuotaSummaryChartsTitle from './quota-summary-chart';

function toValueFormat (value) {
  return Math.round((+value || 0) * 100.0) / 100.0;
}

function getValues (data, propertyName = 'value') {
  return data.map(({item}) => toValueFormat(item[propertyName]));
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

function BarChart (
  {
    axisPosition = 'left',
    request,
    discounts: discountsFn,
    data: rawData,
    dataSample = 'value',
    previousDataSample = 'previous',
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
    highlightTickStyle = {}
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
  const filteredData = filterTopData(data, top, dataSample);
  const groups = filteredData.map(d => d.name);
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
        maxBarThickness: 70
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
