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
import classNames from 'classnames';
import connect from '../../../../utils/connect';
import {computed, observable} from 'mobx';
import {Row, Tabs, Modal, Button, Alert, Icon, message} from 'antd';
import {names} from '../../../../models/utils/ContextualPreference';
import pipelines from '../../../../models/pipelines/Pipelines';
import AllowedInstanceTypes from '../../../../models/utils/AllowedInstanceTypes';
import PipelineConfigurationUpdate from '../../../../models/pipelines/PipelineConfigurationUpdate';
import PipelineConfigurationRename from '../../../../models/pipelines/PipelineConfigurationRename';
import PipelineConfigurationDelete from '../../../../models/pipelines/PipelineConfigurationDelete';
import preferences from '../../../../models/preferences/PreferencesLoad';
import LoadingView from '../../../special/LoadingView';
import LaunchPipelineForm from '../../launch/form/LaunchPipelineForm';
import roleModel from '../../../../utils/roleModel';
import CreateConfigurationForm from './forms/CreateConfigurationForm';
import styles from './PipelineConfiguration.css';

@connect({
  pipelines, preferences
})
@inject(({history, pipelines, routing, preferences}, {onReloadTree, params}) => {
  return {
    history,
    onReloadTree,
    currentConfiguration: params.configuration,
    pipeline: pipelines.getPipeline(params.id),
    pipelineVersions: pipelines.versionsForPipeline(params.id),
    pipelines,
    pipelineId: params.id,
    version: params.version,
    routing,
    configurations: pipelines.getConfiguration(params.id, params.version),
    preferences
  };
})
@observer
export default class PipelineConfiguration extends React.Component {
  @observable allowedInstanceTypes;

  @observable configurationModified;

  navigationBlockedListener;
  navigationBlocker;
  allowedNavigation;

  state = {
    createConfigurationForm: false,
    configurationsListCollapsed: false,
    pending: false
  };

  componentDidMount () {
    this.navigationBlockedListener = this.props.history.listenBefore((location, callback) => {
      const locationBefore = this.props.routing.location.pathname;
      if (location.pathname === locationBefore) {
        callback();
        return;
      }
      const clearBlocker = () => {
        setTimeout(() => {
          this.navigationBlocker = null;
        }, 0);
      };
      if (
        !this.isBitBucket &&
        this.configurationModified &&
        !this.navigationBlocker &&
        location.pathname !== this.allowedNavigation
      ) {
        const cancel = () => {
          if (this.props.history.getCurrentLocation().pathname !== locationBefore) {
            this.props.history.replace(locationBefore);
          }
          clearBlocker();
        };
        this.navigationBlocker = Modal.confirm({
          title: 'You have unsaved changes. Continue?',
          style: {
            wordWrap: 'break-word'
          },
          onOk () {
            callback();
            clearBlocker();
          },
          onCancel () {
            cancel();
          },
          okText: 'Yes',
          cancelText: 'No'
        });
      } else {
        callback();
      }
    });
    const parameters = this.getParameters();
    if (!this.allowedInstanceTypes) {
      this.allowedInstanceTypes = new AllowedInstanceTypes();
    }
    if (this.allowedInstanceTypes && parameters) {
      this.allowedInstanceTypes.setParameters({
        isSpot: parameters.is_spot,
        regionId: parameters.cloudRegionId
      });
    }
  }

  componentWillUnmount () {
    if (this.navigationBlockedListener) {
      this.navigationBlockedListener();
    }
  }

  @computed
  get selectedConfiguration () {
    if (this.props.configurations.loaded) {
      const [configuration] = this.props.configurations.value.filter(c => c.name === this.selectedConfigurationName);
      return configuration;
    }
    return null;
  }

  @computed
  get selectedConfigurationName () {
    if (this.props.currentConfiguration) {
      return this.props.currentConfiguration;
    }
    if (this.props.configurations.loaded && this.props.configurations.value.length > 0) {
      const [configuration] = this.props.configurations.value.filter(c => c.default);
      if (configuration) {
        return configuration.name;
      } else {
        return this.props.configurations.value[0].name;
      }
    }
    return null;
  }

