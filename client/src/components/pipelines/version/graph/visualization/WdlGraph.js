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
import classNames from 'classnames';
import Graph from './Graph';
import {inject, observer} from 'mobx-react';
import {observable} from 'mobx';
import VersionFile from '../../../../../models/pipelines/VersionFile';
import AllowedInstanceTypes from '../../../../../models/utils/AllowedInstanceTypes';
import {
  Project,
  ContextTypes,
  WdlEvent,
  Visualizer,
  VisualizerEvent,
  isConditional,
  isScatter,
  isWorkflow
} from '../../../../../utils/pipeline-builder';
import styles from './Graph.css';
import LoadingView from '../../../../special/LoadingView';
import {
  ResizablePanel,
  ResizeAnchors
} from '../../../../special/resizablePanel';
import {
  Alert,
  AutoComplete,
  Row,
  Button,
  Icon,
  message,
  Modal,
  Popover,
  Tooltip
} from 'antd';
import WdlPropertiesForm from './forms/wdl-properties-form';
import {
  generatePipelineCommand,
  Primitives,
  testPrimitiveTypeFn
} from './forms/utilities';
import {ItemTypes} from '../../../model/treeStructureFunctions';
import {
  addCall, addTask,
  getEntityNameOptions,
  serializeWorkflowParameters,
  workflowParametersEquals
} from './forms/utilities/workflow-utilities';
import WdlExecutables from './forms/form-items/wdl-executables';
import WdlDocumentProperties from './forms/form-items/wdl-document-properties';
import {clearQuotes} from './forms/utilities/string-utilities';
import buildWdlContentsResolver from './utilities/wdl-contents-resolver';
import getPipelineFilePath from './utilities/get-pipeline-file-path';

const graphFitContentOpts = {
  padding: 24,
  horizontalAlign: 'middle',
  verticalAlign: 'middle'
};

