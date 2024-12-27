/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observable, computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {
  DeletionPlugin,
  SelectionPlugin,
  SVGArrangePlugin,
  SVGEdgeHoverPlugin,
  SVGNodeMovePlugin,
  SVGPortDragPlugin,
  SVGValidatePlugin,
  ZoomPlugin,
  Workflow
} from 'cwl-svg';
import {
  WorkflowFactory,
  isType
} from 'cwlts/models';
import yaml from 'js-yaml';
import {
  Alert,
  Button,
  Icon,
  message
} from 'antd';
import moment from 'moment-timezone';
import classNames from 'classnames';
import Graph from '../Graph';
import VersionFile from '../../../../../../models/pipelines/VersionFile';
import LoadToolVersionSettings from '../../../../../../models/tools/LoadToolVersionSettings';
import LoadTool from '../../../../../../models/tools/LoadTool';
import LoadingView from '../../../../../special/LoadingView';
import CWLProperties from './components/properties';
import CWLCommandLineTool from './components/command-line-tool';
import CWLToolsRepository from './components/tools-repository';
import {base64toString} from '../../../../../../utils/base64';
import styles from '../Graph.css';
import 'cwl-svg/src/assets/styles/theme.scss';
import 'cwl-svg/src/plugins/selection/theme.scss';
import './cwl-styles.scss';

const readOnly = true;

@inject('dockerRegistries')
@inject(({pipelines}, params) => ({
  parameters: pipelines.getVersionParameters(params.pipelineId, params.version),
  pipeline: pipelines.getPipeline(params.pipelineId),
  pipelineId: params.pipelineId,
  pipelineVersion: params.version,
  pipelines
}))
@observer
export default class CwlGraph extends Graph {
  @observable _fileRequest;

  @observable model;
  @observable selected;

  componentDidMount () {
    this.loadFile();
  }

  componentDidUpdate () {
    this.loadFile();
    super.componentDidUpdate();
  }

  getFilePath () {
    if (this._fileRequest) {
      return this._fileRequest.path;
    }
    return null;
  }

  loadFile = () => {
    const {parameters, pipeline, version, pipelineId} = this.props;
    if (parameters.loaded && pipeline.loaded &&
      (!this._fileRequest || this._fileRequest.version !== version)) {
      let codePath = pipeline.value.codePath || '';
      if (codePath.startsWith('/')) {
        codePath = codePath.slice(1);
      }
      if (codePath.endsWith('/')) {
        codePath = codePath.slice(0, -1);
      }
      const filePathParts = parameters.value.main_file.split('.');
      if (filePathParts[filePathParts.length - 1].toLowerCase() === 'cwl') {
        this._fileRequest = new VersionFile(
          pipelineId,
          `${codePath}/${parameters.value.main_file}`,
          version
        );
        this._fileRequest.fetch();
      } else {
        this._fileRequest = new VersionFile(
          pipelineId,
          `${codePath}/${pipeline.value.name}.cwl`,
          version
        );
        this._fileRequest.fetch();
      }
    }
  }

  componentWillUnmount () {
    this._fileRequest = null;
  }

  @computed
  get commandLineTool () {
    if (!this.selected || !this.selected.run) {
      return undefined;
    }
    return this.selected.run;
  }

  initializeModel = (modelJson) => {
    if (!modelJson) {
      return {
        cwlVersion: 'v1.0'
      };
    }
    if (/^CommandLineTool$/i.test(modelJson.class)) {
      const workflowModel = WorkflowFactory.from({cwlVersion: modelJson.cwlVersion || 'v1.0'});
      const step = workflowModel.addStepFromProcess(modelJson);
      if (modelJson.id) {
        workflowModel.changeStepId(step, modelJson.id);
      }
      workflowModel.steps[0].in.forEach((input) => {
        if (isType(input, ['File', 'Directory'])) {
          workflowModel.createInputFromPort(input);
        } else {
          workflowModel.exposePort(input);
        }
      });
      workflowModel.steps[0].out.forEach((output) => {
        workflowModel.createOutputFromPort(output);
      });
      return workflowModel;
    }
    return WorkflowFactory.from(modelJson);
  };

  initializeGraphEventListeners = (graph) => {
    if (!graph) {
      return;
    }
    graph.on('afterChange', this.onSourceChanged);
    graph.getPlugin(SelectionPlugin).registerOnSelectionChange((selectedNode) => {
      if (
        this.model &&
        selectedNode &&
        selectedNode.dataset &&
        selectedNode.dataset.id
      ) {
        this.selected = this.model.findById(selectedNode.dataset.id);
      } else {
        this.selected = undefined;
      }
    });
  };

