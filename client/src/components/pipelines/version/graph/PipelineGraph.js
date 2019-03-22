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

import React, {Component} from 'react';
import {inject, observer} from 'mobx-react';
import {Row} from 'antd';
import parentStyles from '../PipelineDetails.css';
import WorkflowGraph from './WorkflowGraph';
import PipelineConfigurations from '../../../../models/pipelines/PipelineConfigurations';

@inject(({pipelines}, {params}) => ({
  pipelineId: params.id,
  version: params.version,
  pipeline: pipelines.getPipeline(params.id),
  configurations: new PipelineConfigurations(params.id, params.version)
}))
@observer
export default class PipelineGraph extends Component {

  onGraphUpdated = async () => {
    await this.props.pipeline.fetch();
    return this.props.pipeline.value;
  };

  render () {
    return (
      <div
        className={parentStyles.fullHeightContainer}>
        <Row className={parentStyles.fullHeightContainer}>
          <WorkflowGraph
            canEdit={true}
            onGraphUpdated={this.onGraphUpdated}
            pipelineId={this.props.pipelineId}
            configurations={this.props.configurations}
            version={this.props.version} />
        </Row>
      </div>
    );
  }
}
