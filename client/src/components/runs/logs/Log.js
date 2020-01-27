/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import {Link} from 'react-router';
import FileSaver from 'file-saver';
import {Alert, Card, Col, Collapse, Icon, Menu, message, Modal, Popover, Row, Spin} from 'antd';
import SplitPane from 'react-split-pane';
import {
  PipelineRunCommitCheck,
  PIPELINE_RUN_COMMIT_CHECK_FAILED
} from '../../../models/pipelines/PipelineRunCommitCheck';
import pipelineRun from '../../../models/pipelines/PipelineRun';
import PausePipeline from '../../../models/pipelines/PausePipeline';
import ResumePipeline from '../../../models/pipelines/ResumePipeline';
import PipelineRunInfo from '../../../models/pipelines/PipelineRunInfo';
import PipelineExportLog from '../../../models/pipelines/PipelineExportLog';
import PipelineRunSSH from '../../../models/pipelines/PipelineRunSSH';
import PipelineRunCommit from '../../../models/pipelines/PipelineRunCommit';
import pipelines from '../../../models/pipelines/Pipelines';
import Roles from '../../../models/user/Roles';
import PipelineRunUpdateSids from '../../../models/pipelines/PipelineRunUpdateSids';
import {
  stopRun,
  canPauseRun,
  canStopRun,
  runPipelineActions,
  terminateRun
} from '../actions';
import connect from '../../../utils/connect';
import evaluateRunDuration from '../../../utils/evaluateRunDuration';
import displayDate from '../../../utils/displayDate';
import displayDuration from '../../../utils/displayDuration';
import roleModel from '../../../utils/roleModel';
import localization from '../../../utils/localization';
import parseRunServiceUrl from '../../../utils/parseRunServiceUrl';
import parseQueryParameters from '../../../utils/queryParameters';
import styles from './Log.css';
import AdaptedLink from '../../special/AdaptedLink';
import {TaskLink} from './tasks/TaskLink';
import LogList from './LogList';
import StatusIcon from '../../special/run-status-icon';
import UserName from '../../special/UserName';
import WorkflowGraph from '../../pipelines/version/graph/WorkflowGraph';
import {graphIsSupportedForLanguage} from '../../pipelines/version/graph/visualization';
import LoadingView from '../../special/LoadingView';
import AWSRegionTag from '../../special/AWSRegionTag';
import CommitRunDialog from './forms/CommitRunDialog';
import ShareWithForm from './forms/ShareWithForm';
import DockerImageLink from './DockerImageLink';
import mapResumeFailureReason from '../utilities/map-resume-failure-reason';
import LaunchCommand from '../../pipelines/launch/form/utilities/launch-command';

const FIRE_CLOUD_ENVIRONMENT = 'FIRECLOUD';
const DTS_ENVIRONMENT = 'DTS';
const MAX_PARAMETER_VALUES_TO_DISPLAY = 5;
const MAX_NESTED_RUNS_TO_DISPLAY = 10;

@connect({
  pipelineRun,
  pipelines
})
@localization.localizedComponent
@runPipelineActions
@inject('preferences', 'dtsList')
@inject(({pipelineRun, routing, pipelines}, {params}) => {
  const queryParameters = parseQueryParameters(routing);
  let task = null;
  if (params.taskName) {
    task = {
      name: params.taskName,
      parameters: queryParameters.parameters,
      instance: queryParameters.instance
    };
  }

  return {
    runId: params.runId,
    taskName: params.taskName,
    run: pipelineRun.run(params.runId, {refresh: true}),
    nestedRuns: pipelineRun.nestedRuns(params.runId, MAX_NESTED_RUNS_TO_DISPLAY),
    runSSH: new PipelineRunSSH(params.runId),
    runTasks: pipelineRun.runTasks(params.runId),
    task,
    pipelines,
    roles: new Roles()
  };
})
@observer
class Logs extends localization.LocalizedReactComponent {

  @observable language = null;
  @observable _pipelineLanguage = null;

  state = {
    timings: false,
    commitRun: false,
    resolvedValues: true,
    operationInProgress: false,
    openedPanels: [],
    shareDialogOpened: false,
    showLaunchCommands: false
  };

  componentDidMount () {
    const {runTasks} = this.props;
    runTasks.fetch();
  }

  componentWillUnmount () {
    this.props.run.clearInterval();
    this.props.runTasks.clearInterval();
    this.props.nestedRuns.clearRefreshInterval();
  }

  parentRunPipelineInfo = null;

  @computed
  get runPayload () {
    const {run, preferences} = this.props;
    if (run.loaded && preferences.loaded) {
      const payload = {
        instanceType: undefined,
        hddSize: undefined,
        timeout: run.value.timeout,
        cmdTemplate: run.value.cmdTemplate,
        nodeCount: run.value.nodeCount,
        dockerImage: run.value.dockerImage,
        pipelineId: run.value.pipelineId,
        version: run.value.version,
        params: {},
        isSpot: preferences.useSpot,
        cloudRegionId: undefined,
        prettyUrl: run.value.prettyUrl,
        nonPause: run.value.nonPause,
        configurationName: run.value.configName,
        executionEnvironment: undefined
      };
      if (run.value.instance) {
        payload.instanceType = run.value.instance.nodeType;
        payload.hddSize = run.value.instance.nodeDisk;
        payload.isSpot = run.value.instance.spot;
        payload.cloudRegionId = run.value.instance.cloudRegionId;
      }
      if (run.value.executionPreferences) {
        payload.executionEnvironment = run.value.executionPreferences.environment;
      }
      if (run.value.pipelineRunParameters) {
        for (let i = 0; i < run.value.pipelineRunParameters.length; i++) {
          const param = run.value.pipelineRunParameters[i];
          if (param.name && param.value) {
            payload.params[param.name] = {
              value: param.value,
              type: param.type,
              enum: param.enum
            };
          }
        }
      }
      return payload;
    }
    return null;
  }

