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
import PropTypes from 'prop-types';
import {Icon} from 'antd';
import UsersRolesSelect from '../../../users-roles-select';
import Collapse from '../collapse';
import DelayedInput from '../../../DelayedInput';
import UserName from '../../../UserName';
import styles from '../cell-profiler.css';

class CellProfilerJobsFilters extends React.Component {
  onChangeFiltersField = (field) => (value) => {
    const {
      filters = {},
      onChange
    } = this.props;
    if (typeof onChange === 'function' && field) {
      onChange({
        ...filters,
        [field]: value
      });
    }
  };
  render () {
    const {
      className,
      style,
      filters = {},
      onChange
    } = this.props;
    const {
      userNames = [],
      source = ''
    } = filters;
    const onChangeUserNames = (newUserNames = []) => {
      const names = newUserNames.map(o => o.name);
      if (typeof onChange === 'function') {
        onChange({
          ...filters,
          userNames: names
        });
      }
    };
    const renderHeader = (expanded) => {
      if (expanded) {
        return 'Filters';
      }
      const parts = [];
      if (userNames.length > 1) {
        parts.push((
          <span key="owners" style={{marginRight: 5}}>
            {userNames.length} owner{userNames.length === 1 ? '' : 's'}
          </span>
        ));
      } else if (userNames.length === 1) {
        parts.push((
          <UserName
            key="owner"
            userName={userNames[0]}
            showIcon
            style={{marginRight: 5}}
          />
        ));
      }
      if (source && source.length) {
        parts.push((
          <span
            key="source"
            style={{marginRight: 5}}
          >
            <Icon type="file" />
            {source}
          </span>
        ));
      }
      if (parts.length === 0) {
        return 'Filters';
      }
      return (
        <span
          className="cp-ellipsis-text"
        >
          Filters:
          {' '}
          {parts}
        </span>
      );
    };
    return (
      <Collapse
        className={className}
        style={style}
        header={renderHeader}
      >
        <div className={styles.cellProfilerJobsFilterRow}>
          <b>Owners:</b>
          <UsersRolesSelect
            adGroups={false}
            showRoles={false}
            className={styles.cellProfilerJobsFilter}
            value={userNames.map(o => ({principal: true, name: o}))}
            onChange={onChangeUserNames}
          />
        </div>
        <div className={styles.cellProfilerJobsFilterRow}>
          <b>File name:</b>
          <DelayedInput
            className={styles.cellProfilerJobsFilter}
            onChange={this.onChangeFiltersField('source')}
            value={source}
            delay={500}
          />
        </div>
      </Collapse>
    );
  }
}

CellProfilerJobsFilters.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  filters: PropTypes.shape({
    userNames: PropTypes.arrayOf(PropTypes.string),
    measurementUUID: PropTypes.string,
    pipeline: PropTypes.string
  }),
  onChange: PropTypes.func
};

export default CellProfilerJobsFilters;