  @computed
  get selectedConfigurationIsDefault () {
    if (!this.props.currentConfiguration) {
      return true;
    }
    if (this.props.configurations.loaded && this.props.configurations.value.length > 0) {
      const [configuration] = this.props.configurations.value
        .filter(c => c.name.toLowerCase() === this.props.currentConfiguration.toLowerCase());
      if (configuration) {
        return configuration.default;
      }
    }
    return false;
  }

  @computed
  get defaultConfigurationName () {
    if (this.props.configurations.loaded &&
      this.props.configurations.value.length > 0) {
      const [configuration] = this.props.configurations.value
        .filter(c => c.default);
      if (configuration) {
        return configuration.name;
      }
    }
    return undefined;
  }

  @computed
  get isBitBucket () {
    const {pipeline} = this.props;
    if (!pipeline || !pipeline.loaded) {
      return false;
    }
    const {repositoryType} = pipeline.value || {};
    return /^bitbucket$/i.test(repositoryType);
  }

  @computed
  get canModifySources () {
    if (this.props.pipeline.pending || this.isBitBucket) {
      return false;
    }
    return roleModel.writeAllowed(this.props.pipeline.value) &&
      this.props.version === this.props.pipeline.value.currentVersion.name;
  };

  onConfigurationModified = (modified) => {
    this.configurationModified = modified;
  };

  onSelectConfiguration = (key) => {
    this.props.router.push(`/${this.props.pipelineId}/${this.props.version}/configuration/${key}`);
  };

  onSetAsDefaultClicked = () => {
    if (this.selectedConfigurationName &&
      this.props.configurations.loaded &&
      this.props.configurations.value.length > 0) {
      const [configuration] = this.props.configurations.value
        .filter(c => c.name.toLowerCase() === this.selectedConfigurationName.toLowerCase());
      if (configuration) {
        const hide = message.loading(`Setting '${this.selectedConfigurationName}' configuration as default...`, 0);
        this.setState({
          pending: true
        }, async () => {
          const request = new PipelineConfigurationUpdate(this.props.pipelineId);
          const payload = configuration;
          payload.default = true;
          await request.send(payload);
          if (request.error) {
            hide();
            message.error(request.error, 5);
            this.setState({
              pending: false
            });
          } else {
            await this.props.pipeline.fetch();
            await this.props.pipelineVersions.fetch();
            if (this.props.onReloadTree) {
              await this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
            }
            hide();
            this.navigateToNewVersion();
            this.setState({
              pending: false
            });
          }
        });
      }
    }
  };

  navigateToNewVersion = (configuration) => {
    let url;
    if (configuration) {
      url = `/${this.props.pipelineId}/${this.props.pipeline.value.currentVersion.name}/configuration/${configuration}`;
    } else {
      url = `/${this.props.pipelineId}/${this.props.pipeline.value.currentVersion.name}/configuration`;
    }
    this.allowedNavigation = url;
    this.props.router.push(url);
  };

  onRemoveConfigurationClicked = (configuration) => (e) => {
    if (e) {
      e.stopPropagation();
    }
    const removeConfiguration = async () => {
      const hide = message.loading(`Removing '${configuration.name}' configuration ...`, 0);
      const request = new PipelineConfigurationDelete(this.props.pipelineId, configuration.name);
      await request.fetch();
      if (request.error) {
        hide();
        message.error(request.error, 5);
      } else {
        await this.props.pipeline.fetch();
        await this.props.pipelineVersions.fetch();
        if (this.props.onReloadTree) {
          await this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
        }
        hide();
        this.navigateToNewVersion();
      }
    };
    Modal.confirm({
      title: `Are you sure you want to remove configuration '${configuration.name}'?`,
      style: {
        wordWrap: 'break-word'
      },
      async onOk () {
        await removeConfiguration();
      },
      okText: 'Yes',
      cancelText: 'No'
    });
  };

