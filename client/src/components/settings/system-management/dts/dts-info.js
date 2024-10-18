/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import PropTypes from 'prop-types';
import {Modal, Button, Spin, Checkbox, Input, Icon, Tabs} from 'antd';
import LocalSyncDtsPreference from './components/local-sync-dts-preference';
import DtsLogs from './components/dts-logs';
import {
  mapPreferences,
  unMapPreferences,
  getDtsLogsLink,
  getModifiedPreferences,
  getErrorPreferences
} from './utils';
import styles from './dts-info.css';

class DtsInfo extends React.Component {
  state = {
    preferences: [],
    initialPreferences: [],
    drafts: [],
    modifiedPreferences: [],
    errors: [],
    logsFolder: undefined
  }

  componentDidMount () {
    this.initializeState();
    this.checkLogsAvailable();
  }

  componentDidUpdate (prevProps) {
    if (
      prevProps.dts.id !== this.props.dts.id ||
      prevProps.refreshToken !== this.props.refreshToken
    ) {
      this.initializeState();
      this.checkLogsAvailable();
    }
  }

  get preferencesWithDrafts () {
    const {preferences, drafts} = this.state;
    return [...preferences, ...drafts];
  }

  initializeState = () => {
    const {dts = {}} = this.props;
    const initial = Object.entries({
      'dts.local.sync.rules': '[]',
      'dts.heartbeat.enabled': 'false',
      ...(dts.preferences || {})
    }).map(([key, value]) => ({key, value}));
    const mappedPreferences = mapPreferences(initial);
    this.setState({
      preferences: mappedPreferences.slice(),
      initialPreferences: mappedPreferences,
      drafts: [],
      modifiedPreferences: [],
      errors: []
    }, this.validate);
  };

  checkLogsAvailable = () => {
    const {dts} = this.props;
    const logsFolder = getDtsLogsLink(dts);
    this.setState({logsFolder});
  };

  validate = () => {
    const {onChange} = this.props;
    const {initialPreferences, preferences, drafts} = this.state;
    const modifiedPreferences = [
      ...getModifiedPreferences(initialPreferences, preferences),
      ...drafts.filter(({key, value}) => key)
    ];
    const errors = getErrorPreferences([...preferences, ...drafts]);
    this.setState({modifiedPreferences, errors});
    onChange && onChange(modifiedPreferences.length > 0);
  };

  addPreference = () => {
    const drafts = [...this.state.drafts];
    drafts.push({
      key: '',
      value: '',
      draftIndex: drafts.length,
      draft: true
    });
    this.setState({drafts}, this.validate);
  };

  removePreference = (preference) => {
    const {drafts} = this.state;
    if (preference.draft) {
      const filtered = drafts.filter(({draftIndex}) => draftIndex !== preference.draftIndex);
      return this.setState({drafts: filtered}, this.validate);
    } else {
      this.onChangePreference(preference, {
        ...preference,
        markAsDeleted: !preference.markAsDeleted
      });
    }
  };

  onChangePreference = (preference, update) => {
    const {drafts, preferences} = this.state;
    if (preference.draft) {
      const draftIndex = drafts
        .findIndex(({draftIndex}) => draftIndex === preference.draftIndex);
      if (draftIndex >= 0) {
        drafts.splice(draftIndex, 1, update);
        this.setState({drafts: [...drafts]});
      }
    } else {
      const preferenceIndex = preferences
        .findIndex(({key}) => key === preference.key);
      if (preferenceIndex >= 0) {
        preferences.splice(preferenceIndex, 1, update);
        this.setState({preferences: [...preferences]});
      }
    }
    this.validate();
  };

  onSave = () => {
    const {dts, onSave} = this.props;
    const {preferences, modifiedPreferences} = this.state;
    onSave && onSave({
      dts,
      toUpdate: unMapPreferences(modifiedPreferences.filter(({markAsDeleted}) => !markAsDeleted)),
      toDelete: preferences.filter(preference => preference.markAsDeleted)
    });
  };

  onRevert = () => this.initializeState();

  onDeleteDts = () => {
    const {onDeleteDts, dts} = this.props;
    Modal.confirm({
      title: `Are you sure you want to delete ${dts.name || 'DTS'}?`,
      okText: 'Delete',
      okType: 'danger',
      onOk: () => onDeleteDts && onDeleteDts(dts)
    });
  };

