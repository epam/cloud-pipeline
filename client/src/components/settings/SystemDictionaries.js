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
import {Alert, Modal, message, Table} from 'antd';
import SystemDictionaryForm from './forms/SystemDictionaryForm';
import roleModel from '../../utils/roleModel';
import SystemDictionariesUpdate from '../../models/systemDictionaries/SystemDictionariesUpdate';
import SystemDictionariesDelete from '../../models/systemDictionaries/SystemDictionariesDelete';
import LoadingView from '../special/LoadingView';
import {SplitPanel} from '../special/splitPanel';

import styles from './SystemDictionaries.css';

class SystemDictionaries extends React.Component {
  state = {
    modified: false,
    pending: false,
    changesCanBeSkipped: false
  };

  componentDidMount () {
    const {route, router, currentDictionary} = this.props;
    if (!currentDictionary && this.dictionariesNames.length > 0) {
      this.selectDictionary(this.dictionariesNames[0]);
    }
    if (route && router) {
      router.setRouteLeaveHook(route, this.checkModifiedBeforeLeave);
    }
  };

  componentDidUpdate () {
    const {currentDictionary} = this.props;
    if (!currentDictionary && this.dictionariesNames.length > 0) {
      this.selectDictionary(this.dictionariesNames[0]);
    }
  };

  @computed
  get dictionaries () {
    const {systemDictionaries} = this.props;
    if (!systemDictionaries.loaded) {
      return [
        {key: 'billing-center', values: ['1', '2', '3']},
        {key: 'dictionary', values: ['a', 'b', 'c']}
      ];
    }
    return systemDictionaries.value;
  }

  @computed
  get currentDictionary () {
    const {currentDictionary} = this.props;
    return this.dictionaries.find(dict => dict.key === currentDictionary);
  }

  @computed
  get dictionariesNames () {
    const names = this.dictionaries.map(dict => dict.key);
    names.sort();
    return names;
  }

  checkModifiedBeforeLeave = (nextLocation) => {
    const {router} = this.props;
    const {changesCanBeSkipped} = this.state;
    const makeTransition = nextLocation => {
      this.setState({changesCanBeSkipped: true},
        () => router.push(nextLocation)
      );
    };
    if (this.state.modified && !changesCanBeSkipped) {
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

  selectDictionary = (name) => {
    const {router} = this.props;
    router && router.push(`settings/dictionaries/${name}`);
  };

  onDictionaryChanged = (name, items, changed) => {
    this.setState({modified: changed});
  };

  onDictionarySave = (name, items) => {
    const hide = message.loading('Saving dictionary...', 0);
    const {systemDictionaries, router} = this.props;
    this.setState({pending: true}, async () => {
      const request = new SystemDictionariesUpdate();
      await request.send([{
        key: name,
        values: items
      }]);
      if (request.error) {
        hide();
        message.error(request.error, 5);
        this.setState({pending: false});
      } else {
        await systemDictionaries.fetch();
        hide();
        this.setState({pending: false, modified: false}, () => {
          router.push(`/dictionaries/${name}`);
        });
      }
    });
  };

  onDictionaryDelete = (name) => {
    const hide = message.loading('Removing dictionary...', 0);
    const {systemDictionaries, router} = this.props;
    this.setState({pending: true}, async () => {
      const request = new SystemDictionariesDelete(name);
      await request.send();
      if (request.error) {
        hide();
        message.error(request.error, 5);
        this.setState({pending: false});
      } else {
        await systemDictionaries.fetch();
        hide();
        this.setState({pending: false, modified: false}, () => {
          router.push('/dictionaries');
        });
      }
    });
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
        dataSource={this.dictionariesNames.map(name => ({name}))}
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
    const {authenticatedUserInfo} = this.props;
    if (!authenticatedUserInfo.loaded || !authenticatedUserInfo.value.admin) {
      return null;
    }
    const {systemDictionaries} = this.props;
    if (!systemDictionaries.loaded && systemDictionaries.pending) {
      return <LoadingView />;
    }
    // if (systemDictionaries.error) {
    //   return <Alert type="warning" message={systemDictionaries.error} />;
    // }
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
            <div key="dictionaries" style={{padding: 5}}>
              {this.renderDictionariesTable()}
            </div>
            <div
              key="preferences"
              style={{
                display: 'flex',
                flexDirection: 'column',
                padding: 5
              }}>
              <SystemDictionaryForm
                disabled={this.state.pending}
                onDelete={this.onDictionaryDelete}
                onSave={this.onDictionarySave}
                onChange={this.onDictionaryChanged}
                name={this.currentDictionary ? this.currentDictionary.key : undefined}
                items={this.currentDictionary ? (this.currentDictionary.values || []) : []}
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
})(
  roleModel.authenticationInfo(
    observer(SystemDictionaries)
  )
);
