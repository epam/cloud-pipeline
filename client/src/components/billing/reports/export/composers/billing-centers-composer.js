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

import {minutesToHours} from '../../../../../models/billing/utils';
import {Range} from '../../periods';

function compose (csv, resources) {
  return new Promise((resolve, reject) => {
    if (!resources || resources.length === 0 || resources.filter(r => !r.loaded).length > 0) {
      reject(new Error('Resources data is not available'));
    } else {
      const centers = [];
      for (let r = 0; r < resources.length; r++) {
        const resource = resources[r];
        const {value = {}} = resource;
        const currentCenters = Object.keys(value);
        for (let c = 0; c < currentCenters.length; c++) {
          if (centers.indexOf(currentCenters[c]) === -1) {
            centers.push(currentCenters[c]);
          }
        }
      }
      const titleRow = csv.addRow('', true);
      const centersRows = {};
      for (let c = 0; c < centers.length; c++) {
        centersRows[centers[c]] = csv.addRow(centers[c]);
      }
      const grandTotalRow = csv.addRow('Grand total', true);
      const columns = {};
      for (let i = 0; i < resources.length; i++) {
        const resource = resources[i];
        const {filters} = resource;
        const {start, endStrict, name} = filters;
        const periodName = Range.getRangeDescription({start, end: endStrict}, name);
        columns[i] = csv.addColumns(periodName, '', '');
        csv.setCellValueByIndex(
          titleRow,
          columns[i][0],
          'Sum of runs'
        );
        csv.setCellValueByIndex(
          titleRow,
          columns[i][1],
          'Sum of hours'
        );
        csv.setCellValueByIndex(
          titleRow,
          columns[i][2],
          'Sum of costs'
        );
      }
      for (let i = 0; i < resources.length; i++) {
        const resource = resources[i];
        const {value = {}} = resource;
        const getGroupingInfo = (parent, name) => {
          if (
            value.hasOwnProperty(parent) &&
            value[parent].hasOwnProperty('groupingInfo') &&
            value[parent].groupingInfo.hasOwnProperty(name)
          ) {
            return value[parent].groupingInfo[name];
          }
          return undefined;
        };
        const getInfo = (parent, name) => {
          if (
            value.hasOwnProperty(parent) &&
            value[parent].hasOwnProperty(name)
          ) {
            return value[parent][name];
          }
          return undefined;
        };
        let sumOfRuns = 0;
        let sumOfHours = 0;
        let sumOfCost = 0;
        for (let c = 0; c < centers.length; c++) {
          const center = centers[c];
          const runsValue = getGroupingInfo(center, 'runs');
          const costsValue = getInfo(center, 'value');
          const runs = (!runsValue || isNaN(runsValue)) ? 0 : +runsValue;
          const hours = minutesToHours(getGroupingInfo(center, 'usage_runs'));
          const costs = (!costsValue || isNaN(costsValue)) ? 0 : +costsValue;
          sumOfRuns += runs;
          sumOfHours += hours;
          sumOfCost += costs;
          csv.setCellValueByIndex(
            centersRows[center],
            columns[i][0],
            runs
          );
          csv.setCellValueByIndex(
            centersRows[center],
            columns[i][1],
            hours
          );
          csv.setCellValueByIndex(
            centersRows[center],
            columns[i][2],
            costs
          );
        }
        csv.setCellValueByIndex(
          grandTotalRow,
          columns[i][0],
          sumOfRuns
        );
        csv.setCellValueByIndex(
          grandTotalRow,
          columns[i][1],
          sumOfHours
        );
        csv.setCellValueByIndex(
          grandTotalRow,
          columns[i][2],
          sumOfCost
        );
      }
      resolve();
    }
  });
}

export default compose;