  exportLog = async () => {
    const {runId} = this.props.params;

    try {
      const hide = message.loading('Exporting log...');
      const request = new PipelineExportLog(runId);
      await request.fetch();
      if (request.response) {
        FileSaver.saveAs(request.response, `run_${runId}_log.txt`);
      } else {
        message.error('Error exporting log', 2);
      }
      hide();
    } catch (e) {
      message.error('Error exporting log', 5);
    }
  };

  stopPipeline = () => {
    return stopRun(this, () => { this.props.run.fetch(); })(this.props.run.value);
  };

  terminatePipeline = () => {
    return terminateRun(this, () => { this.props.run.fetch(); })(this.props.run.value);
  };

  showPauseConfirmDialog = async () => {
    const dockerImageParts = (this.props.run.value.dockerImage || '').split('/');
    const imageName = dockerImageParts[dockerImageParts.length - 1].split(':')[0];
    const pipelineName = this.props.run.value.pipelineName || imageName || this.localizedString('pipeline');
    const checkRequest = new PipelineRunCommitCheck(this.props.runId);
    await checkRequest.fetch();
    let content;
    if (checkRequest.loaded && !checkRequest.value) {
      content = (
        <Alert
          type="error"
          message={PIPELINE_RUN_COMMIT_CHECK_FAILED} />
      );
    }
    Modal.confirm({
      title: `Do you want to pause ${pipelineName}?`,
      content,
      style: {
        wordWrap: 'break-word'
      },
      onOk: () => this.pausePipeline(),
      okText: 'PAUSE',
      cancelText: 'CANCEL',
      width: 450
    });
  };

  showResumeConfirmDialog = () => {
    const dockerImageParts = (this.props.run.value.dockerImage || '').split('/');
    const imageName = dockerImageParts[dockerImageParts.length - 1].split(':')[0];
    const pipelineName = this.props.run.value.pipelineName || imageName || this.localizedString('pipeline');
    Modal.confirm({
      title: `Do you want to resume ${pipelineName}?`,
      style: {
        wordWrap: 'break-word'
      },
      onOk: () => this.resumePipeline(),
      okText: 'RESUME',
      cancelText: 'CANCEL'
    });
  };

  pausePipeline = async (e) => {
    if (e) {
      e.stopPropagation();
    }
    const {id} = this.props.run.value;
    const pausePipeline = new PausePipeline(id);
    await pausePipeline.send({});
    if (pausePipeline.error) {
      message.error(pausePipeline.error);
    }
    this.props.run.fetch();
  };

  resumePipeline = async (e) => {
    if (e) {
      e.stopPropagation();
    }
    const {id} = this.props.run.value;
    const resumePipeline = new ResumePipeline(id);
    await resumePipeline.send({});
    if (resumePipeline.error) {
      message.error(resumePipeline.error);
    }
    this.props.run.fetch();
  };

  reRunPipeline = () => {
    const {pipelineId, version, id, configName} = this.props.run.value;
    if (pipelineId && version && id) {
      this.props.router.push(`/launch/${pipelineId}/${version}/${configName || 'default'}/${id}`);
    } else if (pipelineId && version && configName) {
      this.props.router.push(`/launch/${pipelineId}/${version}/${configName}`);
    } else if (pipelineId && version) {
      this.props.router.push(`/launch/${pipelineId}/${version}/default`);
    } else if (id) {
      this.props.router.push(`/launch/${id}`);
    }
  };

  loadParentRunInfo = (runId) => {
    if (this.parentRunPipelineInfo === null ||
      this.parentRunPipelineInfo.runId !== runId) {
      this.parentRunPipelineInfo = new PipelineRunInfo(runId);
      this.parentRunPipelineInfo.fetch();
    }
  };

  renderRunParameter = (runParameter) => {
    if (!runParameter || !runParameter.name) {
      return null;
    }
    const valueSelector = () => {
      if (this.state.resolvedValues) {
        return runParameter.resolvedValue || runParameter.value || '';
      }
      return runParameter.value || '';
    };
    if (runParameter.dataStorageLinks) {
      const valueParts = valueSelector().split(/[,|]/);
      const urls = [];
      for (let i = 0; i < valueParts.length; i++) {
        const value = valueParts[i].trim();
        const [link] = runParameter.dataStorageLinks.filter(link => {
          return link.absolutePath && value.toLowerCase() === link.absolutePath.toLowerCase();
        });
        if (link) {
          let url = `/storage/${link.dataStorageId}`;
          if (link.path && link.path.length) {
            url = `/storage/${link.dataStorageId}?path=${link.path}`;
          }
          urls.push((
            <AdaptedLink
              key={i}
              className={styles.taskParameterValue}
              to={url}
              location={this.props.router.location}>{value}</AdaptedLink>
          ));
        } else {
          urls.push(<span key={i}>{value}</span>);
        }
      }
      return (
        <tr key={runParameter.name}>
          <td className={styles.taskParameterName}>
            <span>{runParameter.name}: </span>
          </td>
          <td>
            <ul>
              {urls.map((url, index) => <li key={index}>{url}</li>)}
            </ul>
          </td>
        </tr>
      );
    }
    if (runParameter.name === 'parent-id' && parseInt(valueSelector()) !== 0) {
      this.loadParentRunInfo(valueSelector());
      if (this.parentRunPipelineInfo.pending) {
        return (
          <tr
            key={runParameter.name}>
            <td
              className={styles.taskParameterName}>{runParameter.name}:</td><td>Loading...</td>
          </tr>
        );
      } else {
        return (
          <tr key={runParameter.name}>
            <td
              className={styles.taskParameterName}>{runParameter.name}:</td>
            <td>
              <AdaptedLink
                className={styles.taskParameterValue}
                to={`/run/${valueSelector()}/${this.props.params.mode}`}
                location={this.props.router.location}>
                {valueSelector()}
              </AdaptedLink>
            </td>
          </tr>
        );
      }
    } else {
      let values = (valueSelector() || '').split(',').map(v => v.trim());
      if (values.length === 1) {
        return (
          <tr
            key={runParameter.name}>
            <td className={styles.taskParameterName}>{runParameter.name}:</td>
            <td>{values[0]}</td>
          </tr>
        );
      } else if (values.length <= MAX_PARAMETER_VALUES_TO_DISPLAY + 1) {
        return (
          <tr key={runParameter.name}>
            <td className={styles.taskParameterName}>
              <span>{runParameter.name}:</span>
            </td>
            <td>
              <ul>
                {values.map((value, index) => <li key={index}>{value}</li>)}
              </ul>
            </td>
          </tr>
        );
      } else {
        return (
          <tr key={runParameter.name}>
            <td className={styles.taskParameterName}>
              <span>{runParameter.name}:</span>
            </td>
            <td>
              <ul>
                {
                  values
                    .filter((value, index) => index < MAX_PARAMETER_VALUES_TO_DISPLAY)
                    .map((value, index) => <li key={index}>{value}</li>)
                }
                <li>
                  <Popover
                    placement="right"
                    content={
                      <div style={{maxHeight: '50vh', overflow: 'auto', paddingRight: 20}}>
                        {values.map((value, index) => <Row key={index}>{value}</Row>)}
                      </div>
                    }>
                    <a>And {values.length - MAX_PARAMETER_VALUES_TO_DISPLAY} more</a>
                  </Popover>
                </li>
              </ul>
            </td>
          </tr>
        );
      }
    }
  };

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

