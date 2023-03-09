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
import {Checkbox, Icon, Popover, Row} from 'antd';
import classNames from 'classnames';
import AWSRegionTag from '../../../special/AWSRegionTag';
import RunName from '../../run-name';
import {parseRunServiceUrlConfiguration} from '../../../../utils/multizone';
import StatusIcon from '../../../special/run-status-icon';
import MultizoneUrl from '../../../special/multizone-url';
import {
  getFiltersState,
  onFilterDropdownVisibilityChangedGenerator,
  onFilterGenerator
} from './state-utilities';
import Statuses, {getStatusName} from '../statuses';
import RunLoadingPlaceholder from './run-loading-placeholder';
import styles from './run-table-columns.css';

function getColumnFilter (state, setState) {
  const parameter = 'statuses';
  const onFilterDropdownVisibleChange = onFilterDropdownVisibilityChangedGenerator(
    parameter,
    state,
    setState
  );
  const {
    value,
    visible: filterDropdownVisible,
    onChange,
    filtered
  } = getFiltersState(parameter, state, setState);
  const onFilter = onFilterGenerator(parameter, state, setState);
  const allStatuses = [
    Statuses.running,
    Statuses.pausing,
    Statuses.paused,
    Statuses.resuming,
    Statuses.success,
    Statuses.failure,
    Statuses.stopped
  ];
  const clear = () => onFilter([]);
  const enabledStatuses = new Set(value || []);
  const onChangeStatusSelected = (status) => (e) => {
    if (e.target.checked && !enabledStatuses.has(status)) {
      onChange([...enabledStatuses, status]);
    } else if (!e.target.checked && enabledStatuses.has(status)) {
      onChange([...enabledStatuses].filter((s) => s !== status));
    }
  };
  const onOK = () => onFilterDropdownVisibleChange(false);
  const filterDropdown = (
    <div
      className={
        classNames(
          styles.filterPopoverContainer,
          'cp-filter-popover-container'
        )
      }
      style={{width: 120}}
    >
      <Row>
        <div style={{maxHeight: 400, overflowY: 'auto'}}>
          {
            allStatuses
              .map(status => {
                return (
                  <Row
                    style={{margin: 5}}
                    key={status}
                  >
                    <Checkbox
                      onChange={onChangeStatusSelected(status)}
                      checked={enabledStatuses.has(status)}
                    >
                      {getStatusName(status)}
                    </Checkbox>
                  </Row>
                );
              })
          }
        </div>
      </Row>
      <Row
        type="flex"
        justify="space-between"
        className={styles.filterActionsButtonsContainer}
      >
        <a onClick={onOK}>OK</a>
        <a onClick={clear}>Clear</a>
      </Row>
    </div>
  );
  return {
    filterDropdown,
    filterDropdownVisible,
    filtered,
    onFilterDropdownVisibleChange
  };
}

function renderRun (text, run) {
  let clusterIcon;
  if (run.nodeCount > 0) {
    clusterIcon = (
      <Icon
        type="database"
      />
    );
  }
  const style = {
    display: 'inline-table',
    marginLeft: run.isChildRun ? '10px' : 0
  };
  let instanceOrSensitiveFlag;
  if (run.instance || run.sensitive) {
    instanceOrSensitiveFlag = (
      <span>
        {
          run.instance && (
            <AWSRegionTag
              plainMode
              provider={run.instance.cloudProvider}
              regionId={run.instance.cloudRegionId}
            />
          )
        }
        {
          run.sensitive
            ? (
              <span
                className="cp-sensitive"
                style={run.instance ? {marginLeft: 5} : {}}
              >
                sensitive
              </span>
            )
            : null
        }
      </span>
    );
  }
  const name = (
    <RunName
      style={{fontWeight: 'bold'}}
      run={run}
      ignoreOffset
    >
      {text}
    </RunName>
  );
  if (run.serviceUrl && run.initialized) {
    const regionedUrls = parseRunServiceUrlConfiguration(run.serviceUrl);
    return (
      <div style={style}>
        <StatusIcon run={run} small additionalStyle={{marginRight: 5}} />
        <Popover
          mouseEnterDelay={1}
          content={
            <div>
              <ul>
                {
                  regionedUrls.map(({name, url, sameTab}, index) =>
                    <li key={index} style={{margin: 4}}>
                      <MultizoneUrl
                        target={sameTab ? '_top' : '_blank'}
                        configuration={url}
                      >
                        {name}
                      </MultizoneUrl>
                    </li>
                  )
                }
              </ul>
            </div>
          }
          trigger={['hover']}
        >
          {clusterIcon} <Icon type="export" />
          {name}
          {instanceOrSensitiveFlag && <br />}
          {
            instanceOrSensitiveFlag &&
            <span style={{marginLeft: 18}}>
              {instanceOrSensitiveFlag}
            </span>
          }
        </Popover>
      </div>
    );
  }
  return (
    <div style={style}>
      <StatusIcon
        run={run}
        small
        additionalStyle={{marginRight: 5}}
      />
      {clusterIcon}
      {name}
      {instanceOrSensitiveFlag && <br />}
      {
        instanceOrSensitiveFlag &&
        <span style={{marginLeft: 18}}>
          {instanceOrSensitiveFlag}
        </span>
      }
    </div>
  );
}

const getColumn = () => ({
  title: (
    <span>
      Run
    </span>
  ),
  dataIndex: 'podId',
  key: 'statuses',
  className: styles.runRowName,
  render: (text, run) => (
    <RunLoadingPlaceholder run={run}>
      {renderRun(text, run)}
    </RunLoadingPlaceholder>
  )
});

export {
  getColumn,
  getColumnFilter
};
