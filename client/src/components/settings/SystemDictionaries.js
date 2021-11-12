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
import classNames from 'classnames';
import {
  Alert,
  Button,
  Icon,
  Input,
  Modal,
  message,
  Table
} from 'antd';
import SystemDictionaryForm from './forms/SystemDictionaryForm';
import roleModel from '../../utils/roleModel';
import SystemDictionariesUpdate from '../../models/systemDictionaries/SystemDictionariesUpdate';
import SystemDictionariesDelete from '../../models/systemDictionaries/SystemDictionariesDelete';
import LoadingView from '../special/LoadingView';
import {SplitPanel} from '../special/splitPanel';

import styles from './SystemDictionaries.css';

function nameSorter (a, b) {
  const aName = (a.name || '').toLowerCase();
  const bName = (b.name || '').toLowerCase();
  if (aName === bName) {
    return 0;
  }
  return aName < bName ? -1 : 1;
}

class SystemDictionaries extends React.Component {
  state = {
    newDictionary: false,
    modified: false,
    pending: false,
    changesCanBeSkipped: false,
    navigating: false,
    filter: undefined
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
    const {pending, navigating} = this.state;
    if (pending || navigating) {
      return;
    }
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
      return [];
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

  selectDictionary = (name) => {
    if (this.state.newDictionary) {
      return;
    }
    const {router} = this.props;
    router && router.push(`settings/dictionaries/${encodeURIComponent(name)}`);
  };

  onDictionaryChanged = (name, items, changed) => {
    this.setState({modified: changed});
  };

  onDictionarySave = (id, name, items) => {
    const {currentDictionary} = this.props;
    const hide = message.loading('Saving dictionary...', 0);
    const {systemDictionaries, router} = this.props;
    this.setState({pending: true}, async () => {
      const request = new SystemDictionariesUpdate();
      await request.send({
        id: id,
        key: name,
        values: items
      });
      if (request.error) {
        hide();
        message.error(request.error, 5);
        this.setState({pending: false});
      } else {
        await systemDictionaries.fetch();
        hide();
        this.setState({
          pending: false,
          modified: false,
          newDictionary: false,
          navigating: true
        }, () => {
          if (currentDictionary !== name) {
            router.push(`/settings/dictionaries/${encodeURIComponent(name)}`);
            this.setState({navigating: false});
          }
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
          this.setState({
            pending: false,
            modified: false,
            navigating: true
          }, () => {
            router.push('/settings/dictionaries');
            this.setState({navigating: false});
          });
        }
      });
    }
  };

  onFilter = (e) => {
    this.setState({filter: e.target.value});
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
    const {newDictionary, filter} = this.state;
    if (newDictionary) {
      dataSource.push({name: 'New dictionary', isNew: true});
    }
    const lowerCasedFilter = (filter || '').toLowerCase();
    dataSource.push(
      ...this.dictionaries
        .filter((dict) => !filter ||
          (dict.values || [])
            .find(v => (v.value || '').toLowerCase().indexOf(lowerCasedFilter) >= 0)
        )
        .map((dict) => ({name: dict.key}))
        .sort(nameSorter)
    );
    const getRowClassName = (group) => {
      return classNames(
        'cp-settings-sidebar-element',
        {
          'cp-table-element-selected': (newDictionary && group.isNew) ||
            group.name === currentDictionary,
          'cp-table-element-disabled': newDictionary && !group.isNew
        }
      );
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
    if (!authenticatedUserInfo.loaded && authenticatedUserInfo.pending) {
      return null;
    }
    if (!authenticatedUserInfo.value.admin) {
      return (
        <Alert type="error" message="Access is denied" />
      );
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
          <Input.Search
            style={{flex: 1}}
            value={this.state.filter}
            onChange={this.onFilter}
            placeholder="Search dictionary"
          />
          <Button
            className={styles.action}
            disabled={this.state.pending || this.state.newDictionary}
            onClick={this.addNewDictionary}
          >
            <Icon type="plus" />
            <span>Add dictionary</span>
          </Button>
        </div>
        <div
          style={{flex: 1, minHeight: 0}}
        >
          <SplitPanel
            contentInfo={[
              {
                key: 'dictionaries',
                size: {
                  pxDefault: 300
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
                padding: 5,
                maxHeight: '100%'
              }}>
              <SystemDictionaryForm
                filter={this.state.filter}
                disabled={this.state.pending}
                onDelete={this.onDictionaryDelete}
                onSave={this.onDictionarySave}
                onChange={this.onDictionaryChanged}
                id={this.currentDictionary ? this.currentDictionary.id : undefined}
                name={this.currentDictionary ? this.currentDictionary.key : undefined}
                items={
                  this.currentDictionary
                    ? (this.currentDictionary.values || []).map(o => o)
                    : []
                }
                dictionaries={this.dictionaries.map(d => d)}
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
