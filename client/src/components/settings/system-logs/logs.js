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
import PropTypes from 'prop-types';
import {observable} from 'mobx';
import {message} from 'antd';
import AU from 'ansi_up';
import SystemLogsFilter from '../../../models/system-logs/filter';
import styles from './logs.css';

class Logs extends React.Component {
  state = {
    logs: [],
    currentRow: 0
  };

  ansiUp = new AU();
  @observable logs = new SystemLogsFilter();

  get logs () {
    if (this.logs.loaded) {
      return (this.logs.value.results || []);
    }
    return [];
  }

  componentDidMount () {
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this);
    this.onFiltersChanged();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.filters !== this.props.filters) {
      this.onFiltersChanged();
    }
  }

  onFiltersChanged = () => {
    const {filters} = this.props;
    this.logs.send(filters)
      .then(() => {
        if (this.logs.error) {
          message.error(this.logs.error, 5);
        }
      })
      .catch(error => {
        message.error(error.toString(), 5);
      });
  };

  render () {
    const {height} = this.props;
    if (!height) {
      return null;
    }
    return (
      <div style={{height, width: '100%'}}>
        Logs
      </div>
    );
  }
}

Logs.propTypes = {
  filters: PropTypes.object,
  height: PropTypes.number,
  onInitialized: PropTypes.func
};

export default Logs;