  openCreateConfigurationFormDialog = () => {
    this.setState({createConfigurationForm: true});
  };

  closeCreateConfigurationFormDialog = () => {
    this.setState({createConfigurationForm: false});
  };

  createConfigurationForm = async ({name, description, template}) => {
    if (this.props.configurations.value.filter(c => c.name === name).length > 0) {
      message.error(`Configuration '${name}' already exists`, 5);
      return;
    }
    const [configuration] = this.props.configurations.value
      .filter(c => c.name.toLowerCase() === template.toLowerCase()).map(c => c.configuration);
    if (configuration) {
      const hide = message.loading(`Creating '${name}' configuration ...`, 0);
      const request = new PipelineConfigurationUpdate(this.props.pipelineId);
      const payload = {
        name: name,
        default: false,
        description: description,
        configuration: configuration
      };
      await request.send(payload);
      if (request.error) {
        hide();
        message.error(request.error, 5);
      } else {
        await this.props.pipeline.fetch();
        await this.props.pipelineVersions.fetch();
        if (this.props.onReloadTree) {
          await this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
        }
        hide();
        this.closeCreateConfigurationFormDialog();
        this.navigateToNewVersion(name);
      }
    } else {
      message.error(`Cannot create configuration: template '${template}' is empty`, 5);
    }
  };

  getConfigurations = () => {
    if (this.props.configurations.loaded) {
      return (this.props.configurations.value || []).map(c => c);
    }
    return [];
  };

  getParameters = () => {
    if (!this.props.configurations.loaded) {
      return undefined;
    }
    let configuration;
    if (this.selectedConfigurationName) {
      [configuration] = (this.props.configurations.value || []).filter(c => {
        return c.name.toLowerCase() === this.selectedConfigurationName.toLowerCase();
      });
    }
    if (!configuration) {
      [configuration] = (this.props.configurations.value || []).filter(c => c.default);
    }
    if (!configuration) {
      return {parameters: {}};
    }
    return configuration.configuration || {parameters: {}};
  };

  onSaveConfiguration = (opts) => {
    if (this.selectedConfigurationName &&
      this.props.configurations.loaded &&
      this.props.configurations.value.length > 0) {
      if (this.props.configurations.value
          .filter(c => c.name.toLowerCase() !== this.selectedConfigurationName.toLowerCase() &&
          c.name === opts.configuration.name).length > 0) {
        message.error(`Configuration ${opts.configuration.name} already exists`, 5);
        return false;
      }
      const [configuration] = this.props.configurations.value
        .filter(c => c.name.toLowerCase() === this.selectedConfigurationName.toLowerCase());
      if (configuration) {
        const hide = message.loading(`Updating '${this.selectedConfigurationName}' configuration ...`, 0);
        this.setState({
          pending: true
        }, async () => {
          if (this.selectedConfigurationName !== opts.configuration.name) {
            const renameRequest = new PipelineConfigurationRename(this.props.pipelineId, this.selectedConfigurationName, opts.configuration.name);
            await renameRequest.send({});
            if (renameRequest.error) {
              message.error(renameRequest.error);
              this.setState({
                pending: false
              });
              return;
            }
          }
          const request = new PipelineConfigurationUpdate(this.props.pipelineId);
          const mainFile = configuration.configuration.main_file;
          const mainClass = configuration.configuration.main_class;
          const language = configuration.configuration.language;
          const payload = {
            name: opts.configuration.name,
            default: configuration.default,
            description: configuration.description,
            configuration: opts
          };
          payload.configuration.configuration = undefined;
          payload.configuration.main_file = mainFile;
          payload.configuration.main_class = mainClass;
          payload.configuration.language = language;
          await request.send(payload);
          if (request.error) {
            hide();
            message.error(request.error, 5);
            this.setState({
              pending: false
            });
          } else {
            await this.props.pipeline.fetch();
            await this.props.pipelineVersions.fetch();
            if (this.props.onReloadTree) {
              await this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
            }
            hide();
            this.navigateToNewVersion(payload.name);
            this.setState({
              pending: false
            });
          }
        });
      }
    }
    return false;
  };