  renderInstanceHeader = (instance, run) => {
    if (this.state.openedPanels.indexOf('instance') >= 0) {
      return 'Instance';
    }
    const details = [];
    if (instance) {
      if (run.executionPreferences && run.executionPreferences.environment) {
        details.push({key: 'Execution environment', value: this.getExecEnvString(run)});
      }
      if (!this.isDtsEnvironment) {
        if (instance.cloudRegionId && instance.nodeType) {
          details.push({
            key: 'Cloud region and instance',
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
        if (instance.spot !== undefined) {
          details.push(
            {key: 'Price type', value: `${instance.spot}` === 'true' ? 'Spot' : 'On-demand'}
          );
        }
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
        <Row>
          Instance: {
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
    return 'Instance';
  };

  renderInstanceDetails = (instance, run) => {
    const details = [];
    if (instance) {
      if (run.executionPreferences && run.executionPreferences.environment) {
        details.push({key: 'Execution environment', value: this.getExecEnvString(run)});
      }
      if (!this.isDtsEnvironment) {
        if (instance.cloudRegionId) {
          details.push({
            key: 'Cloud Region',
            value: (
              <AWSRegionTag
                regionId={instance.cloudRegionId}
                displayName
                style={{marginLeft: -5, verticalAlign: 'top'}} />
            )
          });
        }
        if (instance.nodeType) {
          details.push({key: 'Node type', value: `${instance.nodeType}`});
        }
        if (instance.spot !== undefined) {
          details.push(
            {key: 'Price type', value: `${instance.spot}` === 'true' ? 'Spot' : 'On-demand'}
          );
        }
        if (instance.nodeDisk) {
          details.push({key: 'Disk', value: `${instance.nodeDisk} Gb`});
        }
      } else {
        if (run.executionPreferences && run.executionPreferences.coresNumber) {
          details.push({key: 'Cores', value: `${run.executionPreferences.coresNumber}`});
        }
      }
      if (instance.nodeIP) {
        if (instance.nodeName) {
          details.push({
            key: 'IP',
            value: (
              <Link to={`/cluster/${instance.nodeName}`}>
                {instance.nodeName} ({instance.nodeIP})
              </Link>
            )});
        } else {
          const parts = instance.nodeIP.split('.');
          if (parts.length === 4) {
            details.push({
              key: 'IP',
              value: (
                <Link to={`/cluster/ip-${parts.join('-')}`}>
                  {instance.nodeIP}
                </Link>
              )
            });
          } else {
            details.push({key: 'IP', value: `${instance.nodeIP}`});
          }
        }
      }
      if (run.dockerImage) {
        details.push({key: 'Docker image', value: (<DockerImageLink path={run.dockerImage} />)});
      }
      if (instance.nodeImage) {
        details.push({key: 'Node image', value: `${instance.nodeImage}`});
      }
      if (run.cmdTemplate) {
        details.push({key: 'Cmd template', value: `${run.cmdTemplate}`});
      }
      if (run.actualCmd) {
        details.push({key: 'Cmd', value: `${run.actualCmd}`});
      }
    }
    return details.map(detail =>
      <li key={detail.key}>
        <span className={styles.nodeParameterName}>{detail.key}: </span>
        <span className={styles.nodeParameterValue}>{detail.value}</span>
      </li>
    );
  };

  onSelect = (node) => {
    if (node) {
      const task = this.getTask(node);
      if (!task) {
        return;
      }
      const parameters = task.parameters ? `?parameters=${task.parameters}` : '';
      const taskUrl = this.getTaskUrl(task);
      const url = `/run/${this.props.params.runId}/${this.props.params.mode}/${taskUrl}`;
      this.props.router.push(url);
    } else {
      const url = `/run/${this.props.params.runId}/${this.props.params.mode}`;
      this.props.router.push(url);
    }
  };

  // For WDL pipelines actual task name may be like 'cromwell_<some id>_<task name>,
  // so we need to process this format as weel.
  getTask = ({task}) => {
    if (task && task.name) {
      const parametersMatchFn = (parametersMask, parameters) => {
        if (!parameters || !parametersMask) {
          return false;
        }
        const _maskParts = (parametersMask || '').split(',');
        const _parts = (parameters || '').split(',');
        const maskParts = [];
        const parts = [];

        const getParameter = (str) => {
          const p = str.split('=');
          const key = p[0].trim();
          let value = p[1];
          for (let j = 2; j < p.length; j++) {
            value += `=${p[j]}`;
          }
          return {key, value};
        };

        for (let i = 0; i < _maskParts.length; i++) {
          maskParts.push(getParameter(_maskParts[i]));
        }

        for (let i = 0; i < _parts.length; i++) {
          parts.push(getParameter(_parts[i]));
        }

        for (let i = 0; i < parts.length; i++) {
          const [maskPart] = maskParts.filter(p => p.key.toLowerCase() === parts[i].key.toLowerCase());
          if (maskPart && !maskPart.value.startsWith('&') &&
            maskPart.value.toLowerCase() !== parts[i].value.toLowerCase()) {
            return false;
          }
        }
        return true;
      };
      const tasksState = this.props.runTasks.pending ? [] : this.props.runTasks.value.map(r => r);
      // trying ot get task state by received task name:
      let [taskState] = tasksState.filter(t =>
        t.name === task.name && parametersMatchFn(task.parameters, t.parameters));
      if (!taskState) {
        // trying to get task state by name format 'cromwell_<some id>_<task name>:
        const regExp = new RegExp(`^cromwell_[\\da-zA-Z]+_${task.name}$`, 'i');
        [taskState] = tasksState.filter(t => regExp.test(t.name));
      }
      return taskState;
    }
    return null;
  };

  getNodeAdditionalInfo = (task) => {
    const taskState = this.getTask(task);
    return {
      status: taskState ? taskState.status : undefined,
      internalId: taskState ? this.getTaskUrl(taskState) : undefined,
      task: taskState
    };
  };

  renderContentGraphMode () {
    if (!this.props.run.loaded) {
      return <div className={styles.container}><Spin /></div>;
    }
    if (this.props.run.value.pipelineId && this.props.run.value.version) {
      const selectedTask = this.props.task ? this.getTaskUrl(this.props.task) : null;
      let timeout;
      const resizeGraph = () => {
        if (timeout) {
          clearTimeout(timeout);
        }
        timeout = setTimeout(() => {
          if (this.graph) {
            this.graph.draw();
          }
          timeout = null;
        }, 100);
      };
      return (
        <Row type="flex" style={{flex: 1}}>
          <SplitPane
            style={{display: 'flex', flex: 1, minHeight: 500}}
            defaultSize={300}
            onChange={resizeGraph}
            pane1Style={{display: 'flex', flexDirection: 'column'}}
            pane2Style={{display: 'flex', flexDirection: 'column'}}
            resizerStyle={{
              width: 10,
              margin: '0 -4px',
              cursor: 'col-resize',
              backgroundColor: 'transparent',
              boxSizing: 'border-box',
              backgroundClip: 'padding',
              zIndex: 1
            }}>
            <div style={{display: 'flex', flex: 1}}>
              <WorkflowGraph
                canEdit={false}
                onGraphReady={(graph) => { this.graph = graph; }}
                pipelineId={this.props.run.value.pipelineId}
                version={this.props.run.value.version}
                onSelect={this.onSelect}
                getNodeInfo={this.getNodeAdditionalInfo}
                selectedTaskId={selectedTask} />
            </div>
            <div
              className={styles.logContent}>
              <LogList
                runId={this.props.runId}
                taskName={this.props.taskName} />
            </div>
          </SplitPane>
        </Row>
      );
    } else {
      return this.renderContentPlainMode();
    }
  };

  getTaskUrl = (task) => {
    let url = '';
    if (task) {
      url = task.name;
      const params = [];
      if (task.parameters) {
        params.push(`parameters=${task.parameters}`);
      }
      if (task.instance) {
        params.push(`instance=${task.instance}`);
      }
      if (params.length) {
        url += `?${params.join('&')}`;
      }
      url = decodeURIComponent(url);
    }
    return url;
  };

  renderContentPlainMode () {
    const {runId}=this.props.params;
    const selectedTask = this.props.task ? this.getTaskUrl(this.props.task) : null;
    let Tasks;

    if (this.props.runTasks.pending) {
      Tasks = <Menu.Item key={-3}>...Loading</Menu.Item>;
    } else if (this.props.runTasks.value.length === 0) {
      Tasks = <Menu.Item key={-2}>No tasks</Menu.Item>;
    } else {
      Tasks = this.props.runTasks.value.map((task, index) => {
        return (
          <Menu.Item key={this.getTaskUrl(task, index)}>
            <TaskLink
              to={`/run/${runId}/${this.props.params.mode}/${this.getTaskUrl(task)}`}
              location={location}
              task={task}
              timings={this.state.timings} />
          </Menu.Item>);
      }
      );
    }

    return (
      <Row type="flex" style={{flex: 1}}>
        <SplitPane
          style={{display: 'flex', flex: 1, minHeight: 500}}
          defaultSize={300}
          pane1Style={{display: 'flex', flexDirection: 'column'}}
          pane2Style={{display: 'flex', flexDirection: 'column'}}
          resizerStyle={{
            width: 10,
            margin: '0 -4px',
            cursor: 'col-resize',
            backgroundColor: 'transparent',
            boxSizing: 'border-box',
            backgroundClip: 'padding',
            zIndex: 1
          }}>
          <div style={{display: 'flex', flex: 1, height: '100%', overflowY: 'auto'}}>
            <Menu
              selectedKeys={selectedTask ? [selectedTask] : []}
              mode="inline"
              className={this.state.timings ? styles.taskListTimings : styles.taskList}>
              {Tasks}
            </Menu>
          </div>
          <div
            className={styles.logContent}>
            <LogList
              runId={this.props.runId}
              taskName={this.props.taskName} />
          </div>
        </SplitPane>
      </Row>
    );
  }

  renderContent () {
    if (this._pipelineLanguage) {
      if (graphIsSupportedForLanguage(this._pipelineLanguage)) {
        if (this.props.params.mode.toLowerCase() === 'plain') {
          return this.renderContentPlainMode();
        } else {
          return this.renderContentGraphMode();
        }
      } else {
        return this.renderContentPlainMode();
      }
    } else {
      return <LoadingView />;
    }
  }

  @computed
  get timeFromStart () {
    if (!this.props.run.loaded) {
      return '';
    }
    const {startDate} = this.props.run.value;
    return displayDuration(startDate);
  }

  @computed
  get runningTime () {
    if (this.props.runTasks.pending || this.props.runTasks.value.length === 0) {
      return '';
    }
    return displayDuration(this.props.runTasks.value[0].started);
  }

  switchTimings = () => {
    this.setState({timings: !this.state.timings});
  };

  showLaunchCommands = () => {
    this.setState({showLaunchCommands: true});
  };

  hideLaunchCommands = () => {
    this.setState({showLaunchCommands: false});
  };

  switchResolvedValues = () => {
    this.setState({resolvedValues: !this.state.resolvedValues});
  };

  @observable
  _commitCheck = null;

  fetchCommitCheck = async () => {
    this._commitCheck = new PipelineRunCommitCheck(this.props.runId);
    await this._commitCheck.fetch();
  };

  @computed
  get commitCheck () {
    if (!this._commitCheck || !this._commitCheck.loaded) {
      return true;
    }

    return !!this._commitCheck.value;
  }

  openCommitRunForm = () => {
    this.operationWrapper(this.fetchCommitCheck);
    this.setState({commitRun: true});
  };

  closeCommitRunForm = () => {
    this.setState({commitRun: false});
  };

  operationWrapper = (operation) => (...props) => {
    this.setState({
      operationInProgress: true
    }, async () => {
      await operation(...props);
      this.setState({
        operationInProgress: false
      });
    });
  };

  commitRun = async ({deleteFiles, newImageName, registryToCommitId, stopPipeline}) => {
    if ((this.props.run.value.status || '').toLowerCase() !== 'running') {
      message.error('You can commit only running pipelines');
      this.closeCommitRunForm();
    } else {
      const request = new PipelineRunCommit(this.props.run.value.id);
      const hide = message.loading('Committing...', -1);
      await request.send({
        deleteFiles,
        newImageName,
        registryToCommitId,
        stopPipeline
      });
      hide();
      if (request.error) {
        message.error(request.error);
      } else {
        await this.props.run.fetch();
        this.closeCommitRunForm();
      }
    }
  };

  @computed
  get isDtsEnvironment () {
    return this.props.run.loaded && this.props.run.value.executionPreferences &&
      this.props.run.value.executionPreferences.environment === DTS_ENVIRONMENT;
  }

  @computed
  get isFireCloudEnvironment () {
    return this.props.run.loaded && this.props.run.value.executionPreferences &&
      this.props.run.value.executionPreferences.environment === FIRE_CLOUD_ENVIRONMENT;
  }

  @computed
  get initializeEnvironmentFinished () {
    return this.props.run.loaded && this.props.run.value.initialized;
  }

  @computed
  get sshEnabled () {
    if (this.props.run.loaded && this.props.runSSH.loaded && this.initializeEnvironmentFinished &&
      !this.isDtsEnvironment) {
      const {status, podIP} = this.props.run.value;
      return status.toLowerCase() === 'running' &&
        roleModel.executeAllowed(this.props.run.value) &&
        podIP;
    }
    return false;
  }

  @computed
  get endpointAvailable () {
    if (this.props.run.loaded && this.initializeEnvironmentFinished) {
      const {serviceUrl} = this.props.run.value;
      return serviceUrl;
    }
    return false;
  }

  onChangeCollapsedPanels = (tabs) => {
    this.setState({
      openedPanels: tabs
    });
  };

  openShareDialog = () => {
    this.setState({
      shareDialogOpened: true
    });
  };

  closeShareDialog = () => {
    this.setState({
      shareDialogOpened: false
    });
  };

  saveShareSids = async (sids) => {
    const hide = message.loading('Updating sharing info...', -1);
    const request = new PipelineRunUpdateSids(this.props.runId);
    await request.send(sids);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await this.props.run.fetch();
      hide();
      this.closeShareDialog();
    }
  };

  renderNestedRuns = () => {
    if (!this.props.nestedRuns.loaded ||
      !this.props.nestedRuns.value ||
      this.props.nestedRuns.value.length === 0) {
      return null;
    }
    const {total} = this.props.nestedRuns;
    const nestedRuns = (this.props.nestedRuns.value || [])
      .map(r => r);
    nestedRuns.sort((rA, rB) => {
      if (rA.id > rB.id) {
        return 1;
      }
      if (rA.id < rB.id) {
        return -1;
      }
      return 0;
    });
    const renderSingleRun = (run, index) => {
      const {
        dockerImage,
        endDate,
        id,
        pipelineName,
        startDate,
        version
      } = run;
      let executable = (dockerImage || '').split('/').pop();
      if (pipelineName) {
        executable = pipelineName;
        if (version) {
          executable = `${pipelineName} (${version})`;
        }
      }
      const duration = displayDuration(startDate, endDate);
      return (
        <Link
          key={index}
          className={styles.nestedRun}
          to={`/run/${run.id}`}
        >
          <StatusIcon run={run} small />
          <b className={styles.runId}>{id}</b>
          {executable && <span className={styles.details}>{executable}</span>}
          {duration && <span className={styles.details}>{duration}</span>}
        </Link>
      );
    };
    return (
      <tr>
        <th
          className={styles.nestedRunsHeader}
        >
          Nested runs:
        </th>
        <td
          className={styles.nestedRuns}
        >
          {nestedRuns.map(renderSingleRun)}
          {
            total > MAX_NESTED_RUNS_TO_DISPLAY &&
            <Link
              className={styles.allNestedRuns}
              to={`/runs/filter?search=${encodeURIComponent(`parent.id=${this.props.runId}`)}`}
            >
              show all {total} runs
            </Link>
          }
        </td>
      </tr>
    );
  };

  render () {
    if (this.props.run.error) {
      return <Alert type="error" message={this.props.run.error} />;
    }
    const {router: {location}} = this.props;

    let Details;
    let Parameters;
    let InstanceDetails;
    let Title;
    let PauseResumeButton;
    let ActionButton;
    let SSHButton;
    let ExportLogsButton;
    let SwitchTimingsButton;
    let ShowLaunchCommandsButton;
    let SwitchModeButton;
    let CommitStatusButton;
    let dockerImage;
    let ResumeFailureReason;

    let selectedTask = null;
    if (this.props.task) {
      selectedTask = this.props.task.name;
      const params = [];
      if (this.props.task.parameters) {
        params.push(`parameters=${this.props.task.parameters}`);
      }
      if (this.props.task.instance) {
        params.push(`instance=${this.props.task.instance}`);
      }
      if (params.length) {
        selectedTask += `?${params.join('&')}`;
      }
      selectedTask = decodeURIComponent(selectedTask);
    }

    if (!this.props.run.loaded) {
      Title = <h1>Run </h1>;
      Details = <div>Loading details...</div>;
    } else {
      const pipelineName = this.props.run.value.pipelineName;
      const configName = this.props.run.value.configName;
      const pipelineId = this.props.run.value.pipelineId;
      const version = this.props.run.value.version;
      const owner = (this.props.run.value.owner || '').toLowerCase();
      const podIP = this.props.run.value.podIP;
      const podStatus = this.props.run.value.podStatus;
      let endpoints;
      let share;
      if (this.endpointAvailable) {
        const urls = parseRunServiceUrl(this.props.run.value.serviceUrl);
        endpoints = (
          <tr style={{fontSize: '11pt'}}>
            <th style={{verticalAlign: 'top'}}>{urls.length > 1 ? 'Endpoints: ': 'Endpoint: '}</th>
            <td>
              <ul>
                {
                  urls.map((url, index) =>
                    <li key={index}><a href={url.url} target="_blank">{url.name || url.url}</a></li>
                  )
                }
              </ul>
            </td>
          </tr>
        );
      }
      if (
        this.initializeEnvironmentFinished &&
        this.props.run.value.status === 'RUNNING' &&
        roleModel.isOwner(this.props.run.value)
      ) {
        let shareList = 'Not shared (click to configure)';
        if (this.props.run.value.runSids && (this.props.run.value.runSids || []).length > 0) {
          shareList = (this.props.run.value.runSids || [])
            .map((s, index, array) => {
              return (
                <span
                  key={s.name}
                  style={{marginRight: 5}}>
                  <UserName userName={s.name} />
                  {
                    index < array.length - 1 ? ',' : undefined
                  }
                </span>
              );
            });
        }
        share = (
          <tr>
            <th>Share with:</th>
            <td><a onClick={this.openShareDialog}>{shareList}</a></td>
          </tr>
        );
      }
      const pipeline = pipelineName && pipelineId && version
        ? {name: pipelineName, id: pipelineId, version: version}
        : undefined;
      const {runId}= this.props.params;

      const {
        startDate,
        endDate,
        pipelineRunParameters,
        status,
        instance,
        commitStatus,
        resumeFailureReason
      } = mapResumeFailureReason(this.props.run.value);
      dockerImage = this.props.run.value.dockerImage;
      ResumeFailureReason = resumeFailureReason
        ? (<Alert type="warning" message={resumeFailureReason} />)
        : null;
      const pipelineLink = pipeline
        ? (
          <Link className={styles.pipelineLink} to={`/${pipeline.id}/${pipeline.version}`}>
            {pipeline.name} ({pipeline.version})
          </Link>
        )
        : undefined;

      const failureReason = status === 'FAILURE' && podStatus
        ? <span style={{fontWeight: 'normal', marginLeft: 5}}>({podStatus})</span> : undefined;

      Title = (
        <h1 className={styles.runTitle}>
          <StatusIcon run={this.props.run.value} /><span>Run #{runId}{failureReason} - </span>
          {pipelineLink}
          <span>{pipelineLink && ' -'} Logs</span>
        </h1>
      );
      let startedTime, finishTime;

      if (this.props.runTasks.value.length) {
        startedTime = (
          <tr>
            <th>Started: </th>
            <td>
              {displayDate(this.props.runTasks.value[0].started)} (
              {displayDuration(startDate, this.props.runTasks.value[0].started)}
              )
            </td>
          </tr>);
        if (status === 'RUNNING') {
          finishTime = <tr><th>Running for: </th><td>{this.runningTime}</td></tr>;
        } else if (status === 'SUCCESS' || status === 'FAILURE') {
          finishTime = (
            <tr>
              <th>Finished: </th>
              <td>
                {displayDate(endDate)} (
                {displayDuration(this.props.runTasks.value[0].started, endDate)}
                )
              </td>
            </tr>);
        } else {
          finishTime = (
            <tr>
              <th>Stopped at: </th>
              <td>
                {displayDate(endDate)} (
                {displayDuration(this.props.runTasks.value[0].started, endDate)}
                )
              </td>
            </tr>);
        }
      } else {
        startedTime = <tr><th>Waiting for: </th><td>{this.timeFromStart}</td></tr>;
      }

      let price;
      if (this.props.run.value.pricePerHour) {
        const adjustPrice = (value) => {
          let cents = Math.ceil(value * 100);
          if (cents < 1) {
            cents = 1;
          }
          return cents / 100;
        };
        price = (
          <tr>
            <th>Estimated price:</th>
            <td>{adjustPrice(evaluateRunDuration(this.props.run.value) * this.props.run.value.pricePerHour).toFixed(2)}
              $
            </td>
          </tr>
        );
      }

      Details =
        <div>
          <table className={styles.runDetailsTable}>
            <tbody>
              {endpoints}
              {share}
              <tr>
                <th>Owner: </th><td><UserName userName={owner}/></td>
              </tr>
              {
                configName ?
                  (
                    <tr>
                      <th>Configuration:</th>
                      <td>{configName}</td>
                    </tr>
                  ) : undefined
              }
              <tr>
                <th>Scheduled: </th><td>{displayDate(startDate)}</td>
              </tr>
              {startedTime}
              {finishTime}
              {price}
              {this.renderNestedRuns()}
            </tbody>
          </table>
        </div>;

      let filteredRunParameters = (pipelineRunParameters || []).filter(p => p.name && p.value);
      const getParameterType = p => {
        switch ((p.type || '').toLowerCase()) {
          case 'common':
          case 'input':
            return 'input';
          case 'output':
            return 'output';
          default:
            return 'general';
        }
      };
      const types = ['input', 'output', 'general'];
      if (filteredRunParameters.length > 0) {
        const switchResolvedValuesButton = (
          <a
            onClick={this.switchResolvedValues}>
            {this.state.resolvedValues ? 'SHOW ORIGINAL' : 'SHOW RESOLVED'}
          </a>
        );
        Parameters = (
          <Collapse
            bordered={false}>
            <Collapse.Panel header="Parameters">
              <Row type="flex" justify="end" style={{position: 'absolute', right: 0}}>
                {switchResolvedValuesButton}
              </Row>
              <table>
                <tbody>
                  {
                    types.map((type, index) => {
                      const parameters = filteredRunParameters
                        .filter(p => getParameterType(p) === type);
                      if (parameters.length === 0) {
                        return [];
                      }
                      const rows = [];
                      rows.push((
                        <tr key={`type_${index}`}>
                          <td
                            colSpan={2}
                            style={{
                              fontWeight: 'bold',
                              paddingTop: index === 0 ? 0 : 10
                            }}>
                            {type ? type.toUpperCase() : 'GENERAL'}
                          </td>
                        </tr>
                      ));
                      rows.push(...parameters.map((p, i) => this.renderRunParameter(p, i, type)));
                      return rows;
                    })
                  }
                </tbody>
              </table>
            </Collapse.Panel>
          </Collapse>
        );
      }

      InstanceDetails =
        <Collapse
          bordered={false}
          onChange={this.onChangeCollapsedPanels}
          activeKey={this.state.openedPanels}>
          <Collapse.Panel
            key="instance"
            header={this.renderInstanceHeader(instance, this.props.run.value)}>
            <ul>
              {
                this.renderInstanceDetails(instance, this.props.run.value)
              }
            </ul>
          </Collapse.Panel>
        </Collapse>;
      if (roleModel.executeAllowed(this.props.run.value)) {
        switch (status.toLowerCase()) {
          case 'paused':
            if (roleModel.isOwner(this.props.run.value)) {
              ActionButton = <a style={{color: 'red'}} onClick={() => this.terminatePipeline()}>TERMINATE</a>;
            }
            break;
          case 'running':
          case 'pausing':
          case 'resuming':
            if (roleModel.isOwner(this.props.run.value) && canStopRun(this.props.run.value)) {
              ActionButton = <a style={{color: 'red'}} onClick={() => this.stopPipeline()}>STOP</a>;
            }
            break;
          case 'stopped':
          case 'failure':
          case 'success':
            ActionButton = <a onClick={() => this.reRunPipeline()}>RERUN</a>;
            break;
        }
        if (roleModel.isOwner(this.props.run.value) &&
          this.props.run.value.initialized && !(this.props.run.value.nodeCount > 0) &&
          !(this.props.run.value.parentRunId && this.props.run.value.parentRunId > 0) &&
          this.props.run.value.instance && this.props.run.value.instance.spot !== undefined &&
          !this.props.run.value.instance.spot) {
          switch (status.toLowerCase()) {
            case 'running':
              if (canPauseRun(this.props.run.value)) {
                PauseResumeButton = <a onClick={this.showPauseConfirmDialog}>PAUSE</a>;
              }
              break;
            case 'paused':
              PauseResumeButton = <a onClick={this.showResumeConfirmDialog}>RESUME</a>;
              break;
            case 'pausing':
              PauseResumeButton = <span>PAUSING</span>;
              break;
            case 'resuming':
              PauseResumeButton = <span>RESUMING</span>;
              break;
          }
        }
      }

      if (this.sshEnabled) {
        SSHButton = (<a href={this.props.runSSH.value} target="_blank">SSH</a>);
      }

      if (!(this.props.run.value.nodeCount > 0) &&
        !(this.props.run.value.parentRunId && this.props.run.value.parentRunId > 0) && podIP) {
        if (status.toLowerCase() === 'running' &&
          (commitStatus || '').toLowerCase() !== 'committing' &&
          roleModel.executeAllowed(this.props.run.value)) {
          let previousStatus;
          switch ((commitStatus || '').toLowerCase()) {
            case 'not_committed': break;
            case 'committing': previousStatus = <span><Icon type="loading" /> COMMITTING...</span>; break;
            case 'failure': previousStatus = <span>COMMIT FAILURE</span>; break;
            case 'success': previousStatus = <span>COMMIT SUCCEEDED</span>; break;
            default: break;
          }
          if (previousStatus) {
            CommitStatusButton = (<Row>{previousStatus}. <a onClick={this.openCommitRunForm}>COMMIT</a></Row>);
          } else {
            CommitStatusButton = (<a onClick={this.openCommitRunForm}>COMMIT</a>);
          }
        } else {
          switch ((commitStatus || '').toLowerCase()) {
            case 'not_committed': break;
            case 'committing': CommitStatusButton = <span><Icon type="loading" /> COMMITTING...</span>; break;
            case 'failure': CommitStatusButton = <span>COMMIT FAILURE</span>; break;
            case 'success': CommitStatusButton = <span>COMMIT SUCCEEDED</span>; break;
            default: break;
          }
        }
      }

      if (status !== 'RUNNING') {
        ExportLogsButton = <a onClick={this.exportLog}>EXPORT LOGS</a>;
      }

      let switchModeUrl;
      if (this._pipelineLanguage &&
        graphIsSupportedForLanguage(this._pipelineLanguage)) {
        if (this.props.params.mode.toLowerCase() === 'graph') {
          switchModeUrl = `/run/${this.props.params.runId}/plain`;
        } else {
          switchModeUrl = `/run/${this.props.params.runId}/graph`;
        }
      }

      if (switchModeUrl && selectedTask) {
        switchModeUrl += `/${selectedTask}`;
      }

      SwitchTimingsButton = <a onClick={this.switchTimings}>{this.state.timings ? 'HIDE TIMINGS' : 'SHOW TIMINGS'}</a>;
      if (this.runPayload) {
        ShowLaunchCommandsButton = (
          <a
            onClick={this.showLaunchCommands}>
            LAUNCH COMMAND
          </a>
        );
      }

      SwitchModeButton = switchModeUrl &&
        <AdaptedLink to={switchModeUrl} location={location}>
          {this.props.params.mode.toLowerCase() === 'plain' ? 'GRAPH VIEW' : 'PLAIN VIEW'}
        </AdaptedLink>;
    }

    return (
      <Card
        className={styles.logCard}
        bodyStyle={{
          padding: 10,
          display: 'flex',
          flexDirection: 'column',
          flex: 1,
          overflowY: 'auto'
        }}>
        <Row>
          <Col span={18}>
            <Row type="flex" justify="space-between">
              {Title}
            </Row>
            {
              this.props.run.value.stateReasonMessage &&
              <Row type="flex">
                <Alert
                  message={`Server failure reason: ${this.props.run.value.stateReasonMessage}`}
                  type="error" />
              </Row>
            }
            {ResumeFailureReason}
            <Row>
              {Details}
            </Row>
          </Col>
          <Col span={6}>
            <Row type="flex" justify="end" className={styles.actionButtonsContainer}>
              {PauseResumeButton}{ActionButton}{SSHButton}{ExportLogsButton}
            </Row>
            <br />
            <Row type="flex" justify="end" className={styles.actionButtonsContainer}>
              {SwitchTimingsButton}{SwitchModeButton}{ShowLaunchCommandsButton}
            </Row>
            <br />
            <Row type="flex" justify="end" className={styles.actionButtonsContainer}>
              {CommitStatusButton}
            </Row>
          </Col>
        </Row>
        <Row>
          <Col>
            {Parameters}
          </Col>
        </Row>
        <Row className={styles.rowDetailLast}>
          <Col>
            {InstanceDetails}
          </Col>
        </Row>
        <Row className={styles.fullHeightContainer}>
          {this.renderContent(selectedTask)}
        </Row>
        <ShareWithForm
          endpointsAvailable={!!this.endpointAvailable}
          visible={this.state.shareDialogOpened}
          roles={this.props.roles.loaded ? (this.props.roles.value || []).map(r => r) : []}
          sids={this.props.run.loaded ? (this.props.run.value.runSids || []).map(s => s) : []}
          pending={this.state.operationInProgress}
          onSave={this.operationWrapper(this.saveShareSids)}
          onClose={this.closeShareDialog} />
        <CommitRunDialog
          defaultDockerImage={dockerImage}
          pending={this.state.operationInProgress}
          visible={this.state.commitRun}
          commitCheck={this.commitCheck}
          onCancel={this.closeCommitRunForm}
          onSubmit={this.operationWrapper(this.commitRun)} />
        <LaunchCommand
          payload={this.runPayload}
          visible={this.state.showLaunchCommands}
          onClose={this.hideLaunchCommands}
        />
      </Card>);
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.runId !== this.props.runId) {
      this.language = null;
      this._pipelineLanguage = null;
    }
  }

  componentDidUpdate () {
    if (this.language === null && this.props.run.loaded) {
      if (this.props.run.value.pipelineId && this.props.run.value.version) {
        this.language = pipelines.getLanguage(
          this.props.run.value.pipelineId,
          this.props.run.value.version
        );
        (async () => {
          await this.language.fetchIfNeededOrWait();
          if (this.language.error) {
            this._pipelineLanguage = 'other';
          } else {
            this._pipelineLanguage = this.language.value.toLowerCase();
          }
        })();
      } else {
        this._pipelineLanguage = 'other';
      }
    }
    if (!this.props.runTasks.pending && this.graph) {
      this.graph.updateData();
    }
    if (this.props.run.loaded) {
      const {status} = this.props.run.value;
      if (status === 'RUNNING' || status === 'PAUSING' || status === 'RESUMING') {
        this.props.runTasks.startInterval();
        this.props.nestedRuns.startRefreshInterval();
      } else {
        this.props.run.clearInterval();
        this.props.runTasks.clearInterval();
        this.props.nestedRuns.clearRefreshInterval();
      }
    }
  }
}

export default Logs;
