import React, {Component} from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {Icon, Spin} from 'antd';
import Chart from 'chart.js';
import {BarchartDataLabelPlugin, ChartClickPlugin} from '../../billing/reports/charts/extensions';
import ThemedReport from '../../billing/reports/themed-report';
import 'chart.js/dist/Chart.css';

@inject('reportThemes')
@observer
class RunsInfoChart extends Component {
  static propTypes = {
    loading: PropTypes.bool,
    title: PropTypes.string,
    data: PropTypes.object,
    style: PropTypes.object,
    onEntryClick: PropTypes.func
  };

  chart;
  ctx;

  componentDidUpdate (prevProps) {
    if (this.ctx) {
      const {
        data: prevData,
        options: prevOptions,
        title: prevTitle,
        displayEmptyTitleRow: prevDisplayEmptyTitleRow,
        onEntryClick: prevOnEntryClick
      } = prevProps;
      const {
        data,
        options,
        title,
        displayEmptyTitleRow,
        onEntryClick
      } = this.props;
      if (
        data !== prevData ||
        options !== prevOptions ||
        title !== prevTitle ||
        displayEmptyTitleRow !== prevDisplayEmptyTitleRow ||
        onEntryClick !== prevOnEntryClick
      ) {
        this.chartRef(this.ctx, this.props);
      }
    }
  }

  get noDataProvided () {
    const {data} = this.props;
    return (data.labels || []).length === 0;
  }

  chartRef = (ctx, props) => {
    if (ctx) {
      this.ctx = ctx;
      const {
        data,
        options = {},
        title,
        displayEmptyTitleRow,
        onEntryClick
      } = props || this.props;
      const opts = {
        animation: {duration: 0},
        legend: {
          display: false
        },
        title: {
          display: !!title || displayEmptyTitleRow,
          text: displayEmptyTitleRow ? '' : title
        },
        scales: {
          xAxes: [{
            id: 'x-axis',
            stacked: true,
            gridLines: {
              display: false
            }
          }],
          yAxes: [
            {
              stacked: true,
              ticks: {
                beginAtZero: true,
                stepSize: 1,
                maxTicksLimit: 5
              }
            }
          ]
        },
        maintainAspectRatio: false,
        plugins: {
          [BarchartDataLabelPlugin.id]: {
            valueFormatter: (value) => value
          },
          [ChartClickPlugin.id]: {
            handler: index => {
              const {entries = []} = data || {};
              if (typeof onEntryClick === 'function') {
                onEntryClick(entries[index]);
              }
            },
            axis: 'x-axis'
          }
        },
        hover: {
          onHover: function (e) {
            const point = this.getElementsAtXAxis(e);
            e.target.style.cursor = point.length > 0
              ? 'pointer'
              : 'default';
          }
        },
        tooltips: {
          intersect: false,
          mode: 'index'
        },
        ...options
      };
      if (this.chart) {
        this.chart.data = data;
        this.chart.options = opts;
        this.chart.update();
      } else {
        this.chart = new Chart(ctx, {
          type: 'bar',
          data,
          options: opts,
          plugins: [BarchartDataLabelPlugin.plugin, ChartClickPlugin.plugin]
        });
      }
      this.chart.resize();
    }
  };

  render () {
    const {style, loading} = this.props;
    return (
      <div
        style={Object.assign(
          {position: 'relative'},
          style
        )}
      >
        <canvas
          ref={this.chartRef}
          style={{
            position: 'relative',
            width: '100%',
            height: '100%',
            maxHeight: 'calc(50vh - 70px)'
          }}
        />
        {(!loading && this.noDataProvided) ? (
          <div
            style={{
              position: 'absolute',
              top: '50%',
              left: '50%',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'center',
              transform: 'translate(-50%, -50%)'
            }}
          >
            <Icon type="inbox" style={{fontSize: 'large'}} />
            <span>No data</span>
          </div>
        ) : null}
        {loading ? (
          <Spin style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            transform: 'translate(-50%, -50%)'
          }} spinning={loading} />
        ) : null}
      </div>
    );
  }
}

const RunsInfoChartWithThemes = (props) => {
  return (
    <ThemedReport>
      <RunsInfoChart {...props} />
    </ThemedReport>
  );
};

export default RunsInfoChartWithThemes;
