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
import {observer} from 'mobx-react';
import classNames from 'classnames';
import moment from 'moment-timezone';
import {Pagination} from 'antd';
import {
  getStorageClassByAggregate,
  getStorageClassNameByAggregate,
  StorageAggregate
} from '../navigation/aggregate';
import {costTickFormatter, DisplayUser, numberFormatter} from './utilities';
import {discounts} from './discounts';
import styles from './storage-report.css';

function StorageTable (
  {
    storages,
    discounts: discountsFn,
    height,
    aggregate,
    showDetails
  }
) {
  if (!storages || !storages.loaded) {
    return null;
  }
  const storageClass = aggregate && aggregate !== StorageAggregate.default
    ? getStorageClassByAggregate(aggregate)
    : 'TOTAL';
  const storageClassName = aggregate && aggregate !== StorageAggregate.default
    ? getStorageClassNameByAggregate(aggregate)
    : undefined;
  const getLayerValue = (item, key) => {
    const {
      costDetails = {},
      value,
      usage,
      usageLast
    } = item || {};
    if (!showDetails) {
      switch (key) {
        case 'size': return (usageLast || 0);
        case 'avgSize': return (usage || 0);
        case 'cost': return (value || 0);
        default:
          return 0;
      }
    }
    const {
      tiers = {}
    } = costDetails;
    const tier = tiers[storageClass] || {};
    return tier[key] || 0;
  };
  const getLayerValues = (item, ...keys) =>
    keys.map((key) => getLayerValue(item, key)).reduce((r, c) => r + c, 0);
  const getLayerCostValue = (item, ...keys) => {
    const value = getLayerValues(item, ...keys);
    return value || showDetails ? costTickFormatter(value || 0) : null;
  };
  const getLayerSizeValue = (item, ...keys) => {
    const value = getLayerValues(item, ...keys);
    return value || showDetails ? numberFormatter(value || 0) : null;
  };
  const getDetailedCellsTitle = (title, measure) => {
    const details = [
      measure,
      storageClassName
    ].filter(Boolean);
    if (details.length > 0) {
      return `${title} (${details.join(', ')})`;
    }
    return title;
  };
  const getDetailedCells = ({
    title,
    measure,
    key = (title || '').toLowerCase(),
    currentKey,
    oldVersionsKey,
    dataExtractor = ((item, ...keys) => 0)
  }) => ([
    {
      key,
      title: getDetailedCellsTitle(title, measure),
      headerSpan: showDetails ? 2 : 1,
      render: (item) => {
        const total = dataExtractor(item, currentKey, oldVersionsKey);
        return (
          <span>
            {total}
          </span>
        );
      },
      className: showDetails
        ? classNames(styles.cell, styles.rightAlignedCell, styles.noPadding)
        : styles.cell,
      headerClassName: showDetails
        ? classNames(styles.cell, styles.centeredCell)
        : styles.cell
    },
    showDetails ? ({
      key: `${key}-old-versions`,
      title: '\u00A0',
      header: false,
      render: (item) => {
        const oldVersions = dataExtractor(item, oldVersionsKey);
        return (
          <span className="cp-text-not-important">
            <span>{'/ '}</span>
            {oldVersions}
          </span>
        );
      },
      className: classNames(styles.cell, styles.leftAlignedCell, styles.noPadding)
    }) : undefined
  ].filter(Boolean));
  const columns = [
    {
      key: 'storage',
      title: 'Storage',
      className: styles.storageCell,
      render: ({info, name}) => {
        return info && info.name ? info.pathMask || info.name : name;
      },
      fixed: true
    },
    {
      key: 'owner',
      title: 'Owner',
      dataIndex: 'owner',
      render: owner => (<DisplayUser userName={owner} />),
      className: styles.cell
    },
    {
      key: 'billingCenter',
      title: 'Billing Center',
      dataIndex: 'billingCenter',
      className: styles.cell
    },
    {
      key: 'storageType',
      title: 'Type',
      dataIndex: 'storageType',
      className: styles.cell
    },
    ...getDetailedCells({
      title: 'Cost',
      dataExtractor: getLayerCostValue,
      currentKey: 'cost',
      oldVersionsKey: 'oldVersionCost'
    }),
    ...getDetailedCells({
      title: 'Avg. Vol.',
      measure: 'GB',
      dataExtractor: getLayerSizeValue,
      currentKey: 'avgSize',
      oldVersionsKey: 'oldVersionAvgSize'
    }),
    ...getDetailedCells({
      title: 'Cur. Vol.',
      measure: 'GB',
      dataExtractor: getLayerSizeValue,
      currentKey: 'size',
      oldVersionsKey: 'oldVersionSize'
    }),
    {
      key: 'region',
      title: 'Region',
      dataIndex: 'region',
      className: styles.cell
    },
    {
      key: 'provider',
      title: 'Provider',
      dataIndex: 'provider',
      className: styles.cell
    },
    {
      key: 'created',
      title: 'Created date',
      dataIndex: 'created',
      render: (value) => value ? moment.utc(value).format('DD MMM YYYY') : value,
      className: styles.cell
    }
  ];
  const dataSource = Object.values(
    discounts.applyGroupedDataDiscounts(storages.value || {}, discountsFn)
  );
  const paginationEnabled = storages && storages.loaded
    ? storages.totalPages > 1
    : false;
  const getRowClassName = (storage = {}) => {
    if (`${(storage.groupingInfo || {}).is_deleted}` === 'true') {
      return 'cp-warning-row';
    }
    return '';
  };
  return (
    <div
      className={styles.storageReport}
    >
      <div
        className={
          classNames(
            styles.storageReportTableContainer,
            'cp-bordered'
          )
        }
        style={{
          maxHeight: height - (paginationEnabled ? 40 : 10)
        }}
      >
        <table
          className={classNames('cp-report-table', styles.storageReportTable)}
        >
          <thead>
            <tr>
              {
                columns
                  .filter((column) => column.header === undefined || column.header)
                  .map((column, index) => (
                    <th
                      key={column.key}
                      className={
                        classNames(
                          column.headerClassName || column.className,
                          styles.cell,
                          styles.fixedRow,
                          {
                            [styles.fixedColumn]: index === 0,
                            'fixed-column': index === 0
                          }
                        )
                      }
                      colSpan={column.headerSpan || 1}
                    >
                      {column.title}
                    </th>
                  ))
              }
            </tr>
          </thead>
          <tbody>
            {
              dataSource.map((item, index) => (
                <tr
                  key={`item-${index}`}
                  className={getRowClassName(item)}
                >
                  {
                    columns.map((column) => (
                      <td
                        key={column.key}
                        className={
                          classNames(
                            column.className,
                            styles.cell,
                            {
                              [styles.fixedColumn]: !!column.fixed,
                              'fixed-column': !!column.fixed
                            }
                          )
                        }
                      >
                        {
                          column.render
                            ? column.render(column.dataIndex ? item[column.dataIndex] : item, item)
                            : item[column.dataIndex]
                        }
                      </td>
                    ))
                  }
                </tr>
              ))
            }
          </tbody>
        </table>
      </div>
      {
        paginationEnabled && (
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              alignItems: 'center',
              height: 30
            }}
          >
            <Pagination
              disabled={storages.pending}
              current={storages.pageNum + 1}
              pageSize={storages.pageSize}
              total={storages.totalPages * storages.pageSize}
              onChange={(page) => storages.fetchPage(page - 1)}
              size="small"
            />
          </div>
        )
      }
    </div>
  );
}

export default observer(StorageTable);
