/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observable} from 'mobx';
import {observer, Provider} from 'mobx-react';
import {Icon, Table} from 'antd';
import classNames from 'classnames';
import ConflictsSession from './session';
import Conflict from './conflict';
import styles from './conflicts.css';

class Conflicts extends React.Component {
  state = {
    current: undefined,
    resolved: false
  };

  @observable session = new ConflictsSession();

  componentDidMount () {
    this.updateFromProps();
    this.updateResolvedState();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.run !== this.props.run ||
      prevProps.storage !== this.props.storage ||
      prevProps.conflicts !== this.props.conflicts ||
      prevProps.mergeInProgress !== this.props.mergeInProgress
    ) {
      this.updateFromProps();
    }
    if (this.session.resolved !== this.state.resolved) {
      this.updateResolvedState();
    } else if (this.session.resolved) {
      this.reportSessionState();
    }
  }

  updateFromProps = () => {
    const {
      conflicts,
      run,
      storage,
      mergeInProgress
    } = this.props;
    this.session.setFiles(
      run,
      storage,
      mergeInProgress,
      conflicts
    );
    this.setState({
      current: (conflicts || [])[0]
    });
  };

  updateResolvedState = () => {
    this.setState({
      resolved: this.session.resolved
    }, this.reportSessionState);
  };

  reportSessionState = () => {
    const {onSessionStateChanged} = this.props;
    onSessionStateChanged && onSessionStateChanged(this.session);
  };

  renderFiles = () => {
    const {current} = this.state;
    const tableColumns = [
      {
        key: 'file',
        dataIndex: 'name'
      },
      {
        key: 'status',
        className: styles.resolvedCell,
        dataIndex: 'resolved',
        render: resolved => {
          if (resolved) {
            return (
              <Icon
                className={styles.resolved}
                type="check-circle"
              />
            );
          }
          return null;
        }
      }
    ];
    const data = (this.session?.files || []).map(file => ({
      name: file.file,
      resolved: file.resolved
    }));
    return (
      <Table
        className={styles.filesTable}
        showHeader={false}
        columns={tableColumns}
        rowKey={file => file.name}
        dataSource={data}
        size="small"
        pagination={false}
        rowClassName={
          file => classNames(
            styles.file,
            {[styles.selectedFile]: file.name === current}
          )
        }
        onRowClick={file => this.setState({current: file.name})}
      />
    );
  };

  render () {
    const {
      current
    } = this.state;
    const {
      disabled,
      run,
      storage
    } = this.props;
    return (
      <div
        className={styles.conflictsIde}
        data-session-hash={this.session.hash}
      >
        <div className={styles.files}>
          {this.renderFiles()}
        </div>
        <Provider conflictsSession={this.session}>
          <div
            className={styles.resolveAreaContainer}
          >
            {
              current && (
                <Conflict
                  disabled={disabled}
                  file={current}
                  run={run}
                  storage={storage}
                />
              )
            }
          </div>
        </Provider>
      </div>
    );
  }
}

Conflicts.propTypes = {
  conflicts: PropTypes.arrayOf(PropTypes.string),
  disabled: PropTypes.bool,
  onSessionStateChanged: PropTypes.func,
  mergeInProgress: PropTypes.bool,
  run: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  storage: PropTypes.object
};

export default observer(Conflicts);