  renderCheckboxPreference = (preference, index) => {
    const checked = `${preference.value}` === 'true';
    const onChange = (event) => {
      this.onChangePreference(preference, {
        ...preference,
        value: `${event.target.checked}`
      });
    };
    return (
      <div key={preference.key} style={{margin: '10px 0'}}>
        <Checkbox
          onChange={onChange}
          checked={checked}
          className={styles.preference}
        >
          <b>{preference.key}</b>
        </Checkbox>
      </div>
    );
  };

  getPreferenceError = (preference) => {
    const {errors} = this.state;
    return errors.find(({text, preference: errorPreference}) => {
      if (preference.draft) {
        return errorPreference.draftIndex === preference.draftIndex;
      }
      return errorPreference.draftIndex === undefined &&
        preference.key === errorPreference.key;
    });
  };

  renderStringPreference = (preference, index) => {
    const onChange = (field) => (event) => {
      this.onChangePreference(preference, {
        ...preference,
        [field]: event.target.value
      });
    };
    const error = this.getPreferenceError(preference);
    return (
      <div
        key={index}
        className={classNames(
          styles.preferenceContainer, {
            [styles.error]: !!error
          }
        )}
      >
        <div
          className={classNames(
            styles.preference,
            styles.stringPreference,
            {
              [styles.markAsDeleted]: preference.markAsDeleted
            }
          )}
          style={{
            background: preference.markAsDeleted ? '#ffccc7' : ''
          }}
        >
          <div className={styles.inputContainer}>
            <span>Key:</span>
            <Input
              size="small"
              value={preference.key}
              onChange={onChange('key')}
              disabled={!preference.draft}
            />
          </div>
          <div className={styles.inputContainer} style={{marginLeft: 3}}>
            <span>
              Value:
            </span>
            <Input
              size="small"
              value={preference.value}
              onChange={onChange('value')}
            />
          </div>
          <Button
            onClick={() => this.removePreference(preference)}
            size="small"
            type="danger"
          >
            <Icon type="delete" />
          </Button>
        </div>
        {error ? (
          <div
            className={classNames(
              'cp-error',
              {[styles.errorText]: !!error}
            )}
          >
            {error.text}
          </div>
        ) : null}
      </div>
    );
  };

  renderPreference = (preference, index) => {
    const renderFn = this.preferenceRenderers[preference.key];
    if (renderFn) {
      return renderFn(preference);
    }
    return this.renderStringPreference(preference, index);
  };

  preferenceRenderers = {
    'dts.heartbeat.enabled': this.renderCheckboxPreference,
    'dts.local.sync.rules': (preference) => (
      <LocalSyncDtsPreference
        preference={preference}
        onChange={this.onChangePreference}
        className={styles.preference}
        key={preference.key}
      />
    )
  };

  renderDtsTab = () => {
    const {modifiedPreferences, errors} = this.state;
    return (
      <div style={{display: 'flex', flexDirection: 'column'}}>
        {this.preferencesWithDrafts.map(this.renderPreference)}
        <Button
          onClick={this.addPreference}
          style={{width: 150}}
          size="small"
        >
          <Icon type="plus" /> Add preference
        </Button>
        <div
          style={{display: 'flex', justifyContent: 'flex-end', gap: 5}}
        >
          <Button
            onClick={this.onDeleteDts}
            size="small"
            type="danger"
            style={{marginRight: 15}}
          >
            Delete
          </Button>
          <Button
            disabled={modifiedPreferences.length === 0}
            onClick={this.onRevert}
            size="small"
          >
            Revert
          </Button>
          <Button
            disabled={errors.length > 0 || modifiedPreferences.length === 0}
            onClick={this.onSave}
            size="small"
            type="primary"
          >
            Save
          </Button>
        </div>
      </div>
    );
  };

  render () {
    const {pending} = this.props;
    const {logsFolder} = this.state;
    return (
      <Tabs className={styles.tabs} defaultActiveKey="dts" size="small" onChange={this.onChangeTab}>
        <Tabs.TabPane tab="DTS" key="dts">
          <Spin spinning={pending}>
            {this.renderDtsTab()}
          </Spin>
        </Tabs.TabPane>
        {logsFolder ? (
          <Tabs.TabPane tab="LOGS" key="logs" style={{height: '100%'}}>
            <DtsLogs
              folder={logsFolder}
            />
          </Tabs.TabPane>
        ) : null}
      </Tabs>
    );
  }
}

DtsInfo.propTypes = {
  dts: PropTypes.object,
  onSave: PropTypes.func,
  onChange: PropTypes.func,
  pending: PropTypes.bool,
  refreshToken: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  onDeleteDts: PropTypes.func
};

export default DtsInfo;
