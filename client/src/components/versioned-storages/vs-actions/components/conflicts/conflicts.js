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
import {Button, Table} from 'antd';
import classNames from 'classnames';
import Conflict from './conflict';
import styles from './conflicts.css';

class Conflicts extends React.Component {
  state = {
    conflicts: [],
    current: undefined
  };

  tableColumns = [
    {
      key: 'file',
      dataIndex: 'file'
    }
  ];

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.run !== this.props.run ||
      prevProps.storage !== this.props.storage ||
      prevProps.conflicts !== this.props.conflicts
    ) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {conflicts} = this.props;
    this.setState({
      conflicts: (conflicts || []).map(conflict => ({file: conflict})),
      current: (conflicts || [])[0]
    });
  };

  renderFiles = () => {
    const {
      conflicts,
      current
    } = this.state;
    return (
      <Table
        showHeader={false}
        columns={this.tableColumns}
        rowKey={conflict => conflict.file}
        dataSource={conflicts}
        size="small"
        pagination={false}
        rowClassName={conflict => classNames({[styles.selectedFile]: conflict.file === current})}
        onRowClick={conflict => this.setState({current: conflict.file})}
      />
    );
  };

  render () {
    const {
      current
    } = this.state;
    const {
      run,
      storage
    } = this.props;
    return (
      <div className={styles.conflictsIde}>
        <div className={styles.files}>
          {this.renderFiles()}
        </div>
        <div className={styles.resolveAreaContainer}>
          {
            current && (
              <Conflict
                file={current}
                run={run}
                storage={storage}
              />
            )
          }
        </div>
      </div>
    );
  }
}

Conflicts.propTypes = {
  conflicts: PropTypes.arrayOf(PropTypes.string),
  run: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  storage: PropTypes.object
};

export default Conflicts;
