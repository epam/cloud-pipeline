/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {costTickFormatter} from '../utilities';
import {fadeout} from '../../../../themes/utilities/color-utilities';

function getMaximum (values) {
  const trueMaximum = Math.max(...values.filter(v => !isNaN(v)), 0);
  const extended = trueMaximum * 1.2; // + 20%
  const step = trueMaximum / 10.0;
  const basis = 10 ** Math.floor(Math.log10(step));
  return Math.ceil(extended / basis) * basis;
}

function StorageLayers (
  {
    highlightedLabel,
    axisPosition = 'left',
    request,
    data: rawData,
    onSelect,
    onScaleSelect,
    title,
    style,
    subChart,
    top,
    valueFormatter = costTickFormatter,
    useImageConsumer = true,
    onImageDataReceived,
    reportThemes,
    highlightTickFn,
    highlightTickStyle = {},
    loading
  }
) {
  // todo: connect to API and get rid of mocks
  // if (!request) {
  //   return null;
  // }
  const totals = rawData.datasets
    .filter(dataset => !dataset.hidden)
    .reduce((acc, current) => {
      acc = current.data.map((value, i) => value + (acc[i] || 0));
      return acc;
    }, []);
  const maximum = getMaximum(totals);
  const disabled = isNaN(maximum);
  const {
    aggregates
  } = rawData;
  const chartData = {
    labels: rawData.labels,
    datasets: rawData.datasets.map((dataset, index) => {
      const baseColors = Array(dataset.data.length)
        .fill(reportThemes.colors[index]);
      const backgroundColors = baseColors
        .map((color, index) => highlightedLabel === index
          ? color
          : fadeout(color, 0.75)
        );
      return {
        ...dataset,
        borderWidth: 2,
        borderDash: [4, 4],
        borderColor: baseColors,
        backgroundColor: backgroundColors,
        flagColor: baseColors[index],
        textColor: reportThemes.textColor
      };
    })
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
      text: top ? `${title} (TOP ${top})` : title,
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
        },
        footer: function (items) {
          const total = items.reduce((acc, current) => acc + current.yLabel, 0);
          return `Total: ${valueFormatter(total)}`;
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
        axis: 'x-axis'
      },
      [BarchartDataLabelPlugin.id]: {
        valueFormatter,
        chartType: 'stacked'
      },
      [ChartClickPlugin.id]: {
        handler: onSelect ? index => onSelect({key: aggregates[index]}) : undefined,
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
          // error={error}
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
    </Container>
  );
}

export default inject('reportThemes')(observer(StorageLayers));
