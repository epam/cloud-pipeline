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
import {computed} from 'mobx';
import {Alert, Input, message, Modal, Row, Table} from 'antd';
import PreferencesUpdate from '../../models/preferences/PreferencesUpdate';
import PreferenceGroup from './forms/PreferenceGroup';
import LoadingView from '../special/LoadingView';
import {SplitPanel} from '../special/splitPanel/SplitPanel';
import styles from './Preferences.css';

@inject('preferences', 'router')
@observer
export default class Preferences extends React.Component {

  state = {
    selectedPreferenceGroup: null,
    operationInProgress: false,
    search: null,
    changesCanBeSkipped: false
  };

  componentDidMount () {
    const {route, router} = this.props;
    const {selectedPreferenceGroup} = this.state;
    if (!selectedPreferenceGroup && this.preferencesGroups.length > 0) {
      this.selectPreferenceGroup(this.preferencesGroups[0]);
    }
    if (route && router) {
      router.setRouteLeaveHook(route, this.checkSettingsBeforeLeave);
    }
  };

  componentDidUpdate () {
    if (!this.state.selectedPreferenceGroup && this.preferencesGroups.length > 0) {
      this.selectPreferenceGroup(this.preferencesGroups[0]);
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

  @computed
  get preferencesGroups () {
    if (this.props.preferences.loaded) {
      return (this.props.preferences.value || [])
        .map(p => p.preferenceGroup)
        .filter((element, index, array) => {
          return array.indexOf(element) === index;
        })
        .sort((a, b) => {
          if (a > b) {
            return 1;
          } else if (a < b) {
            return -1;
          }
          return 0;
        });
    }
    return [];
  }

  @computed
  get preferences () {
    if (this.props.preferences.loaded) {
      if (this.state.search) {
        return (this.props.preferences.value || [])
          .filter(p => p.name.toLowerCase().indexOf(this.state.search.toLowerCase()) >= 0);
      }
      return (this.props.preferences.value || [])
        .filter(p => p.preferenceGroup === this.state.selectedPreferenceGroup);
    }
    return [];
  }

  checkSettingsBeforeLeave = (nextLocation) => {
    const {router} = this.props;
    const {changesCanBeSkipped} = this.state;
    const makeTransition = nextLocation => {
      this.setState({changesCanBeSkipped: true},
        () => router.push(nextLocation)
      );
    };
    if (this.templateModified && !changesCanBeSkipped) {
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

  selectPreferenceGroup = (name) => {
    const changePreferenceGroup = () => {
      this.setState({
        selectedPreferenceGroup: name
      });
    };
    if (this.state.selectedPreferenceGroup && this.templateModified) {
      Modal.confirm({
        title: 'You have unsaved changes. Continue?',
        style: {
          wordWrap: 'break-word'
        },
        async onOk () {
          changePreferenceGroup();
        },
        okText: 'Yes',
        cancelText: 'No'
      });
    } else {
      changePreferenceGroup();
    }
  };

  renderPreferenceGroupsTable = () => {
    const columns = [
      {
        dataIndex: 'name',
        key: 'name',
        render: (name) => name || 'Other'
      }
    ];
    const dataSource = this.state.search
      ? [{name: `Search '${this.state.search}'`}]
      : this.preferencesGroups.map(g => ({name: g}));
    return (
      <Table
        className={styles.table}
        dataSource={dataSource}
        columns={columns}
        showHeader={false}
        pagination={false}
        rowKey="name"
        rowClassName={
          (group) => group.name === this.state.selectedPreferenceGroup
            ? `${styles.preferenceGroupRow} ${styles.selected}`
            : styles.preferenceGroupRow
        }
        onRowClick={group => this.selectPreferenceGroup(group.name)}
        size="medium" />
    );
  };

  preferenceGroupForm;

  initializePreferenceGroupForm = (wrappedComponent) => {
    this.preferenceGroupForm = wrappedComponent;
  };

  @computed
  get templateModified () {
    if (!this.preferenceGroupForm) {
      return false;
    }
    return this.preferenceGroupForm.modified;
  }

  reload = async (clearState = false) => {
    this.props.preferences.fetch();
    if (clearState) {
      this.preferenceGroupForm && this.preferenceGroupForm.resetFormFields();
      this.setState({
        selectedPreferenceGroup: null
      });
    }
  };

  updatePreferences = async (preferences) => {
    const hide = message.loading('Updating preferences...', -1);
    const request = new PreferencesUpdate();
    await request.send(preferences);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await this.props.preferences.fetch();
      this.preferenceGroupForm && this.preferenceGroupForm.resetFormFields();
      hide();
    }
  };

  onSearch = (search) => {
    this.setState({
      search: search
    });
  };

  onChange = (e) => {
    if (!e.target.value) {
      this.setState({
        search: null
      });
    }
  };

  render () {
    if (!this.props.preferences.loaded && this.props.preferences.pending) {
      return <LoadingView />;
    }
    if (this.props.preferences.error) {
      return <Alert type="warning" message={this.props.preferences.error} />
    }
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: 0
        }}
      >
        <Row type="flex" style={{marginBottom: 10}}>
          <Input.Search
            size="small"
            onChange={this.onChange}
            onSearch={this.onSearch} />
        </Row>
        <div
          style={{flex: 1, minHeight: 0}}
        >
          <SplitPanel
            contentInfo={[
              {
                key: 'groups',
                size: {
                  pxDefault: 150
                }
              }
            ]}
          >
            <div key="groups">
              {this.renderPreferenceGroupsTable()}
            </div>
            <div
              key="preferences"
              style={{
                display: 'flex',
                flexDirection: 'column'
              }}>
              <PreferenceGroup
                pending={this.state.operationInProgress}
                onSubmit={this.operationWrapper(this.updatePreferences)}
                group={this.state.selectedPreferenceGroup}
                preferences={this.preferences}
                wrappedComponentRef={this.initializePreferenceGroupForm}
                search={this.state.search} />
            </div>
          </SplitPanel>
        </div>
      </div>
    );
  }
}
