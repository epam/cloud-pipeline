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
import {computed, observable} from 'mobx';
import {Row, Col, Modal, Button, Alert, Icon, Tabs, message} from 'antd';
import LaunchPipelineForm from '../launch/form/LaunchPipelineForm';
import pipelines from '../../../models/pipelines/Pipelines';
import pipelinesLibrary from '../../../models/folders/FolderLoadTree';
import folders from '../../../models/folders/Folders';
import AllowedInstanceTypes from '../../../models/utils/AllowedInstanceTypes';
import configurations from '../../../models/configuration/Configurations';
import PipelineConfigurations from '../../../models/pipelines/PipelineConfigurations';
import ConfigurationUpdate from '../../../models/configuration/ConfigurationUpdate';
import ConfigurationDelete from '../../../models/configuration/ConfigurationDelete';
import preferences from '../../../models/preferences/PreferencesLoad';
import ConfigurationRun from '../../../models/configuration/ConfigurationRun';
import CreateConfigurationForm from '../../pipelines/version/configuration/forms/CreateConfigurationForm';
import EditDetachedConfigurationForm from './forms/EditDetachedConfigurationForm';
import LoadingView from '../../special/LoadingView';
import SessionStorageWrapper from '../../special/SessionStorageWrapper';
import Breadcrumbs from '../../special/Breadcrumbs';
import roleModel from '../../../utils/roleModel';
import localization from '../../../utils/localization';
import connect from '../../../utils/connect';
import styles from './DetachedConfiguration.css';
import Schedule from './schedule';
import browserStyles from '../browser/Browser.css';
import {ItemTypes} from '../model/treeStructureFunctions';

const DTS_ENVIRONMENT = 'DTS';

@connect({
  configurations,
  pipelines,
  folders,
  pipelinesLibrary,
  preferences
})
@localization.localizedComponent
@inject(({configurations, folders, pipelinesLibrary, preferences, history, routing}, {onReloadTree, params}) => {
  return {
    history,
    routing,
    onReloadTree,
    configurations: configurations.getConfiguration(params.id),
    pipelines,
    folders,
    pipelinesLibrary,
    configurationId: params.id,
    currentConfiguration: params.name,
    configurationsCache: configurations,
    preferences
  };
})
@observer
export default class DetachedConfiguration extends localization.LocalizedReactComponent {
  @observable allowedInstanceTypes;

  @observable configurationModified;

  navigationBlockedListener;
  navigationBlocker;
  allowedNavigation;

  state = {
    configurationsListCollapsed: false,
    createConfigurationForm: false,
    editConfigurationFormVisible: false,
    overriddenConfiguration: null,
    emptyPipeline: false,
    selectedPipelineParameters: {},
    selectedPipelineParametersIsLoading: true
  };

  @computed
  get selectedConfiguration () {
    if (this.props.configurations.loaded &&
      this.props.configurations.value.entries) {
      const [configuration] = this.props.configurations.value.entries.filter(c => c.name === this.selectedConfigurationName);
      return configuration;
    }
    return null;
  }

  @computed
  get selectedConfigurationName () {
    if (this.props.currentConfiguration) {
      return this.props.currentConfiguration;
    }
    if (this.props.configurations.loaded &&
      this.props.configurations.value.entries.length > 0) {
      const [configuration] = this.props.configurations.value.entries.filter(c => c.default);
      if (configuration) {
        return configuration.name;
      } else {
        return this.props.configurations.value.entries[0].name;
      }
    }
    return null;
  }

  @computed
  get selectedConfigurationIsDefault () {
    if (!this.props.currentConfiguration) {
      return true;
    }
    if (this.props.configurations.loaded && this.props.configurations.value.entries.length > 0) {
      const [configuration] = this.props.configurations.value.entries
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
      this.props.configurations.value.entries.length > 0) {
      const [configuration] = this.props.configurations.value.entries
        .filter(c => c.default);
      if (configuration) {
        return configuration.name;
      }
    }
    return undefined;
  }

  get canModifySources () {
    if (!this.props.configurations.loaded) {
      return false;
    }
    return roleModel.writeAllowed(this.props.configurations.value);
  };

  get canExecute () {
    if (!this.props.configurations.loaded) {
      return false;
    }
    return roleModel.executeAllowed(this.props.configurations.value);
  };

