/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import PropTypes from 'prop-types';
import {Icon} from 'antd';
import {observer} from 'mobx-react';
import {CellProfilerModule, CellProfilerModuleHeader} from './module';
import AnalysisPipelineInfo from './info';
import {ObjectsOutlineRenderer} from './objects-outline';
import Collapse from './collapse';
import styles from './cell-profiler.css';

class CellProfilerPipeline extends React.Component {
  state = {
    expanded: []
  };

  get moduleIdsWithErrors () {
    const {pipeline} = this.props;
    const ids = (pipeline.parametersWithErrors || [])
      .map(parameter => parameter.cpModule?.uuid)
      .filter(Boolean);
    return [...new Set(ids)];
  }

  getExpanded = (key) => {
    const {expanded = []} = this.state;
    return expanded.includes(key);
  }

  toggleExpanded = (key) => {
    const {expandSingle} = this.props;
    const {expanded = []} = this.state;
    if (expanded.includes(key)) {
      this.setState({expanded: expanded.filter(o => o !== key)});
    } else if (expandSingle) {
      this.setState({expanded: [key]});
    } else {
      this.setState({expanded: [...expanded, key]});
    }
  };

  render () {
    const {
      pipeline,
      viewer,
      className,
      style,
      hidden
    } = this.props;
    if (!pipeline || hidden) {
      return null;
    }
    let infoHeader = (
      <b className={styles.title}>
        <Icon type="info-circle-o" /> Info
      </b>
    );
    if (pipeline.name) {
      infoHeader = (
        <b className={styles.title}>
          <Icon type="info-circle-o" /> Info: {pipeline.name}
        </b>
      );
    }
    return (
      <div
        className={className}
        style={style}
      >
        <Collapse
          header={infoHeader}
        >
          <AnalysisPipelineInfo
            pipeline={pipeline}
          />
        </Collapse>
        {
          (pipeline.modules || [])
            .filter(cpModule => !cpModule.hidden)
            .map((cpModule) => (
              <Collapse
                key={cpModule.displayName}
                expanded={this.getExpanded(cpModule.id)}
                onExpandedChange={() => this.toggleExpanded(cpModule.id)}
                header={(
                  <CellProfilerModuleHeader
                    cpModule={cpModule}
                    hasErrors={this.moduleIdsWithErrors.includes(cpModule.uuid)}
                  />
                )}
              >
                <CellProfilerModule cpModule={cpModule} />
              </Collapse>
            ))
        }
        {
          pipeline &&
          pipeline.objects &&
          pipeline.objects.length > 0 && (
            <Collapse
              header={(
                <CellProfilerModuleHeader
                  cpModule={pipeline.defineResults}
                  removable={false}
                  movable={false}
                />
              )}
            >
              <CellProfilerModule
                cpModule={pipeline.defineResults}
              />
            </Collapse>
          )
        }
        {
          pipeline &&
          pipeline.graphicsOutput &&
          pipeline.graphicsOutput.configurations.length > 0 && (
            <Collapse header="Display objects">
              <ObjectsOutlineRenderer
                pipeline={pipeline}
                viewer={viewer}
              />
            </Collapse>
          )
        }
      </div>
    );
  }
}

CellProfilerPipeline.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  pipeline: PropTypes.object,
  viewer: PropTypes.object,
  expandSingle: PropTypes.bool,
  hidden: PropTypes.bool
};

export default observer(CellProfilerPipeline);
