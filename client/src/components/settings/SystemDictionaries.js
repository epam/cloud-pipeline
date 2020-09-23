/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {Alert, Modal, Table} from 'antd';

import LoadingView from '../special/LoadingView';
import {SplitPanel} from '../special/splitPanel';
import SystemDictionary from './forms/SystemDictionary';

import styles from './SystemDictionaries.css';

class SystemDictionaries extends React.Component {
  state = {
    changesCanBeSkipped: false,
    operationInProgress: false
  };

  componentDidMount () {
    const {route, router, currentDictionary} = this.props;
    if (!currentDictionary && this.dictionariesNames.length > 0) {
      this.selectDictionary(this.dictionariesNames[0].name);
    }
    if (route && router) {
      router.setRouteLeaveHook(route, this.checkModifiedBeforeLeave);
    }
  };

  componentDidUpdate () {
    const {currentDictionary} = this.props;
    if (!currentDictionary && this.dictionariesNames.length > 0) {
      this.selectDictionary(this.dictionariesNames[0].name);
    }
  };

  @computed
  get dictionaries () {
    const {systemDictionaries} = this.props;
    if (!systemDictionaries.loaded) {
      return {};
    }
    return systemDictionaries.value;
  }

  @computed
  get currentDictionary () {
    const {currentDictionary, systemDictionaries} = this.props;
    if (!systemDictionaries.loaded) {
      return null;
    }
    return this.dictionaries[currentDictionary] || null;
  }

  @computed
  get dictionariesNames () {
    const {systemDictionaries} = this.props;
    if (systemDictionaries.loaded) {
      return (Object.keys(this.dictionaries) || [])
        .sort((a, b) => {
          if (a > b) {
            return 1;
          } else if (a < b) {
            return -1;
          }
          return 0;
        })
        .map(name => ({name}));
    }
    return [];
  }

  @computed
  get dictionaryModified () {
    if (!this.dictionaryForm) {
      return false;
    }
    return this.dictionaryForm.modified;
  }

  dictionaryForm;

  initializeDictionaryForm = (form) => {
    this.dictionaryForm = form;
  };

  checkModifiedBeforeLeave = (nextLocation) => {
    const {router} = this.props;
    const {changesCanBeSkipped} = this.state;
    const makeTransition = nextLocation => {
      this.setState({changesCanBeSkipped: true},
        () => router.push(nextLocation)
      );
    };
    if (this.dictionaryModified && !changesCanBeSkipped) {
      Modal.confirm({
        title: 'You have unsaved changes. Continue?',
        style: {
          wordWrap: 'break-word'
        },
        onOk () {
          makeTransition(nextLocation);
        },
        okText: 'Yes',
        cancelText: 'No'
      });
      return false;
    }
  };

  operationWrapper = (operation) => (...props) => {
    this.setState({
      operationInProgress: true
    }, async () => {
      await operation(...props);
      this.setState({
        operationInProgress: false
      });
    });
  };

  selectDictionary = (name) => {
    const {router} = this.props;
    router && router.push(`settings/dictionaries/${name}`);
  };

  updateDictionary = async (name, values) => {
    const {currentDictionary} = this.props;
    // todo
  };

  renderDictionariesTable = () => {
    const {currentDictionary} = this.props;
    const columns = [
      {
        dataIndex: 'name',
        key: 'name',
        render: (name) => name || 'Other'
      }
    ];
    return (
      <Table
        className={styles.table}
        dataSource={this.dictionariesNames}
        columns={columns}
        showHeader={false}
        pagination={false}
        rowKey="name"
        rowClassName={
          (group) => group.name === currentDictionary
            ? `${styles.dictionaryRow} ${styles.selected}`
            : styles.dictionaryRow
        }
        onRowClick={group => this.selectDictionary(group.name)}
        size="medium" />
    );
  };

  render () {
    const {systemDictionaries, currentDictionary} = this.props;
    if (!systemDictionaries.loaded && systemDictionaries.pending) {
      return <LoadingView />;
    }
    if (systemDictionaries.error) {
      return <Alert type="warning" message={systemDictionaries.error} />;
    }

    return (
      <div className={styles.container}>
        <div
          style={{flex: 1, minHeight: 0}}
        >
          <SplitPanel
            contentInfo={[
              {
                key: 'dictionaries',
                size: {
                  pxDefault: 150
                }
              }
            ]}
          >
            <div key="dictionaries">
              {this.renderDictionariesTable()}
            </div>
            <div
              key="preferences"
              style={{
                display: 'flex',
                flexDirection: 'column'
              }}>
              <SystemDictionary
                pending={this.state.operationInProgress}
                onSubmit={this.operationWrapper(this.updateDictionary)}
                dictionaryName={currentDictionary}
                values={this.currentDictionary}
                wrappedComponentRef={this.initializeDictionaryForm}
              />
            </div>
          </SplitPanel>
        </div>
      </div>
    );
  }
}

export default inject(({systemDictionaries}, {params = {}}) => {
  const {currentDictionary} = params;
  return {
    currentDictionary,
    systemDictionaries
  };
})(observer(SystemDictionaries));
