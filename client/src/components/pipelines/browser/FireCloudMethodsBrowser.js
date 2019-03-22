/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {Row, Table} from 'antd';
import styles from './Browser.css';

@inject('fireCloudMethods')
@observer
export default class FireCloudMethodsBrowser extends React.Component {

  static propTypes = {
    methodSearchString: PropTypes.string,
    onMethodSelect: PropTypes.func
  };

  @computed
  get methods () {
    if (this.props.fireCloudMethods.loaded) {
      let methods = this.props.fireCloudMethods.value || [];
      if (this.props.methodSearchString) {
        methods = methods
          .filter(m =>
            m.name.toLowerCase().indexOf(this.props.methodSearchString.toLowerCase()) >= 0 ||
            m.namespace.toLowerCase().indexOf(this.props.methodSearchString.toLowerCase()) >= 0 ||
            (m.synopsis && m.synopsis.toLowerCase()
              .indexOf(this.props.methodSearchString.toLowerCase()) >= 0)
          );
      }
      return methods.map(m => ({...m, key: `${m.namespace}_${m.name}`}));
    }
    return [];
  }

  onMethodSelect = (method) => {
    this.props.onMethodSelect && this.props.onMethodSelect(method.name, method.namespace);
  };

  columns = [
    {
      dataIndex: 'name',
      title: 'Name',
      className: styles.fireCloudMethodNameColumn,
      render: (name, method) => {
        return (
          <Row>
            <Row style={{fontSize: 'smaller'}}>{method.namespace}</Row>
            <Row style={{color: '#2796dd'}}>{method.name}</Row>
          </Row>
        );
      }
    },
    {
      dataIndex: 'synopsis',
      title: 'Synopsis'
    }
  ];

  render () {
    return (
      <Row type="flex" style={{flex: 1, overflowY: 'auto'}}>
        <Table
          className={styles.childrenContainer}
          rowClassName={() => styles.fireCloudMethodRow}
          onRowClick={this.onMethodSelect}
          style={{width: '100%'}}
          columns={this.columns}
          dataSource={this.methods}
          pagination={false}
          showHeader={false}
          locale={{emptyText: 'No FireCloud methods'}}
          size="small" />
      </Row>
    );
  }
}