  initializeGraph = (svgRoot) => {
    try {
      if (this._fileRequest && svgRoot) {
        const response = yaml.load(base64toString(this._fileRequest.response));
        this.model = this.initializeModel(response);
        this.cwlWorkflow = new Workflow({
          svgRoot: svgRoot,
          model: this.model,
          plugins: [
            new SVGArrangePlugin(),
            new SVGPortDragPlugin(),
            new SVGNodeMovePlugin(),
            new SVGEdgeHoverPlugin(),
            new SVGValidatePlugin(),
            new SelectionPlugin(),
            new ZoomPlugin(),
            new DeletionPlugin()
          ],
          editingEnabled: !readOnly
        });
        this.cwlWorkflow.fitToViewport();
        this.initializeGraphEventListeners(this.cwlWorkflow);
        this.setState({
          modified: false
        });
      }
    } catch (error) {
      console.warn('Error parsing CWL:', error.message);
      this.cwlWorkflow = undefined;
    }
  }

  draw () {
    this.onFullScreenChanged();
  }

  onFullScreenChanged () {
    if (this.cwlWorkflow) {
      this.cwlWorkflow.fitToViewport();
    }
  }

  zoomIn () {
    if (this.cwlWorkflow) {

    }
  }

  zoomOut () {
    if (this.cwlWorkflow) {

    }
  }

  onDragOver = (event) => {
    if (this.model && !readOnly) {
      event.preventDefault();
    }
  };

  onDrop = (event) => {
    if (readOnly) {
      return;
    }
    const data = event.dataTransfer.getData('text/plain');
    if (
      data &&
      typeof data === 'string' &&
      /^docker:/i.test(data)
    ) {
      const docker = data.split(':').slice(1).join(':');
      let position;
      if (event.nativeEvent && this.cwlWorkflow) {
        const {
          clientX,
          clientY
        } = event.nativeEvent;
        position = this.cwlWorkflow.transformScreenCTMtoCanvas(clientX, clientY);
      }
      (this.addTool)(docker, position);
    }
  };

  addTool = async (docker, position) => {
    if (readOnly) {
      return;
    }
    const hide = message.loading(
      (<span>Adding <b>{docker}</b></span>),
      0
    );
    const {
      dockerRegistries
    } = this.props;
    try {
      const [registryName = '', groupName = '', toolNameAndVersion = ''] = docker.split('/');
      const [toolName = '', version = 'latest'] = toolNameAndVersion.toLowerCase().split(':');
      const image = `${groupName}/${toolName}`.toLowerCase();
      await dockerRegistries.fetchIfNeededOrWait();
      if (dockerRegistries.error) {
        throw new Error(dockerRegistries.error);
      }
      const registry = ((dockerRegistries.value || {}).registries || [])
        .find((aRegistry) => (aRegistry.path || '').toLowerCase() === registryName.toLowerCase());
      if (!registry) {
        throw new Error(`Registry ${registryName} not found`);
      }
      const group = (registry.groups || [])
        .find((aGroup) => aGroup.name.toLowerCase() === groupName.toLowerCase());
      if (!group) {
        throw new Error(`Group ${groupName} not found`);
      }
      const tool = (group.tools || [])
        .find((aTool) => aTool.image.toLowerCase() === image);
      if (!tool) {
        throw new Error(`Tool ${image} not found`);
      }
      const toolInfo = new LoadTool(image, registryName);
      const settings = new LoadToolVersionSettings(tool.id);
      await Promise.all([toolInfo.fetch(), settings.fetch()]);
      if (toolInfo.error) {
        throw new Error(toolInfo.error);
      }
      if (settings.error) {
        throw new Error(settings.error);
      }
      const versions = settings.value || [];
      const getToolVersion = (versionName = '') => versions
        .find((aVersion) => (aVersion.version || '').toLowerCase() === versionName.toLowerCase());
      const toolVersion = getToolVersion(version) || getToolVersion('latest') || versions[0];
      if (!toolVersion) {
        throw new Error('Tool version not found');
      }
      const {
        defaultCommand,
        instanceType: defaultInstanceType,
        disk: defaultDisk
      } = toolInfo.value;
      const {
        settings: toolVersionSettings = []
      } = toolVersion;
      const {
        configuration = {}
      } = toolVersionSettings[0] || {};
      const {
        cmd_template: cmdTemplate = defaultCommand,
        // eslint-disable-next-line
        instance_disk: disk = defaultDisk,
        // eslint-disable-next-line
        instance_size: instanceType = defaultInstanceType,
        parameters = {}
      } = configuration || {};
      const inputs = [];
      const outputs = [];
      Object.entries(parameters || {}).forEach(([parameterName, parameter]) => {
        const {
          type = 'string',
          // eslint-disable-next-line
          value
        } = parameter;
        switch (type.toLowerCase()) {
          case 'output':
            outputs.push({
              id: parameterName,
              type: 'File?',
              outputBinding: {
                glob: '*'
              }
            });
            break;
          case 'input':
          case 'common':
          case 'path':
            inputs.push({
              id: parameterName,
              type: 'File?',
              inputBinding: {
                position: inputs.length
              }
            });
            break;
          case 'string':
          default:
            inputs.push({
              id: parameterName,
              type: 'string',
              inputBinding: {
                position: inputs.length
              }
            });
            break;
        }
      });
      const coords = position
        ? {'sbg:x': position.x, 'sbg:y': position.y}
        : {};
      const toolModel = {
        class: 'CommandLineTool',
        cwlVersion: 'v1.0',
        id: `id_${moment.utc().unix()}`,
        ...coords,
        baseCommand: cmdTemplate ? [cmdTemplate] : [],
        inputs,
        outputs,
        label: toolName,
        'arguments': [
          {
            'position': 0,
            'prefix': 'aaa',
            'valueFrom': 'hello'
          },
          {
            'position': 0,
            'prefix': 'bbb',
            'valueFrom': 'world'
          }
        ],
        requirements: [
          {
            class: 'DockerRequirement',
            dockerPull: docker
          }
        ]
      };
      const step = this.model.addStepFromProcess(toolModel);
      (step.in || []).forEach((input, idx) => {
        if (isType(input, ['File', 'Directory'])) {
          input.isVisible = true;
          step.createWorkflowStepInputModel(input);
        } else {
          this.model.exposePort(input);
        }
      });
      this.onSourceChanged();
    } catch (error) {
      console.log(error);
      message.error(error.message, 5);
    } finally {
      hide();
    }
  };