  renderTabs = () => {
    let addButton;
    if (this.canModifySources) {
      addButton = (
        <Button
          disabled={this.state.pending}
          id="add-configuration-button"
          size="small"
          onClick={this.openCreateConfigurationFormDialog}>
          <Icon type="plus" /> ADD
        </Button>
      );
    }
    return (
      <Row>
        <Tabs
          className={classNames(styles.tabs, 'cp-tabs-no-content')}
          hideAdd
          onChange={this.onSelectConfiguration}
          activeKey={this.selectedConfigurationName}
          tabBarExtraContent={addButton}
          type="editable-card">
          {
            (this.props.configurations.value || []).sort((cA, cB) => {
              if (cA.name > cB.name) {
                return 1;
              } else if (cA.name < cB.name) {
                return -1;
              }
              return 0;
            }).map(c => (
              <Tabs.TabPane
                closable={false}
                tab={c.default ? <i>{c.name}</i> : c.name}
                key={c.name}
              />
            ))
          }
        </Tabs>
      </Row>
    );
  };

  componentDidUpdate () {
    const parameters = this.getParameters();
    if (!this.allowedInstanceTypes) {
      this.allowedInstanceTypes = new AllowedInstanceTypes();
    }
    if (this.allowedInstanceTypes && parameters) {
      this.allowedInstanceTypes.setParameters({
        isSpot: parameters.is_spot,
        regionId: parameters.cloudRegionId
      });
    }
  }

  render () {
    if (
      (!this.props.configurations.loaded && this.props.configurations.pending) ||
      !this.allowedInstanceTypes
    ) {
      return <LoadingView />;
    }
    if (this.props.configurations.error) {
      return <Alert type="error" message={this.props.configurations.error} />;
    }
    if (!this.getParameters()) {
      return <Alert type="error" message="Error loading configurations" />;
    }
    let defaultPriceTypeIsSpot = false;
    if (this.props.preferences.loaded) {
      defaultPriceTypeIsSpot = this.props.preferences.useSpot;
    }

    return (
      <div style={{display: 'flex', flex: 1, flexDirection: 'column', height: '100%'}}>
        {this.renderTabs()}
        <Row
          className="cp-tabs-content"
          style={{flex: 1, overflowY: 'auto', height: '100%'}}
        >
          <LaunchPipelineForm
            defaultPriceTypeIsLoading={this.props.preferences.pending}
            defaultPriceTypeIsSpot={defaultPriceTypeIsSpot}
            readOnly={!this.canModifySources || this.state.pending}
            canExecute={false}
            canRemove={!this.state.pending && this.canModifySources && this.props.configurations.value.length > 1}
            onRemoveConfiguration={this.onRemoveConfigurationClicked(this.selectedConfiguration)}
            editConfigurationMode={true}
            currentConfigurationName={this.selectedConfigurationName}
            currentConfigurationIsDefault={this.selectedConfigurationIsDefault}
            onSetConfigurationAsDefault={this.onSetAsDefaultClicked}
            pipeline={this.props.pipeline ? this.props.pipeline.value : undefined}
            allowedInstanceTypes={this.allowedInstanceTypes}
            toolInstanceTypes={names.allowedInstanceTypes}
            version={this.props.version}
            parameters={this.getParameters()}
            configurations={this.getConfigurations()}
            onLaunch={this.onSaveConfiguration}
            isDetachedConfiguration={false}
            onModified={this.onConfigurationModified}
          />
        </Row>
        <CreateConfigurationForm
          pending={false}
          configurations={(this.props.configurations.value || []).map(c => c)}
          visible={this.state.createConfigurationForm}
          defaultTemplate={this.defaultConfigurationName}
          onSubmit={this.createConfigurationForm}
          onCancel={this.closeCreateConfigurationFormDialog} />
      </div>
    );
  }

}