  onSelectConfiguration = (key) => {
    if (key !== this.props.currentConfiguration) {
      this.setState({
        overriddenConfiguration: null,
        emptyPipeline: false
      }, () => {
        this.props.router.push(`/configuration/${this.props.configurationId}/${key}`);
      });
    }
  };

  onRemoveConfigurationClicked = (configuration) => (e) => {
    if (e) {
      e.stopPropagation();
    }
    const removeConfiguration = async () => {
      const entries = this.props.configurations.value.entries.map(e => e);
      const [removableConfiguration] = entries
        .filter(c => c.name.toLowerCase() === configuration.name.toLowerCase());
      const index = entries.indexOf(removableConfiguration);
      if (index >= 0) {
        entries.splice(index, 1);
        const hide = message.loading(`Removing '${configuration.name}' configuration ...`, 0);
        const request = new ConfigurationUpdate();
        const payload = {
          id: this.props.configurations.value.id,
          name: this.props.configurations.value.name,
          description: this.props.configurations.value.description,
          parentId: this.props.configurations.value.parent ? this.props.configurations.value.parent.id : undefined,
          entries
        };
        await request.send(payload);
        if (request.error) {
          hide();
          message.error(request.error, 5);
        } else {
          await this.props.configurations.fetch();
          this.props.configurationsCache.invalidateConfigurationCache(this.props.configurationId);
          hide();
          this.props.router.push(`/configuration/${this.props.configurationId}/${entries[0].name}`);
        }
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
    const entries = this.props.configurations.value.entries;
    if (entries.filter(c => c.name === name).length > 0) {
      message.error(`Configuration '${name}' already exists`, 5);
      return;
    }
    let newConfiguration;
    const [configuration] = entries
      .filter(c => template && c.name.toLowerCase() === template.toLowerCase());
    if (configuration) {
      newConfiguration = Object.assign({}, configuration);
    } else {
      newConfiguration = {
        configuration: {
          cmd_template: 'sleep 100'
        }
      };
    }
    newConfiguration.name = name;
    newConfiguration.description = description;
    newConfiguration.default = false;
    entries.push(newConfiguration);
    const hide = message.loading(`Creating '${name}' configuration ...`, 0);
    const request = new ConfigurationUpdate();
    const payload = {
      id: this.props.configurations.value.id,
      name: this.props.configurations.value.name,
      description: this.props.configurations.value.description,
      parentId: this.props.configurations.value.parent ? this.props.configurations.value.parent.id : undefined,
      entries
    };
    await request.send(payload);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      this.closeCreateConfigurationFormDialog();
      await this.props.configurations.fetch();
      this.props.configurationsCache.invalidateConfigurationCache(this.props.configurationId);
      hide();
      this.props.router.push(`/configuration/${this.props.configurationId}/${name}`);
    }
  };

  onSetAsDefault = async () => {
    if (this.selectedConfigurationName &&
      this.props.configurations.loaded &&
      this.props.configurations.value.entries.length > 0) {
      const entries = this.props.configurations.value.entries;
      const [configuration] = entries
        .filter(c => c.name.toLowerCase() === this.selectedConfigurationName.toLowerCase());
      if (configuration) {
        entries.forEach(c => {
          c.default = false;
        });
        configuration.default = true;
        const hide = message.loading(`Updating '${this.selectedConfigurationName}' configuration ...`, 0);
        const request = new ConfigurationUpdate();
        const payload = {
          id: this.props.configurations.value.id,
          name: this.props.configurations.value.name,
          description: this.props.configurations.value.description,
          parentId: this.props.configurations.value.parent ? this.props.configurations.value.parent.id : undefined,
          entries
        };
        await request.send(payload);
        if (request.error) {
          hide();
          message.error(request.error, 5);
        } else {
          await this.props.configurations.fetch();
          this.props.configurationsCache.invalidateConfigurationCache(this.props.configurationId);
          hide();
        }
      }
    }
  };

  onConfigurationModified = (modified) => {
    this.configurationModified = modified;
  };

  onSaveConfiguration = async (opts) => {
    if (this.selectedConfigurationName &&
      this.props.configurations.loaded &&
      this.props.configurations.value.entries.length > 0) {
      const entries = this.props.configurations.value.entries;
      if (entries
          .filter(c => c.name.toLowerCase() !== this.selectedConfigurationName.toLowerCase() &&
          c.name === opts.configuration.name).length > 0) {
        message.error(`Configuration ${opts.configuration.name} already exists`, 5);
        return false;
      }
      const [configuration] = entries
        .filter(c => c.name.toLowerCase() === this.selectedConfigurationName.toLowerCase());
      if (configuration) {
        const hide = message.loading(`Updating '${this.selectedConfigurationName}' configuration ...`, 0);
        if (this.selectedConfigurationName !== opts.configuration.name) {
          configuration.name = opts.configuration.name;
        }
        if (opts.pipelineId && opts.pipelineVersion) {
          configuration.pipelineId = opts.pipelineId;
          configuration.pipelineVersion = opts.pipelineVersion;
          configuration.configName = opts.configName;
          configuration.methodName = null;
          configuration.methodSnapshot = null;
          configuration.methodConfigurationName = null;
          configuration.methodConfigurationSnapshot = null;
          configuration.methodInputs = null;
          configuration.methodOutputs = null;
        } else if (opts.methodName && opts.methodSnapshot) {
          configuration.methodName = opts.methodName;
          configuration.methodSnapshot = opts.methodSnapshot;
          configuration.methodConfigurationName = opts.methodConfigurationName;
          configuration.methodConfigurationSnapshot = opts.methodConfigurationSnapshot;
          configuration.methodInputs = opts.methodInputs;
          configuration.methodOutputs = opts.methodOutputs;
          configuration.pipelineId = null;
          configuration.pipelineVersion = null;
          configuration.configName = null;
        } else {
          configuration.pipelineId = null;
          configuration.pipelineVersion = null;
          configuration.configName = null;
          configuration.methodName = null;
          configuration.methodSnapshot = null;
          configuration.methodConfigurationName = null;
          configuration.methodConfigurationSnapshot = null;
          configuration.methodInputs = null;
          configuration.methodOutputs = null;
        }
        configuration.executionEnvironment = opts.executionEnvironment;
        configuration.rootEntityId = opts.rootEntityId;
        opts.pipelineId = undefined;
        opts.pipelineVersion = undefined;
        opts.configName = undefined;
        opts.configuration = undefined;
        opts.rootEntityId = undefined;
        opts.methodName = undefined;
        opts.methodSnapshot = undefined;
        opts.methodConfigurationName = undefined;
        opts.methodConfigurationSnapshot = undefined;
        opts.methodInputs = undefined;
        opts.methodOutputs = undefined;
        if (opts.executionEnvironment) {
          opts.executionEnvironment = undefined;
        }
        if (configuration.executionEnvironment === DTS_ENVIRONMENT) {
          for (const key in opts) {
            if (opts.hasOwnProperty(key) && opts[key] !== undefined) {
              configuration[key] = opts[key];
            }
          }
        } else {
          configuration.configuration = opts;
        }
        const request = new ConfigurationUpdate();
        const payload = {
          id: this.props.configurations.value.id,
          name: this.props.configurations.value.name,
          description: this.props.configurations.value.description,
          parentId: this.props.configurations.value.parent ? this.props.configurations.value.parent.id : undefined,
          entries
        };
        await request.send(payload);
        if (request.error) {
          hide();
          message.error(request.error, 5);
          return false;
        } else {
          await this.props.configurations.fetch();
          this.props.configurationsCache.invalidateConfigurationCache(this.props.configurationId);
          hide();
          this.setState({
            overriddenConfiguration: null
          }, () => {
            if (this.selectedConfigurationName !== configuration.name) {
              this.allowedNavigation =
                `/configuration/${this.props.configurationId}/${configuration.name}`;
              this.props.router.push(this.allowedNavigation);
            }
          });
          return true;
        }
      }
    }
    return false;
  };

  @computed
  get selectedFireCloudMethod () {
    if (this.selectedConfiguration) {
      const configuration = this.selectedConfiguration;
      if (configuration &&
        configuration.methodName &&
        configuration.methodSnapshot) {
        const nameParts = configuration.methodName.split('/');
        const configurationNameParts = configuration.methodConfigurationName
          ? configuration.methodConfigurationName.split('/') : [];
        return {
          namespace: nameParts[0],
          name: nameParts[1],
          snapshot: configuration.methodSnapshot,
          configuration: configurationNameParts.pop(),
          configurationSnapshot: configuration.methodConfigurationSnapshot,
          methodInputs: (configuration.methodInputs || []).map(i => i),
          methodOutputs: (configuration.methodOutputs || []).map(o => o)
        };
      }
    }
    return undefined;
  }

  getPipelines = () => {
    if (this.props.pipelines.pending || this.props.pipelines.error || !this.props.pipelines.value) {
      return [];
    }
    return this.props.pipelines.value.map(p => p);
  };

  getConfigurations = () => {
    if (!!this.props.configurations && this.props.configurations.loaded) {
      return (this.props.configurations.value.entries || []).map(c => c);
    }
    return [];
  };

  getParameters = () => {
    if (this.state.overriddenConfiguration) {
      const parameters = this.state.overriddenConfiguration.configuration
        ? this.state.overriddenConfiguration.configuration.parameters
        : this.state.overriddenConfiguration.parameters;
      for (let parameterName in parameters) {
        const currentParam = parameters[parameterName];
        currentParam.readOnly = !!currentParam.value;
      }
      return {
        ...this.state.overriddenConfiguration.configuration || this.state.overriddenConfiguration,
        parameters
      };
    }
    if (!this.props.configurations || !this.props.configurations.loaded) {
      return undefined;
    }
    let configuration;
    if (this.selectedConfigurationName) {
      [configuration] = (this.props.configurations.value.entries || []).filter(c => {
        return c.name.toLowerCase() === this.selectedConfigurationName.toLowerCase();
      });
    }
    if (!configuration) {
      [configuration] = (this.props.configurations.value.entries || []).filter(c => c.default);
    }
    if (!configuration) {
      return {parameters: {}};
    }
    const parameters = configuration.configuration
      ? configuration.configuration.parameters
      : configuration.parameters;

    for (let parameter in parameters) {
      if (this.state.selectedPipelineParameters &&
        this.state.selectedPipelineParameters.hasOwnProperty(parameter)) {
        parameters[parameter].readOnly =
          !!this.state.selectedPipelineParameters[parameter].value;
      } else {
        parameters[parameter].readOnly = false;
      }
    }

    return {
      ...configuration.configuration || configuration,
      parameters
    };
  };

  getSelectedPipeline = () => {
    if (!this.props.configurations || !this.props.configurations.loaded) {
      return undefined;
    }
    if (this.state.emptyPipeline) {
      return undefined;
    }
    let configuration;
    if (this.selectedConfigurationName) {
      [configuration] = (this.props.configurations.value.entries || []).filter(c => {
        return c.name.toLowerCase() === this.selectedConfigurationName.toLowerCase();
      });
    }
    if (!configuration) {
      [configuration] = (this.props.configurations.value.entries || []).filter(c => c.default);
    }
    if (configuration && configuration.pipelineId && configuration.pipelineVersion) {
      const [pipeline] = this.getPipelines().filter(p => p.id === configuration.pipelineId);
      if (pipeline) {
        return pipeline;
      } else {
        return {
          id: configuration.pipelineId,
          name: `${this.localizedString('Pipeline')} #${configuration.pipelineId}`,
          unknown: true
        };
      }
    }
    return undefined;
  };

  getSelectedPipelineVersion = () => {
    const pipeline = this.getSelectedPipeline();
    if (!pipeline) {
      return undefined;
    }
    let configuration;
    if (this.selectedConfigurationName) {
      [configuration] = (this.props.configurations.value.entries || []).filter(c => {
        return c.name.toLowerCase() === this.selectedConfigurationName.toLowerCase();
      });
    }
    if (!configuration) {
      [configuration] = (this.props.configurations.value.entries || []).filter(c => c.default);
    }
    if (configuration && configuration.pipelineVersion) {
      return configuration.pipelineVersion;
    }
    return undefined;
  };

  getSelectedPipelineVersionConfiguration = () => {
    const pipeline = this.getSelectedPipeline();
    if (!pipeline) {
      return undefined;
    }
    let configuration;
    if (this.selectedConfigurationName) {
      [configuration] = (this.props.configurations.value.entries || []).filter(c => {
        return c.name.toLowerCase() === this.selectedConfigurationName.toLowerCase();
      });
    }
    if (!configuration) {
      [configuration] = (this.props.configurations.value.entries || []).filter(c => c.default);
    }
    if (configuration && configuration.configName) {
      return configuration.configName;
    }
    return undefined;
  };

  loadSelectedPipelineParameters = async () => {
    this.setState({selectedPipelineParametersIsLoading: true});

    await this.props.configurations.fetch();
    const selectedPipline = this.getSelectedPipeline();
    const selectedPiplineVersion = this.getSelectedPipelineVersion();
    const selectedPiplineVersionConfiguration = this.getSelectedPipelineVersionConfiguration();

    let selectedPipelineParameters = {};

    if (selectedPipline) {
      const request = new PipelineConfigurations(selectedPipline.id, selectedPiplineVersion);
      await request.fetch();
      if (!request.error) {
        const [config] = selectedPiplineVersionConfiguration
          ? request.value.map(c => c).filter(c => c.name === selectedPiplineVersionConfiguration)
          : request.value.map(c => c).filter(c => c.default);
        selectedPipelineParameters = config.configuration.parameters;
      }
    }
    this.setState({
      selectedPipelineParameters: selectedPipelineParameters,
      selectedPipelineParametersIsLoading: false
    });
  };

  runSelected = (opts, entitiesIds, metadataClass, expansionExpression, folderId) => {

    const launchFn = async () => {
      const entries = this.props.configurations.value.entries.map(e => e);
      const [configuration] = entries
        .filter(c => c.name.toLowerCase() === this.selectedConfigurationName.toLowerCase());
      if (configuration) {
        configuration.configName = opts.configName;
        if (opts.pipelineId && opts.pipelineVersion) {
          configuration.pipelineId = opts.pipelineId;
          configuration.pipelineVersion = opts.pipelineVersion;
          configuration.configName = opts.configName;
          configuration.methodName = null;
          configuration.methodSnapshot = null;
          configuration.methodConfigurationName = null;
          configuration.methodConfigurationSnapshot = null;
          configuration.methodInputs = null;
          configuration.methodOutputs = null;
        } else if (opts.methodName && opts.methodSnapshot) {
          configuration.methodName = opts.methodName;
          configuration.methodSnapshot = opts.methodSnapshot;
          configuration.methodConfigurationName = opts.methodConfigurationName;
          configuration.methodConfigurationSnapshot = opts.methodConfigurationSnapshot;
          configuration.methodInputs = opts.methodInputs;
          configuration.methodOutputs = opts.methodOutputs;
          configuration.pipelineId = null;
          configuration.pipelineVersion = null;
          configuration.configName = null;
        } else {
          configuration.pipelineId = null;
          configuration.pipelineVersion = null;
          configuration.configName = null;
          configuration.methodName = null;
          configuration.methodSnapshot = null;
          configuration.methodConfigurationName = null;
          configuration.methodConfigurationSnapshot = null;
          configuration.methodInputs = null;
          configuration.methodOutputs = null;
        }
        configuration.executionEnvironment = opts.executionEnvironment;
        configuration.rootEntityId = opts.rootEntityId;
        opts.pipelineId = undefined;
        opts.pipelineVersion = undefined;
        opts.configName = undefined;
        opts.configuration = undefined;
        opts.rootEntityId = undefined;
        opts.methodName = undefined;
        opts.methodSnapshot = undefined;
        opts.methodConfigurationName = undefined;
        opts.methodConfigurationSnapshot = undefined;
        opts.methodInputs = undefined;
        opts.methodOutputs = undefined;
        opts.executionEnvironment = undefined;
        if (configuration.executionEnvironment === DTS_ENVIRONMENT) {
          for (const key in opts) {
            if (opts.hasOwnProperty(key) && opts[key] !== undefined) {
              configuration[key] = opts[key];
            }
          }
        } else {
          configuration.configuration = opts;
        }
      }
      const hide = message.loading('Launching...', 0);
      const request = new ConfigurationRun(expansionExpression);
      await request.send({
        id: this.props.configurationId,
        entries: [configuration],
        entitiesIds: entitiesIds,
        metadataClass: metadataClass,
        folderId: folderId
      });
      hide();
      if (request.error) {
        message.error(request.error);
      } else {
        SessionStorageWrapper.navigateToActiveRuns(this.props.router);
      }
    };
    let title = `Launch ${this.selectedConfigurationName} configuration?`;
    if (this.configurationModified) {
      title = `You have unsaved changes. ${title}`;
    }
    Modal.confirm({
      title: title,
      style: {
        wordWrap: 'break-word'
      },
      onOk: () => {
        launchFn();
      }
    });
  };

  runCluster = (opts, entitiesIds, metadataClass, expansionExpression, folderId) => {
    const launchFn = async () => {
      const entries = this.props.configurations.value.entries.map(e => {
        if (e.name.toLowerCase() === this.selectedConfigurationName.toLowerCase()) {
          return e;
        }
        return {
          name: e.name
        };
      });
      const [configuration] = entries
        .filter(c => c.name.toLowerCase() === this.selectedConfigurationName.toLowerCase());
      if (configuration) {
        configuration.configName = opts.configName;
        if (opts.pipelineId && opts.pipelineVersion) {
          configuration.pipelineId = opts.pipelineId;
          configuration.pipelineVersion = opts.pipelineVersion;
          configuration.configName = opts.configName;
          configuration.methodName = null;
          configuration.methodSnapshot = null;
          configuration.methodConfigurationName = null;
          configuration.methodConfigurationSnapshot = null;
          configuration.methodInputs = null;
          configuration.methodOutputs = null;
        } else if (opts.methodName && opts.methodSnapshot) {
          configuration.methodName = opts.methodName;
          configuration.methodSnapshot = opts.methodSnapshot;
          configuration.methodConfigurationName = opts.methodConfigurationName;
          configuration.methodConfigurationSnapshot = opts.methodConfigurationSnapshot;
          configuration.methodInputs = opts.methodInputs;
          configuration.methodOutputs = opts.methodOutputs;
          configuration.pipelineId = null;
          configuration.pipelineVersion = null;
          configuration.configName = null;
        } else {
          configuration.pipelineId = null;
          configuration.pipelineVersion = null;
          configuration.configName = null;
          configuration.methodName = null;
          configuration.methodSnapshot = null;
          configuration.methodConfigurationName = null;
          configuration.methodConfigurationSnapshot = null;
          configuration.methodInputs = null;
          configuration.methodOutputs = null;
        }
        configuration.executionEnvironment = opts.executionEnvironment;
        configuration.rootEntityId =
          this.selectedConfigurationIsDefault ? opts.rootEntityId : undefined;
        opts.pipelineId = undefined;
        opts.pipelineVersion = undefined;
        opts.configName = undefined;
        opts.configuration = undefined;
        opts.rootEntityId = undefined;
        opts.methodName = undefined;
        opts.methodSnapshot = undefined;
        opts.methodConfigurationName = undefined;
        opts.methodConfigurationSnapshot = undefined;
        opts.methodInputs = undefined;
        opts.methodOutputs = undefined;
        opts.executionEnvironment = undefined;

        if (configuration.executionEnvironment === DTS_ENVIRONMENT) {
          for (const key in opts) {
            if (opts.hasOwnProperty(key) && opts[key] !== undefined) {
              configuration[key] = opts[key];
            }
          }
        } else {
          configuration.configuration = opts;
        }
      }
      const hide = message.loading('Launching...', 0);
      const request = new ConfigurationRun(expansionExpression);
      await request.send({
        id: this.props.configurationId,
        entries,
        entitiesIds: entitiesIds,
        metadataClass: metadataClass,
        folderId: folderId
      });
      hide();
      if (request.error) {
        message.error(request.error);
      } else {
        SessionStorageWrapper.navigateToActiveRuns(this.props.router);
      }
    };
    let title = 'Launch cluster?';
    if (this.configurationModified) {
      title = `You have unsaved changes. ${title}`;
    }
    Modal.confirm({
      title: title,
      onOk: () => {
        launchFn();
      }
    });
  };

  openEditConfigurationForm = () => {
    this.setState({editConfigurationFormVisible: true});
  };

  closeEditConfigurationForm = () => {
    this.setState({editConfigurationFormVisible: false});
  };

  editConfiguration = async (opts) => {
    const hide = message.loading(`Updating configuration '${this.props.configurations.value.name}'...`, 0);
    const request = new ConfigurationUpdate();
    const payload = {
      id: this.props.configurations.value.id,
      name: opts.name,
      description: opts.description,
      parentId: this.props.configurations.value.parent ? this.props.configurations.value.parent.id : undefined,
      entries: this.props.configurations.value.entries.map(e => e)
    };
    await request.send(payload);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await this.props.configurations.fetch();
      this.props.configurationsCache.invalidateConfigurationCache(this.props.configurationId);
      hide();
      this.closeEditConfigurationForm();
      this.props.router.push(`/configuration/${this.props.configurationId}/${this.selectedConfigurationName}`);
    }
  };

