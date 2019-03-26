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
import Graph from './Graph';
import {inject, observer} from 'mobx-react';
import {observable} from 'mobx';
import Pipeline from '../../../../../models/pipelines/Pipeline';
import VersionParameters from '../../../../../models/pipelines/VersionParameters';
import VersionFile from '../../../../../models/pipelines/VersionFile';
import pipeline from 'pipeline-builder';
import 'pipeline-builder/dist/pipeline.css';
import styles from './Graph.css';
import LoadingView from '../../../../special/LoadingView';
import {
  ResizablePanel,
  ResizeAnchors
} from '../../../../special/resizablePanel';
import {Alert, Row, Button, Icon, message, Modal, Form, Tooltip} from 'antd';
import {prepareTask, WDLItemProperties} from './forms/WDLItemProperties';
import {Primitives, testPrimitiveTypeFn, quotesFn} from './forms/utilities';
import {ItemTypes} from '../../../model/treeStructureFunctions';

const graphFitContentOpts = {padding: 24};

const nameReplacementRegExp = /[-\s.,:;'"!@#$%^&*()\[\]{}\/\\~`±§]/g;

function reportWDLError (error) {
  const message = error.message || error;
  if (message && typeof message === 'string') {
    message.error(message, 5);
  }
  console.error('WDL ERROR:', error.message || error);
}

@inject('runDefaultParameters')
@inject(({history, routing, pipelines}, params) => ({
  history,
  routing,
  parameters: new VersionParameters(params.pipelineId, params.version),
  pipeline: new Pipeline(params.pipelineId),
  pipelineId: params.pipelineId,
  pipelineVersion: params.version,
  pipelines
}))
@observer
export default class WdlGraph extends Graph {

  wdlVisualizer;
  workflow;
  @observable previousSuccessfulCode;

  initializeContainer = async (container) => {
    if (container) {
      const script = atob(this._mainFileRequest.response);
      this.wdlVisualizer = new pipeline.Visualizer(container);
      this.wdlVisualizer.paper.on('cell:pointerclick', this.onSelectItem);
      this.wdlVisualizer.paper.on('blank:pointerclick', this.onSelectItem);
      this.wdlVisualizer.paper.on('link:connect', this.modelChanged);
      this.wdlVisualizer.paper.model.on('remove', this.modelChanged);
      this.wdlVisualizer.paper.el.ondrop = this.onDropModel;
      await this.applyCode(script, true);
    } else {
      if (this.wdlVisualizer) {
        this.wdlVisualizer.paper.off('link:connect', this.modelChanged);
        this.wdlVisualizer.paper.model.off('remove', this.modelChanged);
        this.wdlVisualizer.paper.off('cell:pointerclick', this.onSelectItem);
        this.wdlVisualizer.paper.off('blank:pointerclick', this.onSelectItem);
      }
      this.wdlVisualizer = null;
    }
  };

  onDropModel = async (e) => {
    if (!this.wdlVisualizer) {
      return;
    }
    const modelId = (e.path || []).filter(el => el.attributes &&
      el.attributes['data-type'] &&
      ['VisualGroup', 'VisualWorkflow'].indexOf(el.attributes['data-type'].value) >= 0 &&
      el.attributes['model-id']).map(m => m.attributes['model-id'].value)[0];
    let [target] = this.wdlVisualizer.paper.model.getElements()
      .filter(e => e.attributes.type === 'VisualWorkflow');
    if (modelId) {
      const [model] = this.wdlVisualizer.paper.model.getElements().filter(m => m.id === modelId);
      target = model || target;
    }
    const parts = (e.dataTransfer.getData('dropDataKey') || '').split('_');
    if (target && target.step && parts.length >= 2 && parts[0] === ItemTypes.pipeline) {
      const pipelineId = parts[1];
      if (+pipelineId === +this.props.pipelineId) {
        message.error('You cannot use the same pipeline');
        return;
      }
      let pipelineVersion = parts.slice(2).join('_');
      const hide = message.loading('Fetching pipeline info...', 0);
      const pipelineRequest = new Pipeline(pipelineId);
      await pipelineRequest.fetch();
      if (pipelineRequest.error) {
        hide();
        message.error(pipelineRequest.error, 3);
        return;
      } else if (!pipelineVersion &&
        (!pipelineRequest.value.currentVersion || !pipelineRequest.value.currentVersion.name)) {
        hide();
        message.error('Cannot fetch latest version of pipeline', 3);
        return;
      }
      if (!pipelineVersion) {
        pipelineVersion = pipelineRequest.value.currentVersion.name;
      }
      const versionRequest = this.props.pipelines.getConfiguration(pipelineId, pipelineVersion);
      await versionRequest.fetchIfNeededOrWait();
      hide();
      if (versionRequest.error) {
        message.error(versionRequest.error, 3);
      } else {
        await this.dropPipeline(
          target.step,
          pipelineRequest.value,
          pipelineVersion,
          versionRequest.value
        );
      }
    }
  };

  dropPipeline = async (target, pipelineInfo, version, configurations) => {
    const [defaultConfiguration] = (configurations || []).filter(c => c.default && c.configuration);
    await this.props.runDefaultParameters.fetchIfNeededOrWait();
    const systemParameters = this.props.runDefaultParameters.loaded
      ? (this.props.runDefaultParameters.value || []).map(p => p.name)
      : [];
    const skipParameterFn = (name) => systemParameters.indexOf(name) >= 0;
    const listInputParametersStr = () => {
      const params = [];
      Object.keys(defaultConfiguration.configuration.parameters || []).forEach(key => {
        if (!skipParameterFn(key) &&
          defaultConfiguration.configuration.parameters[key].type !== 'output') {
          params.push(`\\\n\t\t\t--${key} $\{${key.replace(nameReplacementRegExp, '_')}}`);
        }
      });
      return params.join(' ');
    };
    const task = {
      name: pipelineInfo.name.replace(nameReplacementRegExp, '_'),
      command: `pipe run \t--pipeline "${pipelineInfo.name}@${version}" -s -y ${listInputParametersStr()}`,
      inputs: [],
      outputs: []
    };
    const mapParameter = (name, p, value) => {
      if (skipParameterFn(name)) {
        return null;
      }
      let type = 'String';
      switch (p.type) {
        case 'input':
        case 'output':
        case 'path':
        case 'common':
          type = 'File';
          break;
        case 'bool':
        case 'boolean':
          type = 'Boolean';
          break;
      }
      const v = p.value || value;
      return {
        name: name.replace(nameReplacementRegExp, '_'),
        value: v && (testPrimitiveTypeFn(Primitives.string, type) || testPrimitiveTypeFn(Primitives.file, type))
          ? `"${quotesFn.clear(v)}"`
          : v,
        type
      };
    };
    const inputs = [];
    const outputs = [];
    Object.keys(defaultConfiguration.configuration.parameters || []).forEach(key => {
      if (defaultConfiguration.configuration.parameters[key].type !== 'output') {
        inputs.push(
          mapParameter(key, defaultConfiguration.configuration.parameters[key])
        );
      } else {
        outputs.push(
          mapParameter(key, defaultConfiguration.configuration.parameters[key], ' ')
        );
      }
    });
    task.inputs = inputs.filter(p => !!p);
    task.outputs = outputs.filter(p => !!p);
    if (defaultConfiguration) {
      Modal.confirm({
        title: `Add pipeline '${pipelineInfo.name}' as a task to ${target.type}?`,
        style: {
          wordWrap: 'break-word'
        },
        onOk: () => this.addTask(task, target),
        okText: 'ADD',
        cancelText: 'CANCEL'
      });
    }
  };

  applyCode = async (code, clearModifiedConfig) => {
    const hide = message.loading('Loading...');
    const onError = (message) => {
      if (clearModifiedConfig) {
        this.setState({
          selectedElement: null,
          editableTask: null,
          error: message,
          modifiedParameters: null
        });
      } else {
        this.setState({
          selectedElement: null,
          editableTask: null,
          error: message
        });
      }
    };
    try {
      const parseResult = await pipeline.parse(code);
      if (parseResult.status) {
        this.workflow = parseResult.model[0];
        this.clearWrongPorts(this.workflow);
        this.previousSuccessfulCode = code;
        this.wdlVisualizer.attachTo(this.workflow);
        if (clearModifiedConfig) {
          this.setState({
            selectedElement: null,
            editableTask: null,
            canZoomIn: true,
            canZoomOut: true,
            error: null,
            modifiedConfig: null
          });
        } else {
          this.setState({
            selectedElement: null,
            editableTask: null,
            canZoomIn: true,
            canZoomOut: true,
            error: null
          });
        }
        this.onFullScreenChanged();
      } else {
        onError(parseResult.message);
      }
    } catch (e) {
      onError(e);
    } finally {
      hide();
    }
  };

  clearWrongPorts = (step) => {
    if (step.i) {
      for (let variable in step.i) {
        if (step.i.hasOwnProperty(variable) && step.i[variable].inputs) {
          const inputsToRemove = [];
          for (let i = 0; i < step.i[variable].inputs.length; i++) {
            if (!step.i[variable].inputs[i].from || step.i[variable].inputs[i].from === '') {
              inputsToRemove.push(step.i[variable].inputs[i]);
            }
          }
          for (let i = 0; i < inputsToRemove.length; i++) {
            const index = step.i[variable].inputs.indexOf(inputsToRemove[i]);
            if (index >= 0) {
              step.i[variable].inputs.splice(index, 1);
            }
          }
        }
      }
    }
    if (step.children) {
      for (let child in step.children) {
        if (step.children.hasOwnProperty(child)) {
          this.clearWrongPorts(step.children[child]);
        }
      }
    }
  };

  unsavedChangesConfirm = (onOk, onCancel) => {
    return Modal.confirm({
      title: 'You have unsaved changes. Continue?',
      style: {
        wordWrap: 'break-word'
      },
      async onOk () {
        onOk();
      },
      async onCancel () {
        onCancel();
      },
      okText: 'Yes',
      cancelText: 'No'
    });
  };

  onSelectItem = () => {
    if ((this.wdlVisualizer.selection[0] || {}).step === this.state.selectedElement) {
      return;
    }
    if (this.wdlVisualizer && this.wdlVisualizer.selection &&
      this.wdlVisualizer.selection[0] && this.wdlVisualizer.selection[0].step) {
      this.setState({selectedElement: this.wdlVisualizer.selection[0].step},
        this.prepareEditableTask);
    } else {
      this.setState({selectedElement: null}, this.resetEditableTask);
    }
  };

  modelChanged = () => {
    this.setState({modified: true});
  };

  getModifiedCode () {
    try {
      return pipeline.generate(this.workflow);
    } catch (___) {
      return this.previousSuccessfulCode;
    }
  }

  getModifiedParameters () {
    return this.state.modifiedParameters;
  }

  getFilePath () {
    if (this._mainFileRequest) {
      return this._mainFileRequest.path;
    }
    return null;
  }

  draw () {
    this.onFullScreenChanged();
  }

  onFullScreenChanged = () => {
    if (this.wdlVisualizer) {
      this.wdlVisualizer.zoom.fitToPage(graphFitContentOpts);
    }
  };

  zoomIn = () => {
    if (this.wdlVisualizer) {
      this.wdlVisualizer.zoom.zoomIn();
    }
  };

  zoomOut = () => {
    if (this.wdlVisualizer) {
      this.wdlVisualizer.zoom.zoomOut();
    }
  };

  @observable _mainFileRequest;

  async revertChanges () {
    if (this._mainFileRequest) {
      const script = atob(this._mainFileRequest.response);
      await this.applyCode(script);
      this.setState({modified: false, selectedElement: null}, this.resetEditableTask);
    }
  }

  getSelectedElementWorkflow = () => {
    return this.state.selectedElement.parent || this.workflow;
  };

  confirmDeleteTask = () => {
    if (this.state.selectedElement && this.getSelectedElementWorkflow()) {
      const deleteTask = () => {
        this.getSelectedElementWorkflow().remove(this.state.selectedElement.name);
        this.setState({modified: true, selectedElement: null, editableTask: null});
      };
      Modal.confirm({
        title: `Are you sure you want to delete ${this.state.selectedElement.name}?`,
        style: {
          wordWrap: 'break-word'
        },
        onOk () {
          deleteTask();
        }
      });
    }
  };

  prepareEditableTask = () => {
    let task;
    if (this.state.selectedElement) {
      task = {
        alias: this.state.selectedElement.name,
        type: this.state.selectedElement.type || 'task'
      };
      const {action} = this.state.selectedElement;
      if (action) {
        task.name = action.name;
        task.command = action.data
          ? action.data.command
          : undefined;
        task.runtime = action.data
          ? action.data.runtime
          : undefined;
        task.inputs = action.i;
        task.outputs = action.o;
      }
    }
    this.setState({editableTask: prepareTask(task)});
  };

  resetEditableTask = () => {
    if (this.itemEditForm) {
      this.itemEditForm.revertForm();
    }
    this.setState({editableTask: null});
  };

  callCountMap;

  getCallSuffix (desired) {
    desired = desired.toLowerCase();
    if (!this.callCountMap) {
      this.callCountMap = {};
      for (let child in this.workflow.actions) {
        if (this.workflow.actions.hasOwnProperty(child)) {
          child = child.toLowerCase();
          let call = child.split('_');
          const callIndex = parseInt(call[call.length-1]);
          if (callIndex >= 0) {
            call = call.slice(0, call.length-1);
            const callName = call.join();
            this.callCountMap[callName] = callIndex + 1;
          } else {
            this.callCountMap[child] = 1;
          }
        }
      }
    }
    if (!this.callCountMap[desired]) {
      this.callCountMap[desired] = 1;
      return '';
    } else {
      const val = this.callCountMap[desired];
      this.callCountMap[desired] = val + 1;
      return `_${val}`;
    }
  }

  processTaskVariables = (variables, type, workflow, action) => {
    const newPorts = Object.keys(variables || {}).map(k => ({
      name: k,
      ...variables[k]
    })).filter(v => {
      return !v.previousName || !action[type][v.previousName];
    });
    if (newPorts.length > 0) {
      workflow.actions[action.name].addPorts(newPorts.reduce((result, current) => {
        if (!result[type]) {
          result[type] = {};
        }
        result[type][current.name] = {
          type: current.type,
          multi: current.multi,
          default: current.default
        };
        return result;
      }, {}));
    }
    const modifiedPorts = Object.keys(variables || {}).map(k => ({
      name: k,
      ...variables[k]
    })).filter(v => {
      return !!v.previousName &&
        !!action[type][v.previousName] &&
        (v.name !== v.previousName ||
          action[type][v.previousName].type !== v.type ||
          action[type][v.previousName].default !== v.default ||
          action[type][v.previousName].multi !== v.multi);
    });
    modifiedPorts.forEach(p => {
      const name = p.previousName;
      if (p.default) {
        action[type][name].default = p.default;
      } else {
        delete action[type][name].default;
      }
      action[type][name].type = p.type || '';
      action[type][name].multi = p.multi;
      if (name !== p.name) {
        action[`rename${type.toUpperCase()}Port`] &&
        action[`rename${type.toUpperCase()}Port`](name, p.name); // renameIPort or renameOPort call
      }
    });
    const removedPorts = Object.keys(action[type] || {})
      .filter(k => !variables.hasOwnProperty(k));
    if (removedPorts.length > 0) {
      workflow.actions[action.name].removePorts(removedPorts
        .reduce((result, current) => {
          if (!result[type]) {
            result[type] = [];
          }
          result[type].push(current);
          return result;
        }, {}));
    }
    return newPorts.length > 0 || modifiedPorts.length > 0 || removedPorts.length > 0;
  };

  editConfig = () => {
    const parameters = {};
    const clearQuotes = (text) => {
      if (!text) {
        return undefined;
      }
      text = text.trim();
      if (text.startsWith('"')) {
        text = text.substring(1);
      }
      if (text.endsWith('"')) {
        text = text.substring(0, text.length - 1);
      }
      return text;
    };
    for (let inputVar in this.workflow.i) {
      if (this.workflow.i.hasOwnProperty(inputVar)) {
        let type = 'String';
        if (this.workflow.i[inputVar].desc.type === 'File') {
          type = 'input';
        }
        parameters[`${this.workflow.name}_${inputVar}`] = {
          type,
          value: clearQuotes(this.workflow.i[inputVar].desc.default)
        };
      }
    }
    for (let outputVar in this.workflow.o) {
      if (this.workflow.o.hasOwnProperty(outputVar)) {
        if (this.workflow.o[outputVar].desc.type === 'File') {
          parameters[`${this.workflow.name}_${outputVar}`] = {
            type: 'output',
            value: clearQuotes(this.workflow.o[outputVar].desc.default)
          };
        }
      }
    }
    this.setState({modifiedParameters: parameters});
  };

  addTask = async (task, parent) => {
    const actionData = {
      i: task.inputs.reduce((result, v) => {
        result[v.name] = {
          type: v.type || ''
        };
        if (v.value && v.value.length) {
          result[v.name].default = v.value;
        }
        return result;
      }, {}),
      o: task.outputs.reduce((result, v) => {
        result[v.name] = {
          type: v.type || ''
        };
        result[v.name].default = v.value;
        return result;
      }, {}),
      data: {
        command: task.command,
        runtime: task.runtime
      }
    };
    const callSuffix = this.getCallSuffix(task.name);
    const action = new pipeline.Action(task.name + callSuffix, actionData);
    const newCall = new pipeline.Step(task.name + callSuffix, action);
    const element = parent || this.state.selectedElement || this.workflow;
    try {
      element && element.add(newCall);
      this.clearWrongPorts(this.workflow);
      const parseResult = await pipeline.parse(pipeline.generate(this.workflow));
      if (!parseResult.status) {
        reportWDLError(parseResult);
        return;
      }
    } catch (error) {
      reportWDLError(error);
      return;
    }
    this.wdlVisualizer.attachTo(this.workflow);
    this.setState({
      editableTask: null,
      selectedElement: null
    });
  };

  addScatter = async (task) => {
    const actionData = {
      i: task.inputs.reduce((result, v) => {
        result[v.name] = {
          type: v.type || ''
        };
        if (v.value && v.value.length) {
          result[v.name].default = v.value;
        }
        return result;
      }, {})
    };
    const newGroup =
      new pipeline.Group(`scatter${this.getCallSuffix('scatter')}`, 'scatter', actionData);
    this.workflow.add(newGroup);
    this.clearWrongPorts(this.workflow);
    try {
      const parseResult = await pipeline.parse(pipeline.generate(this.workflow));
      if (!parseResult.status) {
        reportWDLError(parseResult);
        return null;
      }
    } catch (error) {
      reportWDLError(error);
      return null;
    }
    this.wdlVisualizer.attachTo(this.workflow);
    this.setState({
      editableTask: null,
      selectedElement: null
    });
    return newGroup;
  };

  get isScatter () {
    return this.state.selectedElement &&
      this.state.selectedElement.type === 'scatter';
  }

  get isWorkflow () {
    return !this.state.selectedElement || this.state.selectedElement.type === 'workflow';
  }

  get isTask () {
    return this.state.selectedElement &&
      (!this.state.selectedElement.type || this.state.selectedElement.type === 'task');
  }

  renderDeleteAction = () => {
    if (!this.canModifySources) {
      return null;
    }
    if (this.isScatter) {
      return (
        <Button
          id="wdl-graph-scatter-delete-button"
          key="remove"
          type="danger"
          style={{width: '100%'}}
          onClick={this.confirmDeleteTask}>
          <Icon type="delete" /> DELETE <b>{
          this.state.selectedElement.type
            ? this.state.selectedElement.type.toUpperCase()
            : undefined
          }</b>
        </Button>
      );
    } else if (this.isTask) {
      return (
        <Button
          id="wdl-graph-task-delete-button"
          key="remove"
          type="danger"
          style={{width: '100%'}}
          onClick={this.confirmDeleteTask}>
          <Icon type="delete" /> DELETE <b>{
          this.state.selectedElement.name
            ? this.state.selectedElement.name.toUpperCase()
            : undefined
          }</b>
        </Button>
      );
    }
    return null;
  };

  renderItemActions = () => {
    if (!this.canModifySources) {
      return undefined;
    }
    if (this.isWorkflow) {
      const addNewScatter = () => {
        return this.addScatter({inputs: [{type: Primitives.scatterItem, name: 'scatterItem'}]});
      };
      const addNewTask = () => {
        return this.addTask({name: 'workflowTask', inputs: [], outputs: []});
      };
      return [
        <Button
          id="wdl-graph-workflow-add-scatter-button"
          key="add scatter"
          size="small"
          onClick={addNewScatter}>
          <Icon type="plus" /> ADD SCATTER
        </Button>,
        <Button
          id="wdl-graph-workflow-add-task-button"
          key="add task"
          size="small"
          onClick={addNewTask}>
          <Icon type="plus" /> ADD TASK
        </Button>
      ];
    } else if (this.isScatter) {
      const addNewTask = () => {
        return this.addTask({name: 'scatterTask', inputs: [], outputs: []});
      };
      let childrenCount = 0;
      for (let child in this.state.selectedElement.children) {
        if (this.state.selectedElement.children.hasOwnProperty(child)) {
          childrenCount += 1;
        }
      }
      return [
        childrenCount === 0
          ? (
            <Button
              id="wdl-graph-scatter-add-task-button"
              key="add task"
              size="small"
              onClick={addNewTask}>
              <Icon type="plus" /> ADD TASK
            </Button>
          )
          : undefined
      ];
    }
    return undefined;
  };

  toggleLinks = () => {
    const showAllLinks = !this.state.showAllLinks;
    this.setState({showAllLinks}, () => {
      this.wdlVisualizer && this.wdlVisualizer.paper.model.off('remove', this.modelChanged);
      this.wdlVisualizer && this.wdlVisualizer.togglePorts(true, showAllLinks);
      setTimeout(() => {
        this.wdlVisualizer && this.wdlVisualizer.paper.model.on('remove', this.modelChanged);
      }, 100);
    });
  };

  fitGraph = () => {
    this.wdlVisualizer && this.wdlVisualizer.zoom.fitToPage(graphFitContentOpts);
  };

  layoutGraph = () => {
    this.wdlVisualizer && this.wdlVisualizer.layout() && this.fitGraph();
  };

  togglePropertiesSidePanelState = () => {
    const propertiesPanelVisible = !this.state.propertiesPanelVisible;
    this.setState({propertiesPanelVisible});
  };

  closePropertiesSidePanel = () => {
    const propertiesPanelVisible = false;
    this.setState({propertiesPanelVisible});
  };

  renderSidePanelsControlButtons = () => {
    if (this.state.propertiesPanelVisible) {
      return null;
    }
    return (
      <div className={styles.panelButtonsRow}>
        <Button
          className={`${styles.propertiesButton} ${this.state.propertiesPanelVisible ? styles.selected : ''}`}
          onClick={this.togglePropertiesSidePanelState}>
          PROPERTIES
        </Button>
      </div>
    );
  };

  renderSidePanelTitle = (title, onPanelClose) => {
    return (
      <Row
        type="flex"
        justify="space-between"
        align="middle"
        style={{
          backgroundColor: '#efefef',
          borderBottom: '1px solid #ddd',
          borderTop: '1px solid #ddd',
          padding: '0px 5px'
        }}>
        <span>{title}</span>
        {
          onPanelClose &&
          <Icon
            type="close"
            onClick={onPanelClose}
            style={{cursor: 'pointer'}} />
        }
      </Row>
    );
  };

  @observable
  itemEditForm;

  initializeItemEditForm = (form) => {
    if (form) {
      this.itemEditForm = form;
    }
  };

  onFieldsChange = async (props, fields) => {
    if (!this.state.selectedElement || !this.workflow) {
      return;
    }
    const values = Object.keys(fields || {})
      .map(key => ({...fields[key], key}))
      .filter(f => !f.validating && !f.dirty && f.touched && !f.errors)
      .reduce((result, current) => {
        result[current.key] = current.value;
        return result;
      }, {});
    if (Object.keys(values || {}).length === 0) {
      return;
    }
    let modified = false;
    const {inputs, outputs} = values;
    let portsModified = false;
    if (this.state.selectedElement.action && inputs) {
      portsModified = this.processTaskVariables(
        inputs,
        'i',
        this.workflow,
        this.state.selectedElement.action
      ) || portsModified;
    }
    if (this.state.selectedElement.action && outputs) {
      portsModified = this.processTaskVariables(
        outputs,
        'o',
        this.workflow,
        this.state.selectedElement.action
      ) || portsModified;
    }
    if (portsModified && this.state.selectedElement.type === 'workflow') {
      this.editConfig();
    }
    if (!this.isScatter && this.state.selectedElement.action) {
      const {alias, command} = values;
      let child = this.getSelectedElementWorkflow().children[this.state.selectedElement.name];
      if (child && alias && child.name !== alias) {
        modified = true;
        child.name !== alias && child.rename(alias);
      }
      const action = this.workflow.actions[this.state.selectedElement.action.name];
      if (action && command && action.data.command !== command) {
        modified = true;
        action.data.command = command;
      }
      let runtime;
      if (values.hasOwnProperty('runtime.docker')) {
        runtime = {
          docker: values['runtime.docker']
        };
      }
      if (action && runtime) {
        const runtimeAreEqual = (r1, r2) => {
          if (!!r1 !== !!r2) {
            return false;
          }
          if (!r1 && !r2) {
            return true;
          }
          return r1.docker === r2.docker;
        };
        if (!runtimeAreEqual(action.data.runtime, runtime)) {
          modified = true;
          action.data.runtime = runtime;
          if (!action.data.runtime.docker) {
            delete action.data.runtime.docker;
          }
        }
      }
      if (modified) {
        try {
          const generatedCode = pipeline.generate(this.workflow);
          const parseResult = await pipeline.parse(generatedCode);
          if (!parseResult.status) {
            reportWDLError(parseResult);
          }
        } catch (error) {
          reportWDLError(error);
        }
      }
    }
    if (modified || portsModified) {
      this.modelChanged();
    }
  };

  editWDLItemComponent = Form.create({
    onFieldsChange: this.onFieldsChange
  })(WDLItemProperties);

  renderPropertiesPanel = () => {
    if (!this.state.propertiesPanelVisible) {
      return null;
    }

    const panelTitle = this.renderSidePanelTitle('Properties', this.closePropertiesSidePanel);
    const itemActions = this.renderItemActions();
    const deleteAction = this.renderDeleteAction();
    const EditWDLItemComponent = this.editWDLItemComponent;
    const itemDetails = this.state.editableTask && <EditWDLItemComponent
      onInitialize={this.initializeItemEditForm}
      workflow={this.workflow}
      task={this.state.editableTask}
      type={this.state.editableTask ? this.state.editableTask.type : undefined}
      readOnly={!this.canModifySources}
      pending={false} />;

    return (
      <ResizablePanel
        className={`${styles.wdlGraphSidePanel} ${styles.right}`}
        resizeAnchors={[ResizeAnchors.left]}>
        {panelTitle}
        <Row type="flex" className={styles.wdlGraphSidePanelContentContainer}>
          <Row type="flex" className={styles.wdlGraphSidePanelContent}>
            {
              itemActions &&
              <Row type="flex" style={{width: '100%'}}>
                {itemActions}
              </Row>
            }
            {
              itemDetails &&
              <Row
                type="flex"
                className={itemActions ? styles.wdlGraphSidePanelSection : ''}
                style={{paddingRight: 5, flex: 1, width: '100%'}}>
                {itemDetails}
              </Row>
            }
          </Row>
          {
            deleteAction &&
            <Row type="flex" className={styles.wdlGraphSidePanelSection}>
              {deleteAction}
            </Row>
          }
        </Row>
      </ResizablePanel>
    );
  };

  renderAppearancePanel = () => {
    return (
      <div className={`${styles.wdlGraphSidePanel} ${styles.left}`}>
        {
          this.canModifySources &&
          <Tooltip title="Save" placement="right">
            <Button
              id="wdl-graph-save-button"
              className={`${styles.wdlAppearanceButton} ${styles.active} ${styles.noFade}`}
              disabled={!this.state.modified}
              type="primary"
              shape="circle"
              onClick={this.openCommitFormDialog}>
              <Icon type="save" />
            </Button>
          </Tooltip>
        }
        {
          this.canModifySources &&
          <Tooltip title="Revert changes" placement="right">
            <Button
              id="wdl-graph-revert-button"
              className={`${styles.wdlAppearanceButton} ${styles.noFade}`}
              disabled={!this.state.modified}
              shape="circle"
              onClick={() => this.revertChanges()}>
              <Icon type="reload" />
            </Button>
          </Tooltip>
        }
        {
          this.canModifySources &&
          <div className={styles.separator}>{'\u00A0'}</div>
        }
        <Tooltip title="Layout" placement="right">
          <Button
            className={styles.wdlAppearanceButton}
            id="wdl-graph-layout-button"
            shape="circle"
            onClick={this.layoutGraph}>
            <Icon type="appstore-o" />
          </Button>
        </Tooltip>
        <Tooltip title="Fit to screen" placement="right">
          <Button
            className={styles.wdlAppearanceButton}
            id="wdl-graph-fit-button"
            shape="circle"
            onClick={this.fitGraph}>
            <Icon type="scan" />
          </Button>
        </Tooltip>
        <Tooltip
          title={this.state.showAllLinks ? 'Hide links' : 'Show links'}
          placement="right">
          <Button
            className={`${styles.wdlAppearanceButton} ${this.state.showAllLinks ? styles.active : ''}`}
            type={this.state.showAllLinks ? 'primary' : 'default'}
            id={`wdl-graph-${this.state.showAllLinks ? 'hide-links' : 'show-links'}-button`}
            shape="circle"
            onClick={this.toggleLinks}>
            <Icon type="swap" />
          </Button>
        </Tooltip>
        <Tooltip title="Zoom out" placement="right">
          <Button
            className={styles.wdlAppearanceButton}
            id="wdl-graph-zoom-out-button"
            shape="circle"
            onClick={this.zoomOut}
            disabled={!this.state.canZoomOut}>
            <Icon type="minus-circle-o" />
          </Button>
        </Tooltip>
        <Tooltip title="Zoom in" placement="right">
          <Button
            className={styles.wdlAppearanceButton}
            id="wdl-graph-zoom-in-button"
            shape="circle"
            onClick={this.zoomIn}
            disabled={!this.state.canZoomIn}>
            <Icon type="plus-circle-o" />
          </Button>
        </Tooltip>
        <Tooltip title="Fullscreen" placement="right">
          <Button
            className={styles.wdlAppearanceButton}
            id="wdl-graph-fuulscreen-button"
            shape="circle"
            onClick={this.toggleFullScreen}>
            <Icon type={this.state.fullScreen ? 'shrink' : 'arrows-alt'} />
          </Button>
        </Tooltip>
      </div>
    );
  };

  renderGraph () {
    if (this.props.parameters.pending ||
        this.props.pipeline.pending ||
        !this._mainFileRequest ||
        this._mainFileRequest.pending) {
      return <LoadingView />;
    }
    if (this.props.parameters.error) {
      return <Alert type="warning" message={this.props.parameters.error} />;
    }
    if (this._mainFileRequest && this._mainFileRequest.error) {
      return <Alert type="warning" message={this._mainFileRequest.error} />;
    }
    if (this.state.error) {
      const errorContent = (
        <Row>
          <Row>Error parsing wdl script:</Row>
          <Row>{this.state.error}</Row>
        </Row>
      );
      return (
        <Alert
          type="warning"
          message={errorContent} />
      );
    }
    return (
      <div
        className={styles.wdlGraph}
        onDragEnter={e => e.preventDefault()}
        onDragOver={e => e.preventDefault()}>
        {this.renderSidePanelsControlButtons()}
        {this.renderPropertiesPanel()}
        {this.renderAppearancePanel()}
        <div className={styles.wdlGraphContainer} >
          <div ref={this.initializeContainer} />
        </div>
      </div>
    );
  }

  componentDidUpdate () {
    if (!this.props.parameters.pending && !this.props.pipeline.pending &&
      !this.props.parameters.error &&
      (!this._mainFileRequest || this._mainFileRequest.version !== this.props.version)) {
      const filePathParts = this.props.parameters.value.main_file.split('.');
      if (filePathParts[filePathParts.length - 1].toLowerCase() === 'wdl') {
        this._mainFileRequest = new VersionFile(
          this.props.pipelineId,
          `src/${this.props.parameters.value.main_file}`,
          this.props.version
        );
        this._mainFileRequest.fetch();
      } else {
        this._mainFileRequest = new VersionFile(
          this.props.pipelineId,
          `src/${this.props.pipeline.value.name}.wdl`,
          this.props.version
        );
        this._mainFileRequest.fetch();
      }
    }
  }

  renderBottomGraphControlls = () => {
    return null;
  };

  async afterSaveChanges () {
    this._mainFileRequest = null;
  }

  async afterCommit () {
    return new Promise(resolve => {
      this.setState({modified: false}, () => resolve());
    });
  }

  _removeRouterListener = null;
  _routeChangeConfirm = null;

  componentDidMount () {
    this._removeRouterListener = this.props.history.listenBefore((location, callback) => {
      const locationBefore = this.props.routing.location.pathname;
      if (this.state.modified && !this._routeChangeConfirm) {
        const onOk = () => {
          callback();
          setTimeout(() => {
            this._routeChangeConfirm = null;
          }, 0);
        };
        const onCancel = () => {
          if (this.props.history.getCurrentLocation().pathname !== locationBefore) {
            this.props.history.replace(locationBefore);
          }
          setTimeout(() => {
            this._routeChangeConfirm = null;
          }, 0);
        };

        this._routeChangeConfirm = this.unsavedChangesConfirm(onOk, onCancel);
      } else {
        callback();
      }
    });
  }

  componentWillUnmount () {
    this._mainFileRequest = null;
    if (this._removeRouterListener) {
      this._removeRouterListener();
    }
  }

}
