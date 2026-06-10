/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import {message} from 'antd';
import {inject, observer} from 'mobx-react';
import PipelineRunUpdateSids, {AccessTypes} from '../../models/pipelines/PipelineRunUpdateSids';
import ShareWithForm, {ROLE_ALL, shouldCombineRoles} from './logs/forms/ShareWithForm';
import UserName from '../shared/user-name';
import roleModel from '../../utils/roleModel';
import Roles from '../../models/user/Roles';

@inject(() => ({
  roles: new Roles(),
}))
@observer
export default class ShareWith extends React.Component {
  state = {
    shareDialogOpened: false,
    operationInProgress: false,
  };

  get combineRolesIntoAllRoles() {
    const {run} = this.props;
    const {runSids = []} = run || {};
    return {
      ssh: shouldCombineRoles(runSids, ROLE_ALL.includedRoles, AccessTypes.ssh),
      endpoint: shouldCombineRoles(runSids, ROLE_ALL.includedRoles, AccessTypes.endpoint),
    };
  }

  get endpointAvailable() {
    const {run} = this.props;
    if (run?.initialized) {
      return run.serviceUrl;
    }
    return false;
  }

  get shareList() {
    const {run = {}} = this.props;
    const {runSids = []} = run;
    let shareList = 'Not shared (click to configure)';
    const {ssh: combineSshRoles, endpoint: combineEndpointRoles} = this.combineRolesIntoAllRoles;
    const filteredRunSids =
      combineSshRoles || combineEndpointRoles
        ? [ROLE_ALL, ...runSids].filter(({name, accessType}) => {
            if (
              (combineSshRoles && accessType === AccessTypes.ssh) ||
              (combineEndpointRoles && accessType === AccessTypes.endpoint)
            ) {
              return !ROLE_ALL.includedRoles.includes(name);
            }
            return true;
          })
        : runSids;
    if (filteredRunSids.length > 0) {
      shareList = filteredRunSids.map((s, index, array) => {
        return (
          <span key={s.name} style={{marginRight: 5, cursor: 'pointer'}}>
            <UserName style={{cursor: 'inherit'}} userName={s.name} />
            {index < array.length - 1 ? ',' : undefined}
          </span>
        );
      });
    }
    return shareList;
  }

  openShareDialog = () => {
    this.setState({
      shareDialogOpened: true,
    });
  };

  closeShareDialog = () => {
    this.setState({
      shareDialogOpened: false,
    });
  };

  operationWrapper =
    (operation) =>
    (...props) => {
      this.setState(
        {
          operationInProgress: true,
        },
        async () => {
          await operation(...props);
          this.setState({
            operationInProgress: false,
          });
        },
      );
    };

  saveShareSids = async (sids) => {
    const {run, onSave} = this.props;
    const hide = message.loading('Updating sharing info...', -1);
    const request = new PipelineRunUpdateSids(run.id);
    await request.send(sids);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      if (onSave) {
        onSave();
      }
      hide();
      this.closeShareDialog();
    }
  };

  render() {
    const {run = {}} = this.props;
    const {runSids = []} = run;
    const canShare = run?.initialized && run.status === 'RUNNING' && roleModel.isOwner(run);
    if (!canShare) {
      return null;
    }
    return (
      <div>
        <a style={{cursor: 'pointer'}} onClick={this.openShareDialog}>
          {this.shareList}
        </a>
        <ShareWithForm
          endpointsAvailable={!!this.endpointAvailable}
          visible={this.state.shareDialogOpened}
          roles={this.props.roles.loaded ? (this.props.roles.value || []).map((r) => r) : []}
          sids={(runSids || []).map((s) => s)}
          pending={this.state.operationInProgress}
          onSave={this.operationWrapper(this.saveShareSids)}
          onClose={this.closeShareDialog}
          runSharing
        />
      </div>
    );
  }
}

ShareWith.propTypes = {
  run: PropTypes.object,
  onSave: PropTypes.func,
  roles: PropTypes.array,
};