const nameReplacementRegExp = /[-\s.,:;'"!@#$%^&*()[\]{}/\\~`±§]/g;

const graphSelectableTypes = [
  ContextTypes.workflow,
  ContextTypes.call,
  ContextTypes.scatter,
  ContextTypes.conditional
];

function reportWDLError (error) {
  const m = error.message || error;
  if (m && typeof m === 'string') {
    message.error(m, 5);
  }
  console.error('WDL ERROR:', m);
}

@inject('runDefaultParameters')
@inject(({history, routing, pipelines}, params) => ({
  history,
  routing,
  parameters: pipelines.getVersionParameters(params.pipelineId, params.version),
  pipeline: pipelines.getPipeline(params.pipelineId),
  pipelineId: params.pipelineId,
  pipelineVersion: params.version,
  pipelines,
  allowedInstanceTypes: new AllowedInstanceTypes()
}))
@observer
export default class WdlGraph extends Graph {
  wdlVisualizer;
  wdlDocument;
  wdlProject;
  workflow;
  workflowParameters;
  @observable previousSuccessfulCode;

  initializeContainer = async (container) => {
    if (container && !this.wdlVisualizer) {
      const {
        script
      } = this.state;
      this.wdlVisualizer = new Visualizer({
        element: container,
        displayWorkflowConnections: this.state.showAllLinks,
        connectionsOnTop: false,
        drop: {
          allowEvent: (event) => event.dataTransfer.types
            .map((t) => t.toLowerCase())
            .includes('dropdatakey'),
          onDrop: this.onDropModel.bind(this)
        }
      });
      this.wdlVisualizer.addEventListener(
        VisualizerEvent.selectionChanged,
        (e, s, item) => this.onSelectItem(item)
      );
      await this.applyCode(script, true);
    } else {
      this.wdlVisualizer = null;
    }
  };

  onDropModel = async (event, parents = []) => {
    const receiver = parents.find((o) => isScatter(o) ||
      isWorkflow(o) ||
      isConditional(o)
    ) || this.workflow;
    const parts = (event.dataTransfer.getData('dropDataKey') || '').split('_');
    if (receiver && parts.length >= 2 && parts[0] === ItemTypes.pipeline) {
      const pipelineId = parts[1];
      if (+pipelineId === +this.props.pipelineId) {
        message.error('You cannot use the same pipeline');
        return;
      }
      let pipelineVersion = parts.slice(2).join('_');
      const hide = message.loading('Fetching pipeline info...', 0);
      const pipelineRequest = this.props.pipelines.getPipeline(pipelineId);
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
          receiver,
          pipelineRequest.value,
          pipelineVersion,
          versionRequest.value
        );
      }
    }
  };

  dropPipeline = async (target, pipelineInfo, version, configurations) => {
    // todo:
    const [defaultConfiguration] = (configurations || []).filter(c => c.default && c.configuration);
    await this.props.runDefaultParameters.fetchIfNeededOrWait();
    const systemParameters = this.props.runDefaultParameters.loaded
      ? (this.props.runDefaultParameters.value || []).map(p => p.name)
      : [];
    const skipParameterFn = (name) => systemParameters.indexOf(name) >= 0;
    const inputs = [];
    const declarations = [];
    const outputs = [];
    const addInputParameter = (name, p) => {
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
      const nameCorrected = name.replace(nameReplacementRegExp, '_');
      // todo:
      if (p.value) {
        const expr = testPrimitiveTypeFn(Primitives.string, type) ||
          testPrimitiveTypeFn(Primitives.file, type)
          ? `"${clearQuotes(p.value)}"`
          : p.value;
        inputs.push({
          name: nameCorrected,
          type: type,
          expression: expr
        });
      } else {
        // input
        inputs.push({
          name: nameCorrected,
          type
        });
      }
    };
    const addOutputParameter = (name, p) => {
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
      const nameCorrected = name.replace(nameReplacementRegExp, '_');
      const outputName = `${pipelineName}_${name}`.replace(nameReplacementRegExp, '_');
      inputs.push({
        name: nameCorrected,
        type
      });
      outputs.push({
        name: outputName,
        type,
        value: `$${name},`
      });
    };
    const pipelineName = pipelineInfo.name.replace(nameReplacementRegExp, '_');
    Object.keys(defaultConfiguration.configuration.parameters || []).forEach(key => {
      if (defaultConfiguration.configuration.parameters[key].type !== 'output') {
        addInputParameter(key, defaultConfiguration.configuration.parameters[key]);
      } else {
        addOutputParameter(key, defaultConfiguration.configuration.parameters[key]);
      }
    });
    const task = {
      name: pipelineName,
      command: {
        command: generatePipelineCommand(
          pipelineInfo.name, version,
          inputs.filter(p => !!p).map(p => p.name)
        )
      },
      inputs,
      declarations,
      outputs,
      runtime: {
        pipeline: `"${pipelineInfo.name}@${version}"`
      }
    };
    if (defaultConfiguration) {
      Modal.confirm({
        title: `Add pipeline '${pipelineInfo.name}' as a task to ${target.type}?`,
        style: {
          wordWrap: 'break-word'
        },
        onOk: () => this.addCall(task, target),
        okText: 'ADD',
        cancelText: 'CANCEL'
      });
    }
  };

  generateWdl () {
    if (this.wdlDocument) {
      const {
        success,
        error,
        content
      } = (this.wdlProject || Project.default).generateWdl(this.wdlDocument);
      if (error) {
        console.log('Error in generated code:', error.message);
        if (content) {
          console.log(content);
        }
        throw new Error(error);
      }
      if (!success) {
        console.log('Error generating code. Content:');
        if (content) {
          console.log(content);
        }
        throw new Error('Error generating wdl content');
      }
      return content;
    }
    throw new Error('WDL Document is missing');
  }

  applyCode = async (code, clearModifiedConfig) => {
    const hide = message.loading('Loading...');
    const onError = (message) => {
      if (clearModifiedConfig) {
        this.setState({
          selectedElement: null,
          error: message,
          modifiedParameters: null
        });
      } else {
        this.setState({
          selectedElement: null,
          error: message
        });
      }
    };
    try {
      const {
        uri
      } = this.state;
      this.wdlProject = new Project({
        baseURI: uri,
        contentsResolver: buildWdlContentsResolver(
          this.props.pipelineId,
          this.props.pipelineVersion
        )
      });
      const wdlDocument = await this.wdlProject.loadDocumentByContents(code);
      if (wdlDocument) {
        if (this.wdlDocument) {
          this.wdlDocument.off(WdlEvent.changed, this.modelChanged, this);
        }
        this.wdlDocument = wdlDocument;
        this.wdlDocument.selectedAction = undefined;
        this.wdlDocument.on(WdlEvent.changed, this.modelChanged, this);
        this.workflow = wdlDocument.requireWorkflow();
        try {
          this.previousSuccessfulCode = this.generateWdl() || code;
        } catch (e) {
          console.warn(e);
          this.previousSuccessfulCode = code;
        }
        this.workflowParameters = serializeWorkflowParameters(this.workflow);
        this.wdlVisualizer.attachTo(wdlDocument);
        this.wdlVisualizer.readOnly = !this.canModifySources;
        this.updateData();
        if (clearModifiedConfig) {
          this.setState({
            selectedElement: null,
            canZoomIn: true,
            canZoomOut: true,
            error: null,
            modifiedConfig: null
          });
        } else {
          this.setState({
            selectedElement: null,
            canZoomIn: true,
            canZoomOut: true,
            error: null
          });
        }
        this.onFullScreenChanged();
      } else {
        // onError(parseResult.message);
      }
    } catch (e) {
      console.log(e);
      onError(e);
    } finally {
      hide();
    }
  };

  updateData () {
    // todo
    let selectedTaskName;
    if (this.props.selectedTaskId) {
      [selectedTaskName] = this.props.selectedTaskId.split('?');
    }
    if (this.wdlVisualizer) {
      this.wdlVisualizer.selectedAction = undefined;
    }
    this.wdlVisualizer && this.wdlVisualizer.getElements().forEach((e) => {
      const view = this.wdlVisualizer.paper.findViewByModel(e);
      if (selectedTaskName && e.action && e.action.reference === selectedTaskName &&
        graphSelectableTypes.includes(e.action.contextType)) {
        this.wdlVisualizer.selectedAction = e.action;
      }
      if (view && view.el && e.action && e.action.contextType !== ContextTypes.workflow) {
        if (!view.el.classList.contains(styles.wdlTask)) {
          view.el.classList.add(styles.wdlTask);
        }
        if (!view.el.classList.contains('cp-wdl-task')) {
          view.el.classList.add('cp-wdl-task');
        }
        if (!view.el.dataset) {
          view.el.dataset = {};
        }
        let status;
        if (this.props.getNodeInfo) {
          const info = this.props.getNodeInfo({
            task: {name: e.action.reference}
          });
          if (info) {
            status = info.status;
          }
        }
        if (
          e.action &&
          e.action.executable &&
          e.action.executable.contextType === ContextTypes.task &&
          typeof e.action.executable.getRuntime === 'function' &&
          !!e.action.executable.getRuntime('pipeline')
        ) {
          if (!view.el.classList.contains(styles.wdlPipelineTask)) {
            view.el.classList.add(styles.wdlPipelineTask);
          }
        }
        if (status) {
          view.el.dataset['taskstatus'] = status.toLowerCase();
        } else {
          delete view.el.dataset['taskstatus'];
        }
      }
    });
  }

  unsavedChangesConfirm = (onOk, onCancel) => {
    if (!this.props.canEdit) {
      onOk();
      return null;
    }
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

  onSelectItem = (selected, fit = false) => {
    if (selected === this.state.selectedElement) {
      return;
    }
    this.setState({
      selectedElement: selected
    }, () => {
      if (this.wdlVisualizer) {
        this.wdlVisualizer.selectedAction = this.state.selectedElement;
        if (fit) {
          this.wdlVisualizer.zoom.fitTo(this.state.selectedElement);
        }
      }
    });
  };

  modelChanged = () => {
    let modified = false;
    let wdlError;
    if (this.wdlDocument) {
      try {
        modified = this.previousSuccessfulCode !== this.generateWdl();
      } catch (e) {
        modified = false;
        wdlError = e.message;
        console.warn(wdlError);
      }
      const params = serializeWorkflowParameters(this.workflow);
      this.setState({
        modified,
        modifiedParameters: modified && !workflowParametersEquals(this.workflowParameters, params)
          ? params
          : undefined,
        wdlError
      });
    } else {
      this.setState({
        modified,
        modifiedParameters: undefined,
        wdlError
      });
    }
  };

  getModifiedCode () {
    try {
      if (this.wdlDocument) {
        return this.generateWdl();
      }
      return this.previousSuccessfulCode;
    } catch (___) {
      console.warn(___.message);
      return this.previousSuccessfulCode;
    }
  }

  getModifiedParameters () {
    return this.state.modifiedParameters;
  }

  getFilePath () {
    return this.state.uri;
  }

  draw () {
    this.onFullScreenChanged();
  }

  onFullScreenChanged = () => {
    if (this.wdlVisualizer && this.wdlVisualizer.paper.el) {
      const parent = this.wdlVisualizer.paper.el.parentElement;
      if (parent) {
        this.wdlVisualizer.paper.setDimensions(parent.offsetWidth, parent.offsetHeight);
      }
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

  async revertChanges () {
    const {script} = this.state;
    await this.applyCode(script);
    this.setState({
      modified: false,
      wdlError: undefined,
      modifiedParameters: undefined
    });
  }

  addCall = (task, parent = this.workflow) => {
    let result;
    try {
      const aTask = addTask(this.wdlDocument, task);
      result = addCall(parent, aTask);
    } catch (error) {
      console.log(error);
      reportWDLError(error);
      result = undefined;
    } finally {
      this.onSelectItem(result, true);
    }
  };

  toggleLinks = () => {
    const showAllLinks = !this.state.showAllLinks;
    this.setState({showAllLinks}, () => {
      if (this.wdlVisualizer) {
        this.wdlVisualizer.displayWorkflowConnections = showAllLinks;
      }
    });
  };

  fitGraph = () => {
    this.wdlVisualizer && this.wdlVisualizer.zoom.fitToPage(graphFitContentOpts);
  };

  layoutGraph = () => {
    this.wdlVisualizer && this.wdlVisualizer.layout() && this.fitGraph();
  };

  togglePropertiesSidePanelState = () => {
    this.setState({
      propertiesPanelVisible: !this.state.propertiesPanelVisible
    });
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
          className={styles.propertiesButton}
          onClick={this.togglePropertiesSidePanelState}
        >
          PROPERTIES
        </Button>
      </div>
    );
  };

  renderSidePanelHeader = (title, onPanelClose) => {
    return (
      <Row
        type="flex"
        justify="space-between"
        align="middle"
        className={
          classNames(
            'cp-divider',
            'top',
            'bottom',
            'cp-content-panel-header'
          )
        }
        style={{
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

  renderPropertiesPanel = () => {
    if (!this.state.propertiesPanelVisible) {
      return null;
    }

    const {
      selectedElement
    } = this.state;

    const panelHeader = this.renderSidePanelHeader(
      'Properties',
      this.closePropertiesSidePanel
    );

    return (
      <ResizablePanel
        className={
          classNames(
            styles.wdlGraphSidePanel,
            styles.right,
            'cp-content-panel'
          )
        }
        resizeAnchors={[ResizeAnchors.left]}
      >
        {panelHeader}
        <div className={styles.wdlGraphSidePanelContentContainer}>
          {
            !selectedElement && (
              <WdlDocumentProperties
                document={this.wdlDocument}
              />
            )
          }
          {
            !selectedElement && (
              <WdlExecutables
                document={this.wdlDocument}
                disabled={!this.canModifySources}
                onSelect={this.onSelectItem}
              />
            )
          }
          {
            selectedElement && (
              <WdlPropertiesForm
                style={{
                  paddingRight: 5,
                  flex: 1,
                  width: '100%',
                  display: 'flex'
                }}
                entity={selectedElement}
                wdlDocument={this.wdlDocument}
                allowedInstanceTypes={this.props.allowedInstanceTypes}
                disabled={!this.canModifySources}
                onRemoved={() => this.onSelectItem(undefined)}
                onActionAdded={(action) => this.onSelectItem(action, true)}
              />
            )
          }
        </div>
      </ResizablePanel>
    );
  };

  handleSearchControlVisible = (searchControlVisible) => {
    const handleChange = () => {
      this.setState({searchControlVisible, tooltipVisible: false}, () => {
        if (!searchControlVisible) {
          this.clearSearch();
        }
      });
    };
    if (!searchControlVisible) {
      setTimeout(handleChange, 300);
    } else {
      handleChange();
    }
  };

  clearSearch = () => this.setState({
    search: undefined,
    searchResults: []
  });

  renderGraphSearch = () => {
    const {
      search,
      searchResults = []
    } = this.state;
    const renderOption = (element) => {
      const {
        type,
        name
      } = getEntityNameOptions(element, this.wdlDocument);
      let description;
      if (isScatter(element) && element.iterator && element.iterator.value) {
        description = `${element.iterator.name} in ${element.iterator.value}`;
      } else if (isScatter(element) && element.iterator) {
        description = `iterator ${element.iterator.name}`;
      } else if (isConditional(element) && element.expression) {
        description = `if (${element.expression})`;
      }
      return (
        <AutoComplete.Option
          key={element.uuid}
          value={element.uuid}
          text={element.toString()}
        >
          <div className={styles.searchItem}>
            <div className={styles.searchItemRow}>
              <span
                className={styles.entityType}
              >
                {type}
              </span>
              <span
                className={styles.entityName}
              >
                {name}
              </span>
            </div>
            {
              description && (
                <div className={styles.searchItemDescriptionRow}>
                  {description}
                </div>
              )
            }
          </div>
        </AutoComplete.Option>
      );
    };
    const onSelect = (uuid) => {
      const entity = searchResults.find((o) => o.uuid === uuid);
      this.setState({
        searchResults: [],
        search: undefined,
        searchControlVisible: false
      }, () => this.onSelectItem(entity, true));
    };
    const onSearch = (s) => {
      if (s && s.length > 2 && this.workflow) {
        const results = this.workflow.find(
          {
            search: s,
            caseInsensitive: true
          },
          ContextTypes.workflow,
          ContextTypes.call,
          ContextTypes.scatter,
          ContextTypes.conditional
        );
        this.setState({
          search: s,
          searchResults: results
        });
      } else {
        this.setState({
          search: s,
          searchResults: []
        });
      }
    };
    const searchControl = (
      <div>
        <Row>
          <AutoComplete
            value={search}
            onSearch={onSearch}
            onSelect={onSelect}
            placeholder="Element type or name..."
            style={{minWidth: 300}}
            optionLabelProp="text"
          >
            {
              searchResults.map(renderOption)
            }
          </AutoComplete>
        </Row>
        {
          search && searchResults.length === 0 && (
            <Row
              className="cp-text-not-important"
              type="flex"
              justify="space-around"
              style={{marginTop: 5}}
            >
              <i>Elements not found</i>
            </Row>
          )
        }
      </div>
    );
    const onTooltipVisibleChange = (visible) => {
      this.setState({
        tooltipVisible: visible
      });
    };
    return (
      <Tooltip
        title="Search element"
        onVisibleChange={onTooltipVisibleChange}
        visible={this.state.tooltipVisible}
        placement="right">
        <Popover
          content={searchControl}
          placement="rightTop"
          trigger="click"
          onVisibleChange={this.handleSearchControlVisible}
          visible={this.state.searchControlVisible}
        >
          <Button
            id="wdl-graph-search-button"
            className={styles.wdlAppearanceButton}
            shape="circle"
          >
            <Icon type="search" />
          </Button>
        </Popover>
      </Tooltip>
    );
  };

  renderAppearancePanel = () => (
    <div
      className={
        classNames(
          styles.wdlGraphSidePanel,
          styles.left,
          'cp-pipeline-graph-side-panel'
        )
      }
    >
      {
        this.canModifySources &&
        <Tooltip title="Save" placement="right">
          <Button
            id="wdl-graph-save-button"
            className={
              classNames(
                styles.wdlAppearanceButton,
                styles.noFade
              )
            }
            disabled={!this.state.modified || this.state.wdlError}
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
            className={
              classNames(
                styles.wdlAppearanceButton,
                styles.noFade
              )
            }
            disabled={!this.state.modified}
            shape="circle"
            onClick={() => this.revertChanges()}>
            <Icon type="reload" />
          </Button>
        </Tooltip>
      }
      {
        this.canModifySources &&
        <div
          className={
            classNames(
              styles.separator,
              'cp-divider',
              'horizontal'
            )
          }
        >
          {'\u00A0'}
        </div>
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
          className={styles.wdlAppearanceButton}
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
      {
        this.renderGraphSearch()
      }
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

  renderGraph () {
    const {
      loaded,
      error,
      loadError
    } = this.state;
    if (!loaded) {
      return <LoadingView />;
    }
    if (loadError) {
      return <Alert type="warning" message={loadError} />;
    }
    if (error) {
      const errorText = error.message || error;
      const errorContent = (
        <Row>
          <Row>Error parsing wdl script:</Row>
          <Row>{errorText}</Row>
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
        {this.props.canEdit && this.renderSidePanelsControlButtons()}
        {this.props.canEdit && this.renderPropertiesPanel()}
        {this.renderAppearancePanel()}
        <div
          className={
            classNames(
              styles.wdlGraphContainer,
              'cp-wdl-visualizer'
            )
          }
        >
          <div ref={this.initializeContainer} />
        </div>
      </div>
    );
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.pipelineId !== this.props.pipelineId ||
      prevProps.pipelineVersion !== this.props.pipelineVersion
    ) {
      console.log('pipeline id or version changed');
      this.loadMainFile();
    }
    super.componentDidUpdate(prevProps, prevState, snapshot);
  }

  renderBottomGraphControls = () => {
    return null;
  };

  async afterCommit () {
    return new Promise(resolve => {
      this.setState({
        modified: false,
        wdlError: undefined
      }, () => resolve());
    });
  }

  _removeRouterListener = null;
  _routeChangeConfirm = null;

  componentDidMount () {
    this.loadMainFile();
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
    this.props.onGraphReady && this.props.onGraphReady(this);
  }

  componentWillUnmount () {
    this._loadMainFileToken = {};
    if (this._removeRouterListener) {
      this._removeRouterListener();
    }
  }

  loadMainFile = () => {
    const {
      parameters,
      pipeline,
      pipelineId,
      pipelineVersion
    } = this.props;
    this._loadMainFileToken = {};
    const token = this._loadMainFileToken;
    const commit = (state) => {
      if (token === this._loadMainFileToken) {
        this.setState(state);
      }
    };
    this.setState({
      loaded: false,
      loadError: undefined,
      error: undefined,
      script: undefined,
      uri: undefined
    }, async () => {
      const state = {loaded: true};
      try {
        await Promise.all([
          parameters.fetchIfNeededOrWait(),
          pipeline.fetchIfNeededOrWait()
        ]);
        if (pipeline.error) {
          throw new Error(pipeline.error);
        }
        if (parameters.error) {
          throw new Error(parameters.error);
        }
        const {
          codePath: pipelineCodePath = ''
        } = pipeline.value || {};
        const {
          main_file: mainFile = ''
        } = parameters.value || {};
        const mainFilePath = (() => {
          const extension = mainFile.split('.').pop().toLowerCase();
          if (extension === 'wdl') {
            return getPipelineFilePath(mainFile, pipelineCodePath);
          }
          return getPipelineFilePath(`${mainFile}.wdl`, pipelineCodePath);
        })();
        const request = new VersionFile(
          pipelineId,
          mainFilePath,
          pipelineVersion
        );
        await request.fetch();
        if (request.error) {
          throw new Error(request.error);
        }
        state.script = atob(request.response);
        state.uri = mainFilePath;
      } catch (error) {
        state.loadError = error.message;
      } finally {
        commit(state);
      }
    });
  };
}