  renameConfiguration = async (name) => {
    const hide = message.loading(`Renaming configuration '${this.props.configurations.value.name}'...`, 0);
    const request = new ConfigurationUpdate();
    const payload = {
      id: this.props.configurations.value.id,
      name: name,
      description: this.props.configurations.value.description,
      parentId: this.props.configurations.value.parent ? this.props.configurations.value.parent.id : undefined,
      entries: this.props.configurations.value.entries.map(e => e)
    };
    await request.send(payload);
    if (request.error) {
      hide();
      message.error(request.error, 5);
      return false;
    } else {
      await this.props.configurations.fetch();
      this.props.configurationsCache.invalidateConfigurationCache(this.props.configurationId);
      hide();
      this.closeEditConfigurationForm();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!this.props.configurations.value.parent);
      }
      return true;
    }
  };

  deleteConfiguration = async () => {
    const hide = message.loading(`Deleting configuration '${this.props.configurations.value.name}'...`, 0);
    const request = new ConfigurationDelete(this.props.configurationId);
    await request.fetch();
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      hide();
      this.closeEditConfigurationForm();
      this.props.configurationsCache.invalidateConfigurationCache(this.props.configurationId);
      const parentFolderId = this.props.configurations.value.parent
        ? this.props.configurations.value.parent.id
        : undefined;
      if (parentFolderId) {
        this.props.folders.invalidateFolder(parentFolderId);
      } else {
        this.props.pipelinesLibrary.invalidateCache();
      }
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!parentFolderId);
      }
      if (parentFolderId) {
        this.props.router.push(`/folder/${parentFolderId}`);
      } else {
        this.props.router.push('/library');
      }
    }
  };

  onConfigurationSelectPipeline = async (opts, callback) => {
    if (opts && !opts.isFireCloud) {
      const {pipeline, version, configuration} = opts;
      const request = new PipelineConfigurations(pipeline.id, version);
      await request.fetch();
      if (!request.error) {
        const [config] = configuration
          ? request.value.map(c => c).filter(c => c.name === configuration)
          : request.value.map(c => c).filter(c => c.default);
        if (config) {
          this.setState({
            overriddenConfiguration: config,
            emptyPipeline: false
          }, () => callback());
        }
      }
    } else {
      this.setState({
        overriddenConfiguration: null,
        emptyPipeline: true
      }, () => callback());
    }
  };

  renderTabs = () => {
    let addButton;
    if (this.canModifySources) {
      addButton = (
        <Button
          id="add-configuration-button"
          size="small"
          onClick={this.openCreateConfigurationFormDialog}>
          <Icon type="plus" style={{lineHeight: 'inherit'}} /> ADD
        </Button>
      );
    }
    return (
      <Row>
        <Tabs
          className={styles.tabs}
          hideAdd={true}
          onChange={this.onSelectConfiguration}
          activeKey={this.selectedConfigurationName}
          tabBarExtraContent={addButton}
          type="editable-card">
          {
            (this.props.configurations.value.entries || [])
              .map(c => {
                return (
                  <Tabs.TabPane
                    closable={false}
                    tab={c.default ? <i>{c.name}</i> : c.name}
                    key={c.name} />
                );
              })
          }
        </Tabs>
      </Row>
    );
  };

  render () {
    if (
      (!this.props.configurations.loaded && this.props.configurations.pending) ||
      !this.allowedInstanceTypes
      ){
      return <LoadingView />;
    }
    if (this.props.configurations.error) {
      return <Alert type="error" message={this.props.configurations.error} />;
    }
    let defaultPriceTypeIsSpot = false;
    if (this.props.preferences.loaded) {
      defaultPriceTypeIsSpot = this.props.preferences.useSpot;
    }

    const configurationTitleClassName = this.props.configurations.value.locked ? browserStyles.readonly : undefined;
    return (
      <div className={styles.fullHeightContainer}>
        <Row
          type="flex"
          justify="space-between"
          align="middle"
          style={{marginBottom: 10, minHeight: 41}}>
          <Col className={browserStyles.itemHeader}>
            <Breadcrumbs
              id={parseInt(this.props.configurationId)}
              type={ItemTypes.configuration}
              textEditableField={this.props.configurations.value.name}
              onSaveEditableField={this.renameConfiguration}
              editStyleEditableField={{flex: 1}}
              readOnlyEditableField={!this.canModifySources}
              icon="setting"
              iconClassName={`${browserStyles.editableControl} ${configurationTitleClassName}`}
              lock={this.props.configurations.value.locked}
              lockClassName={`${browserStyles.editableControl} ${configurationTitleClassName}`}
              subject={this.props.configurations.value}
            />
          </Col>
          <Col className={styles.actionButtons}>
            <Schedule
              configurationId={this.props.configurationId}
            />
            <Button onClick={this.openEditConfigurationForm} size="small">
              <Icon type="setting" style={{lineHeight: 'inherit', verticalAlign: 'middle'}} />
            </Button>
          </Col>
        </Row>
        <Row style={{position: 'relative', display: 'flex', flexDirection: 'column', flex: 1}}>
          {this.renderTabs()}
          <Row style={{position: 'relative', flex: 1, overflowY: 'auto'}}>
            <LaunchPipelineForm
              defaultPriceTypeIsLoading={this.props.preferences.pending}
              defaultPriceTypeIsSpot={defaultPriceTypeIsSpot}
              ref={form => { this.form = form; }}
              readOnly={!this.canModifySources}
              canExecute={this.canExecute}
              canRunCluster={(this.props.configurations.value.entries || []).length > 1}
              canRemove={
                this.canModifySources &&
                this.props.configurations.value.entries &&
                this.props.configurations.value.entries.length > 1
              }
              onRemoveConfiguration={this.onRemoveConfigurationClicked(this.selectedConfiguration)}
              detached={true}
              editConfigurationMode={true}
              currentConfigurationName={this.selectedConfigurationName}
              currentConfigurationIsDefault={this.selectedConfigurationIsDefault}
              onSetConfigurationAsDefault={this.onSetAsDefault}
              runConfigurationCluster={this.runCluster}
              runConfiguration={this.runSelected}
              pipeline={this.getSelectedPipeline()}
              version={this.getSelectedPipelineVersion()}
              pipelineConfiguration={this.getSelectedPipelineVersionConfiguration()}
              pipelines={this.getPipelines()}
              allowedInstanceTypes={this.allowedInstanceTypes}
              parameters={this.getParameters()}
              configurations={this.getConfigurations()}
              onLaunch={this.onSaveConfiguration}
              onSelectPipeline={this.onConfigurationSelectPipeline}
              isDetachedConfiguration={true}
              configurationId={this.props.configurationId}
              selectedPipelineParametersIsLoading={this.state.selectedPipelineParametersIsLoading}
              fireCloudMethod={this.selectedFireCloudMethod}
              onModified={this.onConfigurationModified}
            />
          </Row>
          <CreateConfigurationForm
            pending={false}
            configurations={(this.props.configurations.value.entries || []).map(c => c)}
            visible={this.state.createConfigurationForm}
            defaultTemplate={this.defaultConfigurationName}
            onSubmit={this.createConfigurationForm}
            onCancel={this.closeCreateConfigurationFormDialog} />
          <EditDetachedConfigurationForm
            configuration={this.props.configurations.value}
            visible={this.state.editConfigurationFormVisible}
            onCancel={this.closeEditConfigurationForm}
            pending={false}
            onSubmit={this.editConfiguration}
            onDelete={this.deleteConfiguration} />
        </Row>
      </div>
    );
  }

  componentDidMount () {
    this.loadSelectedPipelineParameters();
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
      if (this.configurationModified && !this.navigationBlocker &&
        location.pathname !== this.allowedNavigation) {
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
  }

  componentWillUnmount () {
    if (this.navigationBlockedListener) {
      this.navigationBlockedListener();
    }
  }

  componentDidUpdate (prevProps) {
    if (prevProps.currentConfiguration !== this.props.currentConfiguration ||
      prevProps.configurationId !== this.props.configurationId) {
      this.loadSelectedPipelineParameters();
      this.setState({overriddenConfiguration: null});
    }
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
}
