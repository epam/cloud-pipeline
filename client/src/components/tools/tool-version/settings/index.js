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
import {inject, observer} from 'mobx-react';
import {computed, observable, makeObservable} from 'mobx';
import LoadTool from '../../../../models/tools/LoadTool';
import LoadToolVersionSettings from '../../../../models/tools/LoadToolVersionSettings';
import UpdateToolVersionSettings from '../../../../models/tools/UpdateToolVersionSettings';
import {
  Alert,
  message
} from 'antd';
import LoadingView from '../../../special/LoadingView';
import roleModel from '../../../../utils/roleModel';
import EditToolForm from '../../forms/EditToolForm';
import LoadToolAttributes from '../../../../models/tools/LoadToolInfo';
import HiddenObjects from '../../../../utils/hidden-objects';

@HiddenObjects.injectToolsFilters
@inject('preferences', 'dockerRegistries')
@inject((stores, {params}) => {
  return {
    toolId: params.id,
    version: params.version,
    tool: new LoadTool(params.id),
    docker: stores.dockerRegistries,
    settings: new LoadToolVersionSettings(params.id, params.version),
    preferences: stores.preferences,
    versions: new LoadToolAttributes(params.id)
  };
})
@observer
export default class ToolSetttings extends React.Component {
  state = {
    operationInProgress: false
  };

  versionSettingsForm;

  constructor (props) {
    super(props);
    makeObservable(this, {
      versionSettingsForm: observable,
      registries: computed,
      dockerImage: computed,
      toolVersionOS: computed,
      settings: computed,
      allowCommit: computed,
      platform: computed
    });
  }

  get registries () {
    if (this.props.docker.loaded) {
      return this.props.hiddenToolsTreeFilter(this.props.docker.value)
        .registries;
    }
    return [];
  }

  get dockerImage () {
    const {tool, version} = this.props;
    if (!tool?.loaded) {
      return;
    }
    const {image} = tool.value;
    const registry = this.registries.find(r => r.id === this.props.tool.value.registryId);
    return registry
      ? `${registry.path}/${image}${version ? `:${version}` : ''}`
      : `${image}${version ? `:${version}` : ''}`;
  }

  get toolVersionOS () {
    const {versions, version} = this.props;
    if (versions.loaded) {
      const {value = {}} = versions;
      const {versions: versionsInfo = []} = value;
      const versionInfo = versionsInfo
        .find(o => o.version === version);
      if (
        versionInfo &&
        versionInfo.scanResult.toolOSVersion &&
        versionInfo.scanResult.toolOSVersion.distribution
      ) {
        const {
          distribution,
          version: distributionVersion = ''
        } = versionInfo.scanResult.toolOSVersion;
        return [
          distribution,
          distributionVersion
        ]
          .filter(Boolean)
          .join(' ');
      }
    }
    return undefined;
  }

  operationWrapper = (fn) => (...opts) => {
    this.setState({
      operationInProgress: true
    }, async () => {
      await fn(...opts);
      this.setState({
        operationInProgress: false
      });
    });
  };

  get settings () {
    if (this.props.settings.loaded) {
      if ((this.props.settings.value || []).length > 0 &&
          this.props.settings.value[0].settings &&
        this.props.settings.value[0].settings.length &&
        this.props.settings.value[0].settings[0].configuration) {
        return this.props.settings.value[0].settings[0].configuration;
      }
      return {parameters: {}};
    }
    return null;
  }

  get allowCommit () {
    const {settings} = this.props;
    if (settings.loaded) {
      if ((settings.value || []).length > 0) {
        return settings.value[0].allowCommit;
      }
      return true;
    }
    return false;
  }

  get platform () {
    if (this.props.settings.loaded) {
      if ((this.props.settings.value || []).length > 0) {
        return this.props.settings.value[0].platform;
      }
    }
    return undefined;
  }

  updateTool = async (tool, configuration, allowCommit) => {
    const hide = message.loading('Updating version settings...', 0);
    const updateRequest = new UpdateToolVersionSettings(
      this.props.toolId,
      this.props.version,
      allowCommit
    );
    await updateRequest.send([{
      configuration,
      name: 'default',
      default: true
    }]);
    if (updateRequest.error) {
      hide();
      message.error(updateRequest.error);
    } else {
      await this.props.settings.fetch();
      this.versionSettingsForm && this.versionSettingsForm.reset();
      hide();
    }
  };

  render () {
    if ((!this.props.settings.loaded && this.props.settings.pending) ||
      (!this.props.tool.loaded && this.props.tool.pending) ||
      (!this.props.preferences.loaded && this.props.preferences.pending)) {
      return <LoadingView />;
    }
    if (this.props.settings.error) {
      return <Alert type="error" title={this.props.settings.error} />;
    }
    if (this.props.tool.error) {
      return <Alert type="error" title={this.props.tool.error} />;
    }
    if (this.props.preferences.error) {
      return <Alert type="error" title={this.props.preferences.error} />;
    }
    if (!roleModel.readAllowed(this.props.tool.value)) {
      return (
        <Alert type="error" title="You have no permissions to view tool details" />
      );
    }
    return (
      <EditToolForm
        mode="version"
        allowSensitive={this.props.tool.value.allowSensitive}
        allowCommitVersion={this.allowCommit}
        toolId={this.props.toolId}
        toolVersion={this.props.version}
        onInitialized={form => { this.versionSettingsForm = form; }}
        readOnly={
          this.state.operationInProgress ||
          !roleModel.writeAllowed(this.props.tool.value) ||
          !!this.props.tool.value.link
        }
        defaultPriceTypeIsSpot={this.props.preferences.useSpot}
        configuration={this.settings}
        platform={this.platform}
        onSubmit={this.operationWrapper(this.updateTool)}
        dockerOSVersion={this.toolVersionOS}
        dockerImage={this.dockerImage}
      />
    );
  }
}
