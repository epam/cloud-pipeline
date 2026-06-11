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
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {Input, Modal} from 'antd';
import {observable} from 'mobx';
import AllowedInstanceTypes from '../../../../models/utils/AllowedInstanceTypes';
import LaunchPipelineForm from '../../../pipelines/launch/form/LaunchPipelineForm';

const emptyConfigurations = [];

function payloadToFormParameters (payload) {
  if (!payload) {
    return {};
  }
  return {
    instance_size: payload.instanceType,
    instance_disk: payload.hddSize !== undefined ? `${payload.hddSize}` : undefined,
    cloudRegionId: payload.cloudRegionId,
    is_spot: payload.isSpot !== undefined ? `${payload.isSpot}` : undefined,
    cmd_template: payload.cmdTemplate,
    timeout: payload.timeout !== undefined ? `${payload.timeout}` : undefined,
    parameters: payload.params
  };
}

@inject('preferences')
@observer
class LaunchProfileForm extends React.Component {
  static propTypes = {
    profileId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    profile: PropTypes.shape({
      id: PropTypes.number,
      name: PropTypes.string,
      payload: PropTypes.object
    }),
    onSave: PropTypes.func,
    onDelete: PropTypes.func,
    onModified: PropTypes.func,
    saving: PropTypes.bool
  };

  state = {
    name: '',
    nameError: '',
    formModified: false,
    parameters: {}
  };

  @observable allowedInstanceTypes = new AllowedInstanceTypes({
    requestAllRegionsForProviders: ['GCP', 'AWS']
  });

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState) {
    const {onModified, profileId, profile} = this.props;
    if (profileId !== prevProps.profileId || profile !== prevProps.profile) {
      this.updateFromProps();
    } else if (onModified && this.isModified !== this._lastModified) {
      this._lastModified = this.isModified;
      onModified(this.isModified);
    }
  }

  updateFromProps = () => {
    const {profile} = this.props;
    this.setState({
      name: profile ? profile.name : '',
      parameters: payloadToFormParameters(profile ? profile.payload : undefined),
      formModified: false
    });
    const {payload} = profile || {};
    if (payload && typeof payload === 'object') {
      const {
        cloudRegionId,
        isSpot
      } = payload;
      this.allowedInstanceTypes.setParameters({
        regionId: cloudRegionId,
        spot: isSpot
      });
    }
  };

  handleSave = async (payload) => {
    const {name} = this.state;
    if (!name.trim()) {
      this.setState({nameError: 'Profile name is required'});
      return;
    }
    const {onSave} = this.props;
    if (onSave) {
      await onSave(name.trim(), payload);
      this.updateFromProps();
    }
  };

  handleReset = () => {
    const {profile, onDelete} = this.props;
    this.updateFromProps();
    if (!profile && onDelete) {
      onDelete();
    }
  };

  handleDelete = () => {
    const {onDelete, profile} = this.props;
    if (!onDelete) return;
    Modal.confirm({
      title: `Delete profile "${profile ? profile.name : ''}"?`,
      onOk: onDelete
    });
  };

  handleModified = (modified) => {
    this.setState({formModified: modified});
  };

  get isModified () {
    const {profile, profileId} = this.props;
    const {name, formModified} = this.state;
    if (profileId === 'new' || !profile) {
      return true;
    }
    return name.trim() !== (profile.name || '').trim() || formModified;
  }

  render () {
    const {saving, preferences, profile, profileId} = this.props;
    const {name, nameError, parameters} = this.state;
    return (
      <div style={{display: 'flex', flexDirection: 'column', height: '100%', overflow: 'auto'}}>
        <div style={{padding: '8px 16px 0'}}>
          <div
            style={{
              display: 'flex',
              flexDirection: 'row',
              alignItems: 'center',
              marginBottom: nameError ? 0 : 16
            }}
          >
            <span style={{fontWeight: 'bold'}}>
              {'Profile name'}
              <span style={{color: 'red', marginLeft: 4}}>{'*'}</span>
            </span>
            <Input
              value={name}
              onChange={(e) => this.setState({name: e.target.value, nameError: ''})}
              placeholder="Profile name"
              style={{flex: 1, marginLeft: 5}}
            />
          </div>
          {nameError && (
            <div style={{color: 'red', marginBottom: 16, marginTop: 4}}>
              {nameError}
            </div>
          )}
        </div>
        <div style={{flex: 1, overflow: 'auto'}}>
          <LaunchPipelineForm
            key={profileId}
            style={{height: '100%', overflow: 'auto'}}
            launchProfile={{
              onSave: this.handleSave,
              onReset: this.handleReset,
              onDelete: this.handleDelete,
              newProfile: !profile,
              saveDisabled: saving || !this.isModified
            }}
            pending={saving}
            parameters={parameters}
            configurations={emptyConfigurations}
            allowedInstanceTypes={this.allowedInstanceTypes}
            defaultPriceTypeIsSpot={preferences.useSpot}
            editConfigurationMode={false}
            isDetachedConfiguration={false}
            onModified={this.handleModified}
          />
        </div>
      </div>
    );
  }
}

export default LaunchProfileForm;
