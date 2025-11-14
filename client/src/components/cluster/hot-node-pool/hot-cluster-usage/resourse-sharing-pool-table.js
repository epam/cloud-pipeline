import React from 'react';
import classNames from 'classnames';
import FileSaver from 'file-saver';
import moment from 'moment-timezone';
import styles from './hot-cluster-usage.css';
import {Period} from '../../../special/periods';
import displayDate from '../../../../utils/displayDate';
import {bytesToGiB} from './charts/utils/hardware-chart-utils';

function extractStatistics (data = []) {
  const stats = data.reduce((acc, record) => {
    const CPUUtilization = record.totalCPUCount > 0
      ? (record.activeCPUCount || 0) / record.totalCPUCount
      : 0;
    const GPUUtilization = record.totalGPUCount > 0
      ? (record.activeGPUCount || 0) / record.totalGPUCount
      : 0;
    const RAMUtilization = record.totalMemoryCount > 0
      ? (record.activeMemoryCount || 0) / record.totalMemoryCount
      : 0;
    acc.CPUCounts.push(record.activeCPUCount || 0);
    acc.GPUCounts.push(record.activeGPUCount || 0);
    acc.RAMCounts.push(record.activeMemoryCount || 0);
    acc.CPUUtilizations.push(CPUUtilization);
    acc.GPUUtilizations.push(GPUUtilization);
    acc.RAMUtilizations.push(RAMUtilization);
    return acc;
  }, {
    CPUCounts: [],
    GPUCounts: [],
    RAMCounts: [],
    CPUUtilizations: [],
    GPUUtilizations: [],
    RAMUtilizations: []
  });
  const getAverage = (data = []) => {
    if (data.length === 0) return 0;
    return data.reduce((acc, current) => acc + current, 0) / data.length;
  };
  const toPercent = (number) => Number((number * 100).toFixed(2));
  return {
    maxCPU: Math.max(...stats.CPUCounts),
    maxGPU: Math.max(...stats.GPUCounts),
    maxRAM: Number(bytesToGiB(Math.max(...stats.RAMCounts)).toFixed(2)),
    maxCPUUtilizationPercent: toPercent(Math.max(...stats.CPUUtilizations)),
    maxGPUUtilizationPercent: toPercent(Math.max(...stats.GPUUtilizations)),
    maxRAMUtilizationPercent: toPercent(Math.max(...stats.RAMUtilizations)),
    averageCPU: Number(getAverage(stats.CPUCounts).toFixed(2)),
    averageGPU: Number(getAverage(stats.GPUCounts).toFixed(2)),
    averageRAM: Number(bytesToGiB(getAverage(stats.RAMCounts)).toFixed(2)),
    averageCPUUtilizationPercent: toPercent(getAverage(stats.CPUUtilizations)),
    averageGPUUtilizationPercent: toPercent(getAverage(stats.GPUUtilizations)),
    averageRAMUtilizationPercent: toPercent(getAverage(stats.RAMUtilizations))
  };
}

export default class ResourseSharingPoolTable extends React.Component {
  onExport = () => {
    const {data, periodType} = this.props;
    const {originalRecords} = data;
    if (!originalRecords || originalRecords.length === 0) {
      return;
    }
    const header = [
      'Date',
      'GPU usage',
      'GPU capacity',
      'CPU usage',
      'CPU capacity',
      'Ram usage (GiB)',
      'Ram capacity (GiB)'
    ].join(',');
    const rows = originalRecords
      .filter(record => record.periodStart)
      .map(record => {
        const date = periodType === Period.month
          ? displayDate(record.periodStart, 'YYYY-MM-DD')
          : record.measureTime;
        const gpuUsage = record.activeGPUCount || 0;
        const gpuCapacity = record.totalGPUCount || 0;
        const cpuUsage = record.activeCPUCount || 0;
        const cpuCapacity = record.totalCPUCount || 0;
        const ramUsage = bytesToGiB(record.activeMemoryCount || 0).toFixed(2);
        const ramCapacity = bytesToGiB(record.totalMemoryCount || 0).toFixed(2);
        return [date, gpuUsage, gpuCapacity, cpuUsage, cpuCapacity, ramUsage, ramCapacity]
          .join(',');
      });
    const csvContent = [header, ...rows].join('\n');
    const blob = new Blob([csvContent], {type: 'text/csv;charset=utf-8'});
    const fileName = `${data.poolName}-utilization-${moment().format('YYYY-MM-DD-HHmmss')}.csv`;
    FileSaver.saveAs(blob, fileName);
  };

  render () {
    const {data, className} = this.props;
    const {originalRecords} = data;
    if (!originalRecords) {
      return null;
    }
    const stats = extractStatistics(data.originalRecords);
    const {
      maxCPU,
      maxGPU,
      maxRAM,
      maxCPUUtilizationPercent,
      maxGPUUtilizationPercent,
      maxRAMUtilizationPercent,
      averageCPU,
      averageGPU,
      averageRAM,
      averageCPUUtilizationPercent,
      averageGPUUtilizationPercent,
      averageRAMUtilizationPercent
    } = stats;
    return (
      <div className={classNames(className, styles.statisticsTableContainer)}>
        <table className={styles.statisticsTable}>
          <thead>
            <tr className="cp-table-cell">
              <th />
              <th>GPU</th>
              <th>CPU</th>
              <th>RAM (GiB)</th>
            </tr>
          </thead>
          <tbody>
            <tr className="cp-table-cell">
              <td>Max utilization</td>
              <td>{maxGPU}</td>
              <td>{maxCPU}</td>
              <td>{maxRAM}</td>
            </tr>
            <tr className="cp-table-cell">
              <td>Max utilization, %</td>
              <td>{maxGPUUtilizationPercent}%</td>
              <td>{maxCPUUtilizationPercent}%</td>
              <td>{maxRAMUtilizationPercent}%</td>
            </tr>
            <tr className="cp-table-cell">
              <td>Average utilization</td>
              <td>{averageGPU}</td>
              <td>{averageCPU}</td>
              <td>{averageRAM}</td>
            </tr>
            <tr className="cp-table-cell">
              <td>Average utilization, %</td>
              <td>{averageGPUUtilizationPercent}%</td>
              <td>{averageCPUUtilizationPercent}%</td>
              <td>{averageRAMUtilizationPercent}%</td>
            </tr>
          </tbody>
        </table>
        <a className={styles.exportButton} onClick={this.onExport}>
          Export
        </a>
      </div>
    );
  }
}
