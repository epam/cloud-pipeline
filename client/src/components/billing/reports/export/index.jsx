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
import Consumer from './export-consumer';
import ImageConsumer from './export-image-consumer';
import exportStore from './export-store';
import ExportFormat from './export-formats';

const ExportFormatName = {
  [ExportFormat.csv]: 'As CSV',
  [ExportFormat.image]: 'As Image',
  [ExportFormat.csvCostCenters]: 'As CSV (Billing centers)',
  [ExportFormat.csvUsers]: 'As CSV (Users)',
  [ExportFormat.rawCsv]: 'Export raw data',
};

const Provider = ({children}) => <MobxProvider export={exportStore}>{children}</MobxProvider>;

/**
 * Returns antd menu items for the Export submenu.
 * @param {object} filterStore - filters store
 * @param {{ exportKeyPrefix?: string }} options
 * @returns {Array<{ key: string, label: string }|{ type: 'divider', key: string }>}
 */
function getExportMenuItems(filterStore, options = {}) {
  if (!filterStore) {
    return [];
  }
  const {exportKeyPrefix = ''} = options;
  let formats = [ExportFormat.csv, ExportFormat.image];
  if (/^general$/i.test(filterStore.report)) {
    formats = [ExportFormat.csvCostCenters, ExportFormat.csvUsers, ExportFormat.image];
  } else if (/^instances$/i.test(filterStore.report)) {
    formats = [ExportFormat.csv, ExportFormat.image, ExportFormat.divider, ExportFormat.rawCsv];
  }
  if (!formats || formats.length === 0) {
    return [];
  }
  const getItemKey = (key) => [exportKeyPrefix, key].filter((o) => o && o.length).join('-');
  return formats.map((format, index) =>
    format === ExportFormat.divider
      ? {type: 'divider', key: `export-divider-${index}`}
      : {key: getItemKey(format), label: ExportFormatName[format]},
  );
}

function onExport(format, stores) {
  const {filters = {}, users, cloudRegionsInfo, discounts} = stores;
  const {getDescription} = filters;
  const documentName =
    typeof getDescription === 'function'
      ? getDescription({
          users,
          cloudRegionsInfo,
          discounts,
        })
      : undefined;
  const title = typeof documentName === 'function' ? documentName() : documentName;
  switch (format) {
    case ExportFormat.image:
      exportStore.doImageExport(title, {format});
      break;
    case ExportFormat.csv:
    case ExportFormat.csvCostCenters:
    case ExportFormat.csvUsers:
    case ExportFormat.rawCsv:
    default:
      exportStore.doCsvExport(title, {format});
      break;
  }
}

const exportStores = ['users', 'cloudRegionsInfo', 'discounts', 'filters'];

const Exports = {
  Provider,
  Consumer,
  ImageConsumer,
};

export {
  exportStores,
  ExportFormat,
  Provider,
  Consumer,
  ImageConsumer,
  getExportMenuItems,
  onExport,
};
export default Exports;
