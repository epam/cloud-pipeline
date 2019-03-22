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
import EditWDLToolForm from './forms/EditWDLToolForm';
import {Alert, Row, Col, Button, Icon, message, Modal} from 'antd';

@inject(({routing, pipelines}, params) => ({
  parameters: new VersionParameters(params.pipelineId, params.version),
  pipeline: new Pipeline(params.pipelineId),
  pipelineId: params.pipelineId
}))
@observer
export default class WdlGraph extends Graph {

  wdlVisualizer;
  workflow;

  initializeContainer = async (container) => {
    if (container) {
      const script = atob(this._mainFileRequest.response);
      this.wdlVisualizer = new pipeline.Visualizer(container);
      this.wdlVisualizer.paper.on('cell:pointerclick', this.onSelectItem);
      this.wdlVisualizer.paper.on('blank:pointerclick', this.onSelectItem);
      this.wdlVisualizer.paper.on('link:connect', this.modelChanged);
      this.wdlVisualizer.paper.model.on('remove', this.modelChanged);
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

  applyCode = async (code, clearModifiedConfig) => {
    const parseResult = await pipeline.parse(code);
    if (parseResult.status) {
      this.workflow = parseResult.model[0];
      this.clearWrongPorts(this.workflow);
      this.wdlVisualizer.attachTo(this.workflow);
      if (clearModifiedConfig) {
        this.setState({canZoomIn: true, canZoomOut: true, error: false, modifiedConfig: null});
      } else {
        this.setState({canZoomIn: true, canZoomOut: true, error: false});
      }
      this.onFullScreenChanged();
    } else {
      if (clearModifiedConfig) {
        this.setState({error: true, modifiedParameters: null});
      } else {
        this.setState({error: true});
      }
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

  onSelectItem = () => {
    if (this.wdlVisualizer && this.wdlVisualizer.selection && this.wdlVisualizer.selection.length) {
      this.setState({selectedElement: this.wdlVisualizer.selection[0]});
    } else {
      this.setState({selectedElement: null});
    }
  };

  modelChanged = () => {
    this.setState({modified: true});
  };

  getModifiedCode () {
    return pipeline.generate(this.workflow);
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

  onFullScreenChanged () {
    if (this.wdlVisualizer) {
      this.wdlVisualizer.zoom.fitToPage();
    }
  };

  zoomIn () {
    if (this.wdlVisualizer) {
      this.wdlVisualizer.zoom.zoomIn();
    }
  }

  zoomOut () {
    if (this.wdlVisualizer) {
      this.wdlVisualizer.zoom.zoomOut();
    }
  }

  @observable _mainFileRequest;

  async revertChanges () {
    if (this._mainFileRequest) {
      const script = atob(this._mainFileRequest.response);
      await this.applyCode(script);
      this.setState({modified: false, selectedElement: null});
    }
  }

  openAddTaskDialog = () => {
    this.setState({addTaskDialogVisible: true});
  };

  closeAddTaskDialog = (modified) => {
    if (modified === undefined) {
      this.setState({addTaskDialogVisible: false});
    } else {
      this.setState({addTaskDialogVisible: false, modified});
    }
  };

  openAddScatterDialog = () => {
    this.setState({addScatterDialogVisible: true});
  };

  closeAddScatterDialog = (modified) => {
    if (modified === undefined) {
      this.setState({addScatterDialogVisible: false});
    } else {
      this.setState({addScatterDialogVisible: false, modified});
    }
  };

  getSelectedElementWorkflow = () => {
    return this.state.selectedElement.step.parent || this.workflow;
  };

  confirmDeleteTask = () => {
    if (this.state.selectedElement && this.getSelectedElementWorkflow()) {
      const deleteTask = () => {
        this.getSelectedElementWorkflow().remove(this.state.selectedElement.step.name);
        this.setState({modified: true, selectedElement: null});
      };
      Modal.confirm({
        title: `Are you sure you want to delete ${this.state.selectedElement.step.name}?`,
        style: {
          wordWrap: 'break-word'
        },
        onOk () {
          deleteTask();
        }
      });
    }
  };

  openEditTaskDialog = () => {
    let task;
    if (this.state.selectedElement && this.state.selectedElement.step) {
      task = {
        alias: this.state.selectedElement.step.name,
        type: this.state.selectedElement.step.type || 'task'
      };
      if (this.state.selectedElement.step.action) {
        task.name = this.state.selectedElement.step.action.name;
        task.command = this.state.selectedElement.step.action.data
          ? this.state.selectedElement.step.action.data.command : undefined;
        task.runtime = this.state.selectedElement.step.action.data
          ? this.state.selectedElement.step.action.data.runtime : undefined;
        task.inputs = this.state.selectedElement.step.action.i;
        task.outputs = this.state.selectedElement.step.action.o;
      }
    }
    this.setState({editTaskDialogVisible: true, editableTask: task});
  };

  closeEditTaskDialog = (modified) => {
    if (modified === undefined) {
      this.setState({editTaskDialogVisible: false, editableTask: null});
    } else {
      this.setState({editTaskDialogVisible: false, editableTask: null, modified});
    }
  };

  callCountMap;

  getCallSuffix (desired) {
    desired = desired.toLowerCase();
    if (!this.callCountMap) {
      this.callCountMap = {};
      for (let child in this.workflow.children) {
        if (this.workflow.children.hasOwnProperty(child)) {
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
    const added = variables.filter(v => v.added);
    const removed = variables.filter(v => v.removed);
    const edited = variables.filter(v => v.edited);
    const addVariables = added.reduce((result, addedVar) => {
      if (!result[type]) {
        result[type] = {};
      }
      result[type][addedVar.name] = {
        type: addedVar.type
      };
      if (addedVar.value && addedVar.value.length) {
        result[type][addedVar.name].default = addedVar.value;
      }
      return result;
    }, {});
    edited.forEach(editedVar => {
      const name = editedVar.originalName || editedVar.name;
      if (editedVar.value && editedVar.value.length) {
        action[type][name].default = editedVar.value;
      } else {
        delete action[type][name].default;
      }
      action[type][name].type = editedVar.type || '';
      if (name !== editedVar.name) {
        if (type === 'i') {
          action.renameIPort(name, editedVar.name);
        } else if (type === 'o') {
          action.renameOPort(name, editedVar.name);
        }
      }
    });
    const removeVariables = removed.reduce((result, removedVar) => {
      if (!result[type]) {
        result[type] = [];
      }
      result[type].push(removedVar.originalName || removedVar.name);
      return result;
    }, {});
    workflow.actions[action.name].addPorts(addVariables);
    workflow.actions[action.name].removePorts(removeVariables);
    return added.length > 0 || removed.length > 0 || edited.length > 0;
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

  editTask = async (task) => {
    let inputVariablesChanged = false;
    let outputVariablesChanged = false;
    if (this.workflow && this.state.selectedElement && this.state.selectedElement.step.action) {
      const code = pipeline.generate(this.workflow);
      this.workflow.actions[this.state.selectedElement.step.action.name].data.command = task.command;
      this.workflow.actions[this.state.selectedElement.step.action.name].data.runtime = task.runtime;
      let child = this.getSelectedElementWorkflow().children[this.state.selectedElement.step.name];
      if (!child) {
        child = this.getSelectedElementWorkflow();
      }
      if (!this.isScatter) {
        child.rename(task.alias);
      }
      inputVariablesChanged = this.processTaskVariables(task.inputs, 'i', this.workflow, this.state.selectedElement.step.action);
      outputVariablesChanged = this.processTaskVariables(task.outputs, 'o', this.workflow, this.state.selectedElement.step.action);
      try {
        const parseResult = await pipeline.parse(pipeline.generate(this.workflow));
        if (!parseResult.status) {
          await this.applyCode(code);
          message.error('Error in wdl code');
          return;
        }
      } catch (error) {
        await this.applyCode(code);
        message.error(error.message);
        return;
      }
    }
    this.closeEditTaskDialog(true);
    if ((inputVariablesChanged || outputVariablesChanged) && this.state.selectedElement.step.type === 'workflow') {
      this.editConfig();
    }
  };

  addTask = async (task) => {
    const code = pipeline.generate(this.workflow);
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
        if (v.value && v.value.length) {
          result[v.name].default = v.value;
        }
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
    if (this.state.selectedElement) {
      this.state.selectedElement.step.add(newCall);
    } else {
      this.workflow.add(newCall);
      this.clearWrongPorts(this.workflow);
    }
    try {
      const parseResult = await pipeline.parse(pipeline.generate(this.workflow));
      if (!parseResult.status) {
        await this.applyCode(code);
        message.error('Error in wdl code');
        return;
      }
    } catch (error) {
      await this.applyCode(code);
      message.error(error.message);
      return;
    }
    this.wdlVisualizer.attachTo(this.workflow);
    this.closeAddTaskDialog(true);
  };

  addScatter = async (task) => {
    const code = pipeline.generate(this.workflow);
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
        await this.applyCode(code);
        message.error('Error in wdl code');
        return;
      }
    } catch (error) {
      await this.applyCode(code);
      message.error(error.message);
      return;
    }
    this.wdlVisualizer.attachTo(this.workflow);
    this.closeAddScatterDialog(true);
  };

  get isScatter () {
    return this.state.selectedElement &&
      this.state.selectedElement.step &&
      this.state.selectedElement.step.type === 'scatter';
  }

  get isWorkflow () {
    return !this.state.selectedElement ||
      (
        this.state.selectedElement.step &&
        this.state.selectedElement.step.type === 'workflow'
      );
  }

  get isTask () {
    return this.state.selectedElement &&
      this.state.selectedElement.step &&
      (!this.state.selectedElement.step.type || this.state.selectedElement.step.type === 'task');
  }

  renderItemActions = () => {
    if (!this.canModifySources) {
      return undefined;
    }
    if (this.isWorkflow) {
      return [
        this.state.selectedElement
          ? (
            <Button id="wdl-graph-workflow-edit-button" key="edit" size="small" onClick={this.openEditTaskDialog}>
              <Icon type="edit" /> EDIT WORKFLOW
            </Button>)
        : undefined,
        <Button id="wdl-graph-workflow-add-scatter-button" key="add scatter" size="small" onClick={this.openAddScatterDialog}>
          <Icon type="plus" /> ADD SCATTER
        </Button>,
        <Button id="wdl-graph-workflow-add-task-button" key="add task" size="small" onClick={this.openAddTaskDialog}>
          <Icon type="plus" /> ADD TASK
        </Button>
      ];
    } else if (this.isScatter) {
      let childrenCount = 0;
      for (let child in this.state.selectedElement.step.children) {
        if (this.state.selectedElement.step.children.hasOwnProperty(child)) {
          childrenCount += 1;
        }
      }
      return [
        <Button id="wdl-graph-scatter-delete-button" key="remove" size="small" type="danger" onClick={this.confirmDeleteTask}>
          <Icon type="delete" /> DELETE <b>{
          this.state.selectedElement.step && this.state.selectedElement.step.type
            ? this.state.selectedElement.step.type.toUpperCase() : undefined
        }</b>
        </Button>,
        <Button id="wdl-graph-scatter-edit-button" key="add" size="small" onClick={this.openEditTaskDialog}>
          <Icon type="edit" /> EDIT <b>{
          this.state.selectedElement.step && this.state.selectedElement.step.type
            ? this.state.selectedElement.step.type.toUpperCase() : undefined
        }</b>
        </Button>,
        childrenCount === 0
          ?
          (
            <Button id="wdl-graph-scatter-add-task-button" key="add task" size="small" onClick={this.openAddTaskDialog}>
              <Icon type="plus" /> ADD TASK
            </Button>
          )
          : undefined
      ];
    } else if (this.isTask) {
      return [
        <Button id="wdl-graph-task-delete-button" key="remove" size="small" type="danger" onClick={this.confirmDeleteTask}>
          <Icon type="delete" /> DELETE <b>{
          this.state.selectedElement.step && this.state.selectedElement.step.name
            ? this.state.selectedElement.step.name.toUpperCase() : undefined
        }</b>
        </Button>,
        <Button id="wdl-graph-task-edit-button" key="add" size="small" onClick={this.openEditTaskDialog}>
          <Icon type="edit" /> EDIT <b>{
          this.state.selectedElement.step && this.state.selectedElement.step.name
            ? this.state.selectedElement.step.name.toUpperCase() : undefined
        }</b>
        </Button>
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
    this.wdlVisualizer && this.wdlVisualizer.zoom.fitToPage();
  };

  layoutGraph = () => {
    this.wdlVisualizer && this.wdlVisualizer.layout();
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
      return <Alert type="warning" message="Error parsing wdl script" />;
    }
    return (
      <div className={styles.wdlGraph}>
        <Row className={styles.wdlGraphToolbar} >
          <div className={styles.background} />
          <Row>
            <Col span={12}>
              {
                this.canModifySources
                ? (
                  <Button
                    id="wdl-graph-save-button"
                    disabled={!this.state.modified}
                    type="primary"
                    size="small"
                    onClick={this.openCommitFormDialog}><Icon type="save"/>SAVE</Button>
                ) : undefined
              }
              {
                this.canModifySources
                  ? (
                    <Button
                      id="wdl-graph-revert-button"
                      disabled={!this.state.modified}
                      size="small"
                      onClick={() => this.revertChanges()}><Icon type="close"/>REVERT</Button>
                ) : undefined
              }
              <Button id="wdl-graph-layout-button" size="small" onClick={this.layoutGraph}>
                <Icon type="appstore-o" />LAYOUT
              </Button>
              <Button id="wdl-graph-fit-button" size="small" onClick={this.fitGraph}>
                <Icon type="scan" />FIT
              </Button>
              <Button
                id={`wdl-graph-${this.state.showAllLinks ? 'hide-links' : 'show-links'}-button`}
                size="small"
                onClick={this.toggleLinks}><Icon type="swap" />{
                this.state.showAllLinks ? 'HIDE LINKS' : 'SHOW LINKS'
              }</Button>
            </Col>
            <Col span={12} style={{textAlign: 'right'}}>
              {this.renderItemActions()}
            </Col>
          </Row>
        </Row>
        <div className={styles.wdlGraphContainer} >
          <div ref={this.initializeContainer} />
        </div>
        <EditWDLToolForm
          visible={this.state.editTaskDialogVisible}
          task={this.state.editableTask}
          type={this.state.editableTask ? this.state.editableTask.type : undefined}
          pending={false}
          onCancel={() => this.closeEditTaskDialog()}
          onSubmit={this.editTask} />
        <EditWDLToolForm
          type="task"
          visible={this.state.addTaskDialogVisible}
          pending={false}
          onCancel={this.closeAddTaskDialog}
          onSubmit={this.addTask} />
        <EditWDLToolForm
          type="scatter"
          visible={this.state.addScatterDialogVisible}
          pending={false}
          onCancel={this.closeAddScatterDialog}
          onSubmit={this.addScatter} />
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

  async afterSaveChanges () {
    this._mainFileRequest = null;
  }

  componentWillUnmount () {
    this._mainFileRequest = null;
  }

}
