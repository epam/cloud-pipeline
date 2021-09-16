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
import {withRouter} from 'react-router-dom';
import {Row} from 'antd';
import parentStyles from '../PipelineDetails.css';
import WorkflowGraph from './WorkflowGraph';
import PipelineConfigurations from '../../../../models/pipelines/PipelineConfigurations';

@inject(({pipelines}, {match}) => ({
  pipelineId: match.params.id,
  version: match.params.version,
  pipeline: pipelines.getPipeline(match.params.id),
  configurations: new PipelineConfigurations(match.params.id, match.params.version)
}))
@observer
class PipelineGraph extends Component {
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
            canEdit
            onGraphUpdated={this.onGraphUpdated}
            pipelineId={this.props.pipelineId}
            configurations={this.props.configurations}
            version={this.props.version} />
        </Row>
      </div>
    );
  }
}

export default withRouter(PipelineGraph);
