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
import {computed} from 'mobx';
import {Icon, Row} from 'antd';
import pipelineRun from '../../../models/pipelines/PipelineRun';
import renderHighlights from './renderHighlights';
import renderSeparator from './renderSeparator';
import {PreviewIcons} from './previewIcons';
import StatusIcon, {Statuses} from '../../special/run-status-icon';
import {getRunSpotTypeName} from '../../special/spot-instance-names';
import styles from './preview.css';
import evaluateRunDuration from '../../../utils/evaluateRunDuration';
import displayDate from '../../../utils/displayDate';
import moment from 'moment';
import parseRunServiceUrl from '../../../utils/parseRunServiceUrl';
import UserName from '../../special/UserName';
import AWSRegionTag from '../../special/AWSRegionTag';

const FIRE_CLOUD_ENVIRONMENT = 'FIRECLOUD';
const DTS_ENVIRONMENT = 'DTS';

const colors = {
  [Statuses.failure]: {
    color: 'rgb(255, 76, 31)'
  },
  [Statuses.paused]: {
    color: 'rgb(78, 186, 255)',
    fontWeight: 'bold'
  },
  [Statuses.pausing]: {
    color: 'rgb(78, 186, 255)',
    fontWeight: 'bold'
  },
  [Statuses.running]: {
    color: 'rgb(78, 186, 255)',
    fontWeight: 'bold'
  },
  [Statuses.queued]: {
    color: 'rgb(78, 186, 255)',
    fontWeight: 'bold'
  },
  [Statuses.resuming]: {
    color: 'rgb(78, 186, 255)',
    fontWeight: 'bold'
  },
  [Statuses.scheduled]: {
    color: 'rgb(78, 186, 255)',
    fontWeight: 'bold'
  },
  [Statuses.stopped]: {
    color: '#f79e2c',
    fontWeight: 'bold'
  },
  [Statuses.success]: {
    color: 'rgb(66, 255, 83)',
    fontWeight: 'bold'
  }
};

const icons = {
  [Statuses.failure]: 'exclamation-circle-o',
  [Statuses.stopped]: 'clock-circle-o'
};

