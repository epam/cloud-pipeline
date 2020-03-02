/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Table
} from 'antd';
import moment from 'moment-timezone';
import {
  BarChart,
  BillingTable,
  Summary
} from './charts';
import Filters from './filters';
import {SummaryHOC} from './charts/summaryHOC';
import {Period, getPeriod} from './periods';
import Export, {ExportComposers} from './export';
import {
  GetBillingData,
  GetGroupedStorages,
  GetGroupedStoragesWithPrevious,
  GetGroupedFileStorages,
  GetGroupedFileStoragesWithPrevious,
  GetGroupedObjectStorages,
  GetGroupedObjectStoragesWithPrevious
} from '../../../models/billing';
import styles from './reports.css';

const SummaryWithControls = SummaryHOC(Summary);
const tablePageSize = 10;

function injection (stores, props) {
  const {location, params} = props;
  const {type} = params || {};
  const {
    user,
    group,
    period = Period.month,
    range
  } = location.query;
  const periodInfo = getPeriod(period, range);
  const filters = {
    group,
    user,
    type,
    ...periodInfo
  };
  let filterBy = GetBillingData.FILTER_BY.storages;
  let storages;
  let storagesTable;
  if (/^file$/i.test(type)) {
    storages = new GetGroupedFileStoragesWithPrevious(filters, true);
    storagesTable = new GetGroupedFileStorages(filters, true);
    filterBy = GetBillingData.FILTER_BY.fileStorages;
  } else if (/^object$/i.test(type)) {
    storages = new GetGroupedObjectStoragesWithPrevious(filters, true);
    storagesTable = new GetGroupedObjectStorages(filters, true);
    filterBy = GetBillingData.FILTER_BY.objectStorages;
  } else {
    storages = new GetGroupedStoragesWithPrevious(filters, true);
    storagesTable = new GetGroupedStorages(filters, true);
  }
  storages.fetch();
  storagesTable.fetch();
  const summary = new GetBillingData({
    ...filters,
    filterBy
  });
  summary.fetch();

  return {
    user,
    group,
    type,
    summary,
    storages,
    storagesTable
  };
}

function StoragesDataBlock ({children}) {
  return (
    <div className={styles.storagesChartsContainer}>
      <div>
        {children}
      </div>
    </div>
  );
}

function renderTable ({storages}) {
  if (!storages || !storages.loaded) {
    return null;
  }
  const columns = [
    {
      key: 'storage',
      title: 'Storage',
      render: ({info, name}) => {
        return info && info.name ? info.pathMask || info.name : name;
      }
    },
    {
      key: 'owner',
      title: 'Owner',
      dataIndex: 'owner'
    },
    {
      key: 'cost',
      title: 'Cost',
      dataIndex: 'value',
      render: (value) => value ? `$${Math.round(value * 100.0) / 100.0}` : null
    },
    {
      key: 'region',
      title: 'Region',
      dataIndex: 'region'
    },
    {
      key: 'provider',
      title: 'Provider',
      dataIndex: 'provider'
    },
    {
      key: 'created',
      title: 'Created date',
      dataIndex: 'created',
      render: (value) => moment.utc(value).format('DD MMM YYYY')
    }
  ];
  const dataSource = Object.values(storages.value || {});
  return (
    <Table
      rowKey={({info, name}) => {
        return info && info.id ? `storage_${info.id}` : `storage_${name}`;
      }}
      loading={storages.pending}
      dataSource={dataSource}
      columns={columns}
      pagination={{
        current: storages.pageNum + 1,
        pageSize: storages.pageSize,
        total: storages.totalPages * storages.pageSize,
        onChange: async (page) => {
          await storages.fetchPage(page - 1);
        }
      }}
      size="small"
    />
  );
}

const RenderTable = observer(renderTable);

function StorageReports ({storages, storagesTable, summary, type}) {
  const getSummaryTitle = () => {
    if (/^file$/i.test(type)) {
      return 'File storages usage';
    }
    if (/^object$/i.test(type)) {
      return 'Object storages usage';
    }
    return 'Storages usage';
  };
  const getTitle = () => {
    if (/^file$/i.test(type)) {
      return 'File storages';
    }
    if (/^object$/i.test(type)) {
      return 'Object storages';
    }
    return 'Storages';
  };
  const composers = [
    {
      composer: ExportComposers.summaryComposer,
      options: [summary]
    },
    {
      composer: ExportComposers.defaultComposer,
      options: [
        storages,
        {
          owner: 'owner',
          cloud_provider: 'provider',
          cloud_region: 'region',
          created_date: 'created'
        }
      ]
    }
  ];
  return (
    <Export.Consumer
      className={styles.chartsContainer}
      composers={composers}
    >
      <StoragesDataBlock>
        <BillingTable summary={summary} showQuota={false} />
        <SummaryWithControls
          summary={summary}
          quota={false}
          title={getSummaryTitle()}
          style={{flex: 1, maxHeight: 500}}
        />
      </StoragesDataBlock>
      <StoragesDataBlock className={styles.chartsColumnContainer}>
        <BarChart
          request={storages}
          title={getTitle()}
          top={tablePageSize}
          style={{height: 300}}
        />
        <RenderTable storages={storagesTable} />
      </StoragesDataBlock>
    </Export.Consumer>
  );
}

export default inject(injection)(
  Filters.attach(
    observer(StorageReports)
  )
);
