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
import {Provider as MobxProvider} from 'mobx-react';
import Menu, {MenuItem, Divider, SubMenu} from 'rc-menu';
import Consumer from './export-consumer';
import ImageConsumer from './export-image-consumer';
import exportStore from './export-store';
import ExportFormat from './export-formats';

const ExportFormatName = {
  [ExportFormat.csv]: 'As CSV',
  [ExportFormat.image]: 'As Image',
  [ExportFormat.csvCostCenters]: 'As CSV (Billing centers)',
  [ExportFormat.csvUsers]: 'As CSV (Users)',
  [ExportFormat.rawCsv]: 'Export raw data'
};

const Provider = ({children}) => (
  <MobxProvider export={exportStore}>
    {children}
  </MobxProvider>
);

function renderExportMenu (filterStore, options = {}) {
  if (!filterStore) {
    return null;
  }
  const {
    subMenu = true,
    exportKeyPrefix = '',
    onSelect
  } = options;
  let formats = [ExportFormat.csv, ExportFormat.image];
  if (/^general$/i.test(filterStore.report)) {
    formats = [ExportFormat.csvCostCenters, ExportFormat.csvUsers, ExportFormat.image];
  } else if (/^instances$/i.test(filterStore.report)) {
    formats = [
      ExportFormat.csv,
      ExportFormat.image,
      ExportFormat.divider,
      ExportFormat.rawCsv
    ];
  }
  if (!formats || formats.length === 0) {
    return null;
  }
  const getItemKey = key => [exportKeyPrefix, key].filter(o => o && o.length).join('-');
  const items = formats.map((format, index) => (
    format === ExportFormat.divider
      ? (<Divider key={`${format}-${index}`} />)
      : (<MenuItem key={getItemKey(format)}>{ExportFormatName[format]}</MenuItem>)
  ));
  if (subMenu) {
    return (
      <SubMenu
        key="export menu"
        title="Export"
        style={{cursor: 'pointer'}}
        selectedKeys={[]}
      >
        {items}
      </SubMenu>
    );
  }
  return (
    <Menu
      onClick={onSelect}
      style={{cursor: 'pointer'}}
      selectedKeys={[]}
    >
      {items}
    </Menu>
  );
}

function onExport (format, stores) {
  const {
    filters = {},
    users,
    cloudRegionsInfo,
    discounts
  } = stores;
  const {getDescription} = filters;
  const documentName = typeof getDescription === 'function'
    ? getDescription({
      users,
      cloudRegionsInfo,
      discounts
    })
    : undefined;
  const title = typeof documentName === 'function' ? documentName() : documentName;
  switch (format) {
    case ExportFormat.image:
      exportStore.doImageExport(title, {format});
      break;
    default:
    case ExportFormat.csv:
    case ExportFormat.csvCostCenters:
    case ExportFormat.csvUsers:
    case ExportFormat.rawCsv:
      exportStore.doCsvExport(title, {format});
      break;
  }
}

const exportStores = ['users', 'cloudRegionsInfo', 'discounts', 'filters'];

const Exports = {
  Provider,
  Consumer,
  ImageConsumer
};

export {
  exportStores,
  ExportFormat,
  Provider,
  Consumer,
  ImageConsumer,
  renderExportMenu,
  onExport
};
export default Exports;
