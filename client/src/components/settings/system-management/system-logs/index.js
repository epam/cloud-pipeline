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
import {Alert} from 'antd';
import styles from './system-logs.css';
import Filters, {DATE_FORMAT} from './filters';
import Logs from './logs';
import moment from 'moment-timezone';

class SystemLogs extends React.Component {
  state = {
    filters: undefined
  };

  componentDidMount () {
    this.setDefaultFilter();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (!this.state.filters) {
      this.setDefaultFilter();
    }
  }

  setDefaultFilter = () => {
    this.onFiltersChange({
      messageTimestampFrom: moment.utc().add(-1, 'd').format(DATE_FORMAT)
    });
  };

  onFiltersChange = (newFilters) => {
    this.setState({
      filters: {...newFilters}
    });
  };

  render () {
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return null;
    }
    if (!this.props.authenticatedUserInfo.value.admin) {
      return (
        <Alert type="error" message="Access is denied" />
      );
    }
    const {filters} = this.state;
    return (
      <div className={styles.container}>
        <Filters
          filters={filters}
          onChange={this.onFiltersChange}
        />
        <Logs
          className={styles.logsContainer}
          filters={filters}
        />
      </div>
    );
  }
}

export default inject('authenticatedUserInfo')(observer(SystemLogs));
