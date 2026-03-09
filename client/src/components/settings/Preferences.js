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
import {computed, makeObservable} from 'mobx';
import {Alert, Input, message, Modal, Row} from 'antd';
import PreferencesUpdate from '../../models/preferences/PreferencesUpdate';
import PreferenceGroup from './forms/PreferenceGroup';
import LoadingView from '../special/LoadingView';
import SubSettings from './sub-settings';

@inject('preferences', 'router', 'authenticatedUserInfo')
@observer
export default class Preferences extends React.Component {
  state = {
    operationInProgress: false,
    search: null,
    changesCanBeSkipped: false
  };

  constructor (props) {
    super(props);
    makeObservable(this, {
      preferencesGroups: computed,
      preferences: computed,
      templateModified: computed
    });
  }

  componentDidMount () {
    const {route, router, preferences} = this.props;
    if (route && router) {
      router.setRouteLeaveHook(route, this.checkSettingsBeforeLeave);
    }
    preferences.fetch();
  }

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

  get preferences () {
    if (this.props.preferences.loaded) {
      return (this.props.preferences.value || []).slice();
    }
    return [];
  }

  get filteredPreferencesGroups () {
    const {search} = this.state;
    return search
      ? [{name: `Search '${search}'`}]
      : this.preferencesGroups.map(g => ({name: g}));
  }

  getPreferencesForGroup = (group) => {
    const {search} = this.state;
    if (search) {
      return this.preferences
        .filter(p => p.name.toLowerCase().indexOf(search.toLowerCase()) >= 0);
    }
    return this.preferences
      .filter(p => p.preferenceGroup === group);
  };

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

  canChangePreferenceGroup = () => {
    return new Promise((resolve) => {
      if (this.templateModified) {
        Modal.confirm({
          title: 'You have unsaved changes. Continue?',
          style: {
            wordWrap: 'break-word'
          },
          onOk () {
            resolve(true);
          },
          onCancel () {
            resolve(false);
          },
          okText: 'Yes',
          cancelText: 'No'
        });
      } else {
        resolve(true);
      }
    });
  };

  preferenceGroupFormRef = React.createRef();

  get templateModified () {
    if (!this.preferenceGroupFormRef.current) {
      return false;
    }
    return this.preferenceGroupFormRef.current.modified;
  }

  updatePreferences = async (preferences) => {
    const hide = message.loading('Updating preferences...', -1);
    const request = new PreferencesUpdate();
    await request.send(preferences);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await this.props.preferences.fetch();
      this.preferenceGroupFormRef.current && this.preferenceGroupFormRef.current.resetFormFields();
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
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return null;
    }
    if (!this.props.authenticatedUserInfo.value.admin) {
      return (
        <Alert type="error" title="Access is denied" />
      );
    }
    if (!this.props.preferences.loaded && this.props.preferences.pending) {
      return <LoadingView />;
    }
    if (this.props.preferences.error) {
      return <Alert type="warning" title={this.props.preferences.error} />;
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
            placeholder="Search preferences"
            onChange={this.onChange}
            onSearch={this.onSearch}
          />
        </Row>
        <SubSettings
          sections={
            this.filteredPreferencesGroups.map(group => ({
              key: group.name,
              title: group.name,
              disabled: this.state.operationInProgress
            }))
          }
          canNavigate={this.canChangePreferenceGroup}
        >
          {
            ({section: group}) => (
              <PreferenceGroup
                pending={this.state.operationInProgress}
                onSubmit={this.operationWrapper(this.updatePreferences)}
                group={group.key}
                preferences={this.getPreferencesForGroup(group.key)}
                ref={this.preferenceGroupFormRef}
                search={this.state.search}
                router={this.props.router}
              />
            )
          }
        </SubSettings>
      </div>
    );
  }
}