@inject('dtsList', 'preferences')
@inject(({}, params) => {
  return {
    runInfo: params.item && params.item.id
      ? pipelineRun.run(params.item.id, {refresh: false})
      : null,
    runTasks: params.item && params.item.id
      ? pipelineRun.runTasks(params.item.id)
      : null
  };
})
@observer
export default class PipelineRunPreview extends React.Component {
  static propTypes = {
    item: PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      parentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      name: PropTypes.string,
      description: PropTypes.string
    })
  };

  @computed
  get runName () {
    if (this.props.runInfo && this.props.runInfo.loaded) {
      const {
        dockerImage,
        pipelineName,
        version
      } = this.props.runInfo.value;
      if (pipelineName && version) {
        return `Run #${this.props.item.id} - ${pipelineName} (${version})`;
      } else if (pipelineName) {
        return `Run #${this.props.item.id} - ${pipelineName}`;
      } else if (dockerImage) {
        const image = dockerImage.split('/').pop();
        return `Run #${this.props.item.id} - ${image}`;
      }
      return `Run #${this.props.item.id}`;
    }
    if (this.props.item) {
      return `Run #${this.props.item.id}`;
    }
    return null;
  }

  @computed
  get timeFromStart () {
    if (!this.props.runInfo.loaded) {
      return '';
    }
    const {startDate} = this.props.runInfo.value;
    return moment.utc(startDate).fromNow(true);
  }

  @computed
  get runningTime () {
    if (this.props.runTasks.pending || this.props.runTasks.value.length === 0) {
      return '';
    }
    return moment.utc(this.props.runTasks.value[0].started).fromNow(true);
  }

  @computed
  get timings () {
    if (this.props.runInfo && this.props.runInfo.loaded) {
      const result = [];
      const {
        startDate,
        endDate,
        status
      } = this.props.runInfo.value;
      result.push({
        title: 'Scheduled:',
        value: displayDate(startDate)
      });
      if (this.props.runTasks && this.props.runTasks.loaded && this.props.runTasks.value.length) {
        const firstTask = this.props.runTasks.value[0];
        result.push({
          title: 'Started:',
          value: `${displayDate(firstTask.started)} (${moment.utc(firstTask.started).diff(moment.utc(startDate), 'minutes', true).toFixed(2)} min)`
        });
        if (status === 'RUNNING') {
          result.push({
            title: 'Running for:',
            value: this.runningTime
          });
        } else if (status === 'SUCCESS' || status === 'FAILURE') {
          const firstTask = this.props.runTasks.value[0];
          result.push({
            title: 'Finished:',
            value: `${displayDate(endDate)} (${moment.utc(endDate).diff(moment.utc(firstTask.started), 'minutes', true).toFixed(2)} min)`
          });
        } else {
          const firstTask = this.props.runTasks.value[0];
          result.push({
            title: 'Stopped at:',
            value: `${displayDate(endDate)} (${moment.utc(endDate).diff(moment.utc(firstTask.started), 'minutes', true).toFixed(2)} min)`
          });
        }
      } else {
        result.push({
          title: 'Waiting for:',
          value: this.timeFromStart
        });
      }
      return result;
    }
    return [];
  }

  @computed
  get isDtsEnvironment () {
    return this.props.runInfo.loaded && this.props.runInfo.value.executionPreferences &&
      this.props.runInfo.value.executionPreferences.environment === DTS_ENVIRONMENT;
  }

  @computed
  get isFireCloudEnvironment () {
    return this.props.runInfo.loaded && this.props.runInfo.value.executionPreferences &&
      this.props.runInfo.value.executionPreferences.environment === FIRE_CLOUD_ENVIRONMENT;
  }

  @computed
  get dtsList () {
    if (this.props.dtsList.loaded) {
      return (this.props.dtsList.value || []).map(i => i);
    }
    return [];
  }

  getExecEnvString = (run) => {
    let environment;
    if (this.isDtsEnvironment) {
      const dts = this.dtsList.filter(dts => dts.id === run.executionPreferences.dtsId)[0];
      environment = dts ? `${dts.name}` : `${run.executionPreferences.dtsId}`;
    } else if (this.isFireCloudEnvironment) {
      environment = 'FireCloud';
    } else {
      environment = this.props.preferences.deploymentName || 'EPAM Cloud Pipeline';
    }

    return environment;
  };

  renderDescription = () => {
    if (this.props.runInfo && this.props.runInfo.loaded) {
      const run = this.props.runInfo.value;
      const {instance} = run;
      const details = [];
      if (instance) {
        if (run.executionPreferences && run.executionPreferences.environment) {
          details.push({key: 'Execution environment', value: this.getExecEnvString(run)});
        }
        if (!this.isDtsEnvironment) {
          if (instance.cloudRegionId && instance.nodeType) {
            details.push({
              key: 'Cloud Region and instance',
              value: (
                <span>
                  <AWSRegionTag
                    style={{verticalAlign: 'top', marginRight: -3, marginLeft: -3}}
                    regionId={instance.cloudRegionId} />
                  {instance.nodeType}
                </span>
              )
            });
          } else if (instance.nodeType) {
            details.push({key: 'Node type', value: `${instance.nodeType}`});
          } else if (instance.cloudRegionId) {
            details.push({
              key: 'Cloud Region',
              value: (
                <AWSRegionTag
                  style={{verticalAlign: 'top'}}
                  regionId={instance.cloudRegionId} />
              )
            });
          }
          details.push(
            {key: 'Price type', value: getRunSpotTypeName({instance})}
          );
          if (instance.nodeDisk) {
            details.push({key: 'Disk', value: `${instance.nodeDisk}Gb`});
          }
        } else {
          if (run.executionPreferences && run.executionPreferences.coresNumber) {
            const label = run.executionPreferences.coresNumber === 1 ? 'core' : 'cores';
            details.push({key: 'Cores', value: `${run.executionPreferences.coresNumber} ${label}`});
          }
        }
        if (run.dockerImage) {
          const [, , imageName] = run.dockerImage.split('/');
          details.push({key: 'Docker image', value: imageName});
        }
      }
      if (details.length > 0) {
        return (
          <Row className={`${styles.description} ${styles.tags}`}>
            {
            details.map(d => {
              return (
                <span
                  key={d.key}
                  className={styles.instanceHeaderItem}>
                  {d.value}
                </span>
              );
            })
          }
          </Row>
        );
      }
    }
    return null;
  };

  renderInfo = () => {
    if (this.props.runInfo) {
      if (this.props.runInfo.pending) {
        return <Row className={styles.contentPreview} type="flex" justify="center"><Icon type="loading" /></Row>;
      }
      if (this.props.runInfo.error) {
        return (
          <div className={styles.contentPreview}>
            <span style={{color: '#ff556b'}}>{this.props.runInfo.error}</span>
          </div>
        );
      }
      const endpointsAvailable = this.props.runInfo.value.initialized;
      const urls = parseRunServiceUrl(this.props.runInfo.value.serviceUrl);
      const {
        owner
      } = this.props.runInfo.value;
      const headerStyle = {verticalAlign: 'top', paddingRight: 5, whiteSpace: 'nowrap'};
      const adjustPrice = (value) => {
        let cents = Math.ceil(value * 100);
        if (cents < 1) {
          cents = 1;
        }
        return cents / 100;
      };
      return (
        <div className={styles.contentPreview}>
          <table>
            <tbody>
              {
                endpointsAvailable && urls.length > 0 &&
                <tr>
                  <td style={headerStyle}>
                    Endpoints:
                  </td>
                  <td>
                    {
                      urls.map((url, index) => {
                        return (
                          <div key={index}>
                            <a href={url.url} target="_blank">{url.name || url.url}</a>
                          </div>
                        );
                      })}
                  </td>
                </tr>
              }
              <tr>
                <td style={headerStyle}>Owner: </td>
                <td><UserName userName={owner} /></td>
              </tr>
              {
                this.timings.map((t, index) => {
                  return (
                    <tr key={`timing-${index}`}>
                      <td style={headerStyle}>{t.title}</td>
                      <td>{t.value}</td>
                    </tr>
                  );
                })
              }
              <tr>
                <td style={headerStyle}>
                  Estimated price:
                </td>
                <td>
                  {
                    adjustPrice(
                      evaluateRunDuration(this.props.runInfo.value) *
                      this.props.runInfo.value.pricePerHour
                    )
                  }$
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      );
    }
    return null;
  };

  renderTasks = () => {
    if (this.props.runTasks) {
      if (this.props.runTasks.pending) {
        return <Row className={styles.contentPreview} type="flex" justify="center"><Icon type="loading" /></Row>;
      }
      if (this.props.runTasks.error) {
        return (
          <div className={styles.contentPreview}>
            <span style={{color: '#ff556b'}}>{this.props.runTasks.error}</span>
          </div>
        );
      }
      if (this.props.runTasks.loaded) {
        return (
          <div className={styles.contentPreview}>
            {
              (this.props.runTasks.value || []).map((task, index) => {
                return (
                  <Row key={index} className={styles.task}>
                    <StatusIcon
                      status={task.status}
                      small
                      additionalStyleByStatus={colors}
                      iconSet={icons}
                      displayTooltip={false} />
                    <code>{task.name}</code>
                  </Row>
                );
              })
            }
          </div>
        );
      }
    }
    return null;
  };

  render () {
    if (!this.props.item) {
      return null;
    }
    const highlights = renderHighlights(this.props.item);
    const description = this.renderDescription();
    const info = this.renderInfo();
    const tasks = this.renderTasks();
    return (
      <div className={styles.container}>
        <div className={styles.header}>
          <Row className={styles.title} type="flex" align="middle">
            {
              this.props.runInfo && this.props.runInfo.loaded
                ? <StatusIcon
                    run={this.props.runInfo.value}
                    additionalStyleByStatus={colors}
                    iconSet={icons} />
                : <Icon type={PreviewIcons[this.props.item.type]} style={{fontSize: 'smaller'}} />
            }
            <span>{this.runName}</span>
          </Row>
          {description}
        </div>
        <div className={styles.content}>
          {highlights && renderSeparator()}
          {highlights}
          {info && renderSeparator()}
          {info}
          {tasks && renderSeparator()}
          {tasks}
        </div>
      </div>
    );
  }

}
