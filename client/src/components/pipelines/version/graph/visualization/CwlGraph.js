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
import {observable} from 'mobx';
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
import {Alert} from 'antd';
import Graph from './Graph';
import VersionFile from '../../../../../models/pipelines/VersionFile';
import LoadingView from '../../../../special/LoadingView';
import styles from './Graph.css';
import 'cwl-svg/src/assets/styles/theme.scss';
import './cwlStyle.scss';

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

  componentDidMount () {
    this.loadFile();
  }

  componentDidUpdate () {
    this.loadFile();
    super.componentDidUpdate();
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

  initializeGraph = async () => {
    try {
      if (this._fileRequest) {
        const response = yaml.load(atob(this._fileRequest.response));
        const workflowModel = this.initializeModel(response);
        this.cwlWorkflow = new Workflow({
          svgRoot: document.getElementById('cwl-workflow'),
          model: workflowModel,
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
          editingEnabled: false
        });
        this.cwlWorkflow.fitToViewport();
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

  renderGraph () {
    const {parameters, pipeline} = this.props;
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
        <div className={styles.cwlGraphContainer} ref={this.initializeGraph}>
          <svg
            id="cwl-workflow"
            className="cwl-workflow"
          />
        </div>
      </div>
    );
  }
}
