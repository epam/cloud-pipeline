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
import {Popover} from 'antd';
import DataStorageLink from '../../../special/data-storage-link';
import RunLoadingPlaceholder from './run-loading-placeholder';
import styles from './run-table-columns.css';

const getColumnFilter = () => {};

const renderRunParameter = (runParameter) => {
  if (!runParameter || !runParameter.name) {
    return null;
  }
  const valueSelector = () => {
    return runParameter.resolvedValue || runParameter.value || '';
  };
  if (/^(input|output|common|path)$/i.test(runParameter.type)) {
    const valueParts = valueSelector().split(/[,|]/);
    return (
      <tr key={runParameter.name}>
        <td style={{verticalAlign: 'top', paddingLeft: 5}}>
          <span>{runParameter.name}: </span>
        </td>
        <td>
          <ul>
            {
              valueParts.map((value, index) => (
                <li
                  key={`${value}-${index}`}
                >
                  <DataStorageLink
                    key={`link-${value}-${index}`}
                    path={value}
                    isFolder={/^output$/i.test(runParameter.type) ? true : undefined}
                  >
                    {value}
                  </DataStorageLink>
                </li>
              ))
            }
          </ul>
        </td>
      </tr>
    );
  }
  const values = (valueSelector() || '').split(',').map(v => v.trim());
  if (values.length === 1) {
    return (
      <tr key={runParameter.name}>
        <td style={{verticalAlign: 'top', paddingLeft: 5}}>{runParameter.name}:</td>
        <td>{values[0]}</td>
      </tr>
    );
  } else {
    return (
      <tr key={runParameter.name}>
        <td style={{verticalAlign: 'top', paddingLeft: 5}}>
          <span>{runParameter.name}:</span>
        </td>
        <td>
          <ul>
            {values.map((value, index) => <li key={index}>{value}</li>)}
          </ul>
        </td>
      </tr>
    );
  }
};

const renderLinks = (run) => {
  if (run.pipelineRunParameters) {
    const inputParameters = run.pipelineRunParameters
      .filter(p => ['input', 'common'].indexOf((p.type || '').toLowerCase()) >= 0);
    const outputParameters = run.pipelineRunParameters
      .filter(p => (p.type || '').toLowerCase() === 'output');
    if (inputParameters.length > 0 || outputParameters.length > 0) {
      const content = (
        <table>
          <tbody>
            {
              inputParameters.length > 0
                ? <tr><td colSpan={2}><b>Input:</b></td></tr>
                : undefined
            }
            {inputParameters.map(renderRunParameter)}
            {
              outputParameters.length > 0
                ? <tr><td colSpan={2}><b>Output:</b></td></tr>
                : undefined
            }
            {outputParameters.map(renderRunParameter)}
          </tbody>
        </table>
      );
      return (
        <Popover content={content}>
          <a onClick={() => {}}>LINKS</a>
        </Popover>
      );
    }
  }
  return null;
};

const getColumn = () => ({
  key: 'links',
  className: styles.runRowLinks,
  render: (run) => (
    <RunLoadingPlaceholder run={run} empty>
      {renderLinks(run)}
    </RunLoadingPlaceholder>
  )
});

export {
  getColumn,
  getColumnFilter
};
