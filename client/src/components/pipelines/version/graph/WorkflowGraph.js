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
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import LoadingView from '../../../special/LoadingView';
import localization from '../../../../utils/localization';
import {Alert, Row} from 'antd';
import {LuigiGraph, WdlGraph} from './visualization';

@localization.localizedComponent
@inject(({pipelines, routing}, params) => ({
  pipeline: pipelines.getPipeline(params.pipelineId),
  language: pipelines.getLanguage(params.pipelineId, params.version),
  routing
}))
@observer
export default class WorkflowGraph extends localization.LocalizedReactComponent {
  static propTypes = {
    pipelineId: PropTypes.oneOfType([
      PropTypes.string,
      PropTypes.number
    ]),
    version: PropTypes.string,
    selectedTaskId: PropTypes.string,
    onSelect: PropTypes.func,
    className: PropTypes.string,
    fitAllSpace: PropTypes.bool,
    onGraphReady: PropTypes.func,
    getNodeInfo: PropTypes.func,
    hideError: PropTypes.bool,
    canEdit: PropTypes.bool,
    onGraphUpdated: PropTypes.func,
    configurations: PropTypes.object
  };

  @observable _language;
  @observable graphComponent;

  @computed
  get language () {
    if (this.props.language.loaded) {
      return this.props.language.value.toLowerCase();
    }
    return 'other';
  }

  base64Image () {
    if (this._graph) {
      return this._graph.getImage();
    }
    return '';
  };

  get imageSize () {
    if (this._graph) {
      return this._graph.imageSize;
    }
    return {
      width: 1,
      height: 1
    };
  };

  updateData () {
    if (this._graph) {
      this._graph.updateData();
    }
  }

  draw () {
    if (this._graph) {
      this._graph.draw();
    }
  }

  onGraphReady = (graph) => {
    this._graph = graph;
    this.props.onGraphReady && this.props.onGraphReady(graph);
  };

  render () {
    if (this.props.language.pending && !this.props.language.loaded) {
      return <LoadingView />;
    }
    if (!this.graphComponent) {
      if (this.props.hideError) {
        return (
          <Row
            ref={() => this.props.onGraphReady && this.props.onGraphReady(null)}/>
        );
      } else {
        return (
          <Row ref={() => this.props.onGraphReady && this.props.onGraphReady(null)}>
            <Alert type="warning" message={
              <div>
                <span>Graph is not supported for current {this.localizedString('pipeline')}</span>
              </div>
            } />
          </Row>
        );
      }
    }
    const Component = this.graphComponent;
    return (
      <Component
        {...this.props}
        language={this.language}
        onGraphReady={this.onGraphReady} />
    );
  }

  componentDidUpdate () {
    this.buildComponent();
  }

  componentDidMount () {
    this.buildComponent();
  }

  buildComponent = () => {
    if (this._language !== this.language) {
      this._language = this.language;
      switch (this.language) {
        case 'luigi':
          this.graphComponent = LuigiGraph;
          break;
        case 'wdl':
          this.graphComponent = WdlGraph;
          break;
        default:
          break;
      }
    }
  }
}