  reDraw = () => {
    if (this.cwlWorkflow && typeof this.cwlWorkflow.draw === 'function') {
      this.cwlWorkflow.draw();
    }
  };

  onSourceChanged = () => {
    if (this.model) {
      const source = this.model.serialize();
      const cwl = yaml.dump(source);
      this.setState({
        modified: true
      });
    }
  };

  getModifiedCode () {
    if (this.model) {
      const source = this.model.serialize();
      return yaml.dump(source);
    }
    return undefined;
  }

  renderGraph () {
    const {parameters, pipeline} = this.props;
    const {modified} = this.state;
    if ((parameters.pending && !parameters.loaded) ||
      (pipeline.pending && !pipeline.loaded) ||
      (!this._fileRequest || (
        this._fileRequest.pending && !this._fileRequest.loaded
      ))) {
      return <LoadingView />;
    }
    if (parameters.error) {
      return <Alert type="warning" message={parameters.error} />;
    }
    if (this._fileRequest && this._fileRequest.error) {
      return <Alert type="warning" message={this._fileRequest.error} />;
    }
    return (
      <div className={styles.cwlGraph}>
        <div
          className={
            classNames(
              styles.cwlControls,
              'cp-panel'
            )
          }
        >
          <Button
            shape="circle"
            style={{zIndex: 1}}
            type="primary"
            disabled={!modified || readOnly}
            onClick={this.openCommitFormDialog}
          >
            <Icon type="save" />
          </Button>
        </div>
        <CWLProperties
          title="Command Line Tool properties"
          buttonTitle="Properties"
          properties={[
            this.commandLineTool ? {
              key: 'properties',
              title: 'Command Line Tool properties',
              buttonTitle: 'Properties',
              component: (
                <CWLCommandLineTool
                  disabled={readOnly}
                  tool={this.commandLineTool}
                  step={this.selected}
                  onRedraw={this.reDraw}
                  onChange={this.onSourceChanged}
                />
              )
            } : false,
            readOnly ? false : {
              key: 'tools',
              title: 'Tools repository',
              buttonTitle: 'Tools',
              component: (<CWLToolsRepository disabled={readOnly} />)
            }
          ].filter(Boolean)}
        />
        <div
          className={styles.cwlGraphContainer}
          onDragOver={this.onDragOver}
          onDrop={this.onDrop}
        >
          <svg
            ref={this.initializeGraph}
            id="cwl-workflow"
            className="cwl-workflow"
          />
        </div>
      </div>
    );
  }
}
