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
import {
  Alert,
  Button,
  Icon,
  Modal,
  message,
  Table
} from 'antd';
import SystemDictionaryForm from './forms/SystemDictionaryForm';
import roleModel from '../../utils/roleModel';
import SystemDictionariesUpdate from '../../models/systemDictionaries/SystemDictionariesUpdate';
import SystemDictionariesDelete from '../../models/systemDictionaries/SystemDictionariesDelete';
import SystemDictionariesSync from '../../models/systemDictionaries/SystemDictionariesSync';
import LoadingView from '../special/LoadingView';
import {SplitPanel} from '../special/splitPanel';

import styles from './SystemDictionaries.css';

class SystemDictionaries extends React.Component {
  state = {
    newDictionary: false,
    modified: false,
    pending: false,
    changesCanBeSkipped: false
  };

  componentDidMount () {
    const {route, router} = this.props;
    this.navigateToDefault();
    if (route && router) {
      router.setRouteLeaveHook(route, this.checkModifiedBeforeLeave);
    }
  };

  componentDidUpdate () {
    this.navigateToDefault();
  };

  componentWillUnmount () {
    this.resetChangesStateTimeout && clearTimeout(this.resetChangesStateTimeout);
  }

  navigateToDefault = () => {
    const {currentDictionary} = this.props;
    if (this.dictionaries.length > 0 && currentDictionary && !this.currentDictionary) {
      this.selectDictionary(this.dictionariesNames[0]);
    } else if (!currentDictionary && this.dictionariesNames.length > 0) {
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
    return systemDictionaries.value || [];
  }

  @computed
  get currentDictionary () {
    if (this.state.newDictionary) {
      return {values: []};
    }
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
    const {changesCanBeSkipped, modified} = this.state;
    const resetChangesCanBeSkipped = () => {
      this.resetChangesStateTimeout = setTimeout(
        () => this.setState && this.setState({changesCanBeSkipped: false}),
        0
      );
    };
    const makeTransition = nextLocation => {
      this.setState({changesCanBeSkipped: true},
        () => {
          router.push(nextLocation);
          resetChangesCanBeSkipped();
        }
      );
    };
    if (modified && !changesCanBeSkipped) {
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

  addNewDictionary = () => {
    this.setState({
      newDictionary: true
    });
  };

  syncDictionaries = () => {
    const hide = message.loading('Synchronizing dictionaries...', 0);
    const {systemDictionaries} = this.props;
    this.setState({pending: true}, async () => {
      const request = new SystemDictionariesSync();
      await request.send();
      if (request.error) {
        hide();
        message.error(request.error, 5);
        this.setState({pending: false});
      } else {
        await systemDictionaries.fetch();
        hide();
        this.setState({pending: false, modified: false, newDictionary: false}, () => {
          this.navigateToDefault();
        });
      }
    });
  };

  selectDictionary = (name) => {
    if (this.state.newDictionary) {
      return;
    }
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
        this.setState({pending: false, modified: false, newDictionary: false}, () => {
          router.push(`/dictionaries/${name}`);
        });
      }
    });
  };

  onDictionaryDelete = (name) => {
    if (this.state.newDictionary) {
      this.setState({
        newDictionary: false
      });
    } else {
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
    }
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
    const dataSource = [];
    const {newDictionary} = this.state;
    if (newDictionary) {
      dataSource.push({name: 'New dictionary', isNew: true});
    }
    dataSource.push(...this.dictionariesNames.map(name => ({name})));
    const getRowClassName = (group) => {
      if (newDictionary) {
        if (group.isNew) {
          return `${styles.dictionaryRow} ${styles.selected}`;
        }
        return `${styles.dictionaryRow} ${styles.disabled}`;
      }
      return group.name === currentDictionary
        ? `${styles.dictionaryRow} ${styles.selected}`
        : styles.dictionaryRow;
    };
    return (
      <Table
        className={styles.table}
        dataSource={dataSource}
        columns={columns}
        showHeader={false}
        pagination={false}
        rowKey="name"
        rowClassName={getRowClassName}
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
    if (systemDictionaries.error) {
      return <Alert type="warning" message={systemDictionaries.error} />;
    }
    return (
      <div className={styles.container}>
        <div
          className={styles.actions}
        >
          <Button
            disabled={this.state.pending || this.state.newDictionary}
            onClick={this.addNewDictionary}
          >
            <Icon type="plus" />
            <span>Add dictionary</span>
          </Button>
          <Button
            disabled={this.state.pending}
            type="primary"
            onClick={this.syncDictionaries}
          >
            <Icon type="reload" />
            <span>Synchronize</span>
          </Button>
        </div>
        <div
          style={{flex: 1, minHeight: 0}}
        >
          <SplitPanel
            style={{
              borderTop: '1px solid #ddd'
            }}
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
                isNew={this.state.newDictionary}
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
