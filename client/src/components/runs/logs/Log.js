/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import {Link} from 'react-router';
import FileSaver from 'file-saver';
import {
  Alert,
  Card,
  Col,
  Collapse,
  Icon,
  Menu,
  message,
  Modal,
  Popover,
  Row,
  Spin
} from 'antd';
import SplitPane from 'react-split-pane';
import PausePipeline from '../../../models/pipelines/PausePipeline';
import ResumePipeline from '../../../models/pipelines/ResumePipeline';
import PipelineExportLog from '../../../models/pipelines/PipelineExportLog';
import pipelineRunSSHCache from '../../../models/pipelines/PipelineRunSSHCache';
import PipelineRunKubeServicesLoad from '../../../models/pipelines/PipelineRunKubeServicesLoad';
import pipelineRunFSBrowserCache from '../../../models/pipelines/PipelineRunFSBrowserCache';
import PipelineRunCommit from '../../../models/pipelines/PipelineRunCommit';
import pipelines from '../../../models/pipelines/Pipelines';
import Roles from '../../../models/user/Roles';
import PipelineRunUpdateSids, {AccessTypes} from '../../../models/pipelines/PipelineRunUpdateSids';
import {
  stopRun,
  canCommitRun,
  canPauseRun,
  canStopRun,
  runPipelineActions,
  terminateRun,
  openReRunForm,
  runIsCommittable
} from '../actions';
import connect from '../../../utils/connect';
import displayDate from '../../../utils/displayDate';
import displayDuration, {displayDurationInSeconds} from '../../../utils/displayDuration';
import roleModel from '../../../utils/roleModel';
import localization from '../../../utils/localization';
import parseQueryParameters from '../../../utils/queryParameters';
import styles from './Log.css';
import AdaptedLink from '../../special/AdaptedLink';
import {getRunSpotTypeName} from '../../special/spot-instance-names';
import {TaskLink} from './tasks/TaskLink';
import RunTaskLogs from '../run-task-logs';
import StatusIcon, {Statuses} from '../../special/run-status-icon';
import UserName from '../../special/UserName';
import WorkflowGraph from '../../pipelines/version/graph/WorkflowGraph';
import {graphIsSupportedForLanguage} from '../../pipelines/version/graph/visualization';
import LoadingView from '../../special/LoadingView';
import AWSRegionTag from '../../special/AWSRegionTag';
import DataStorageList from '../controls/data-storage-list';
import CommitRunDialog from './forms/CommitRunDialog';
import ShareWithForm, {ROLE_ALL, shouldCombineRoles} from './forms/ShareWithForm';
import DockerImageLink from './DockerImageLink';
import {getResumeFailureReason} from '../utilities/map-resume-failure-reason';
import RunTags from '../run-tags';
import RunSchedules from '../../../models/runSchedule/RunSchedules';
import UpdateRunSchedules from '../../../models/runSchedule/UpdateRunSchedules';
import RemoveRunSchedules from '../../../models/runSchedule/RemoveRunSchedules';
import CreateRunSchedules from '../../../models/runSchedule/CreateRunSchedules';
import RunSchedulingList from '../run-scheduling/run-sheduling-list';
import LaunchCommand from '../../pipelines/launch/form/utilities/launch-command';
import JobEstimatedPriceInfo from '../../special/job-estimated-price-info';
import {CP_CAP_LIMIT_MOUNTS} from '../../pipelines/launch/form/utilities/parameters';
import RunName from '../run-name';
import VSActions from '../../versioned-storages/vs-actions';
import MultizoneUrl from '../../special/multizone-url';
import {parseRunServiceUrlConfiguration} from '../../../utils/multizone';
import getMaintenanceDisabledButton from '../controls/get-maintenance-mode-disabled-button';
import confirmPause from '../actions/pause-confirmation';
import getRunDurationInfo from '../../../utils/run-duration';
import RunTimelineInfo from './misc/run-timeline-info';
import evaluateRunPrice from '../../../utils/evaluate-run-price';
import DataStorageLink from '../../special/data-storage-link';
import fetchRunInfo from './misc/fetch-run-info';
import RestartedRunsInfo from './misc/restarted-runs-info';
import NestedRunsModal from './forms/NestedRunsModal';

const FIRE_CLOUD_ENVIRONMENT = 'FIRECLOUD';
const DTS_ENVIRONMENT = 'DTS';
const MAX_PARAMETER_VALUES_TO_DISPLAY = 5;
const MAX_NESTED_RUNS_TO_DISPLAY = 10;
const MAX_KUBE_SERVICES_TO_DISPLAY = 3;

@connect({
  pipelines
})
@localization.localizedComponent
@runPipelineActions
@inject('preferences', 'dtsList', 'multiZoneManager', 'dockerRegistries', 'preferences')
@VSActions.check
@inject(({routing, pipelines, multiZoneManager}, {params}) => {
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
    runSSH: pipelineRunSSHCache.getPipelineRunSSH(params.runId),
    runFSBrowser: pipelineRunFSBrowserCache.getPipelineRunFSBrowser(params.runId),
    runKubeServices: new PipelineRunKubeServicesLoad(params.runId),
    task,
    pipelines,
    roles: new Roles(),
    routing,
    multiZone: multiZoneManager
  };
})
@observer
class Logs extends localization.LocalizedReactComponent {
  state = {
    run: undefined,
    pending: false,
    error: undefined,
    showActiveWorkersOnly: false,
    nestedRuns: [],
    hasNestedRuns: false,
    totalNestedRuns: 0,
    nestedRunsPending: false,
    runTasks: [],
    language: undefined,
    timings: false,
    commitRun: false,
    resolvedValues: true,
    operationInProgress: false,
    openedPanels: [],
    shareDialogOpened: false,
    scheduleSaveInProgress: false,
    showLaunchCommands: false,
    commitAllowed: false,
    nestedRunsModalVisible: false
  };

  @observable runScheduleRequest;

  componentDidMount () {
    this.updateFromProps();
  }

  componentWillUnmount () {
    this.stopAutoUpdate();
  }

  fetchToken = 0;

  stopAutoUpdate = () => {
    if (typeof this.stop === 'function') {
      this.stop();
    }
    this.stop = undefined;
    this.reFetchRunInfo = undefined;
  };

  updateFromProps = () => {
    this.stopAutoUpdate();
    const {
      runId,
      preferences,
      dockerRegistries
    } = this.props;
    if (runId) {
      this.fetchToken += 1;
      const token = this.fetchToken;
      this.setState({
        run: undefined,
        pending: true,
        error: undefined,
        nestedRuns: [],
        hasNestedRuns: false,
        totalNestedRuns: 0,
        nestedRunsPending: false,
        showActiveWorkersOnly: false,
        runTasks: [],
        language: undefined
      }, async () => {
        const commit = (data = {}) => {
          if (token === this.fetchToken) {
            this.setState({pending: false, ...data});
          }
        };
        try {
          this.runScheduleRequest = new RunSchedules(runId);
          (this.runScheduleRequest.fetch)();
          const {
            stop,
            fetch: reFetch
          } = await fetchRunInfo(runId, commit, {
            preferences,
            dockerRegistries,
            maxNestedRunsToDisplay: MAX_NESTED_RUNS_TO_DISPLAY
          });
          this.stop = stop;
          this.reFetchRunInfo = reFetch;
        } catch (error) {
          commit({error: error.message});
        }
      });
    } else {
      this.runScheduleRequest = undefined;
      this.setState({
        run: undefined,
        pending: false,
        error: undefined,
        nestedRuns: [],
        hasNestedRuns: false,
        totalNestedRuns: 0,
        nestedRunsPending: false,
        showActiveWorkersOnly: false,
        runTasks: [],
        language: undefined
      });
    }
  }

  @computed
  get runSchedule () {
    if (!this.runScheduleRequest || !this.runScheduleRequest.loaded) {
      return [];
    }

    return (this.runScheduleRequest.value || []).map(i => i);
  }

  @computed
  get runPayload () {
    const {preferences} = this.props;
    const {run} = this.state;
    if (run && preferences.loaded) {
      const payload = {
        instanceType: undefined,
        hddSize: undefined,
        timeout: run.timeout,
        cmdTemplate: run.cmdTemplate,
        nodeCount: run.nodeCount,
        dockerImage: run.dockerImage,
        pipelineId: run.pipelineId,
        version: run.version,
        params: {},
        isSpot: preferences.useSpot,
        cloudRegionId: undefined,
        prettyUrl: run.prettyUrl,
        nonPause: run.nonPause,
        configurationName: run.configName,
        executionEnvironment: undefined
      };
      if (run.instance) {
        payload.instanceType = run.instance.nodeType;
        payload.hddSize = run.instance.nodeDisk;
        payload.isSpot = run.instance.spot;
        payload.cloudRegionId = run.instance.cloudRegionId;
      }
      if (run.executionPreferences) {
        payload.executionEnvironment = run.executionPreferences.environment;
      }
      if (run.pipelineRunParameters) {
        for (let i = 0; i < run.pipelineRunParameters.length; i++) {
          const param = run.pipelineRunParameters[i];
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

  @computed
  get maintenanceMode () {
    const {preferences} = this.props;
    if (preferences && preferences.loaded) {
      return preferences.systemMaintenanceMode;
    }
    return false;
  }

  get combineRolesIntoAllRoles () {
    const {run} = this.state;
    const {runSids = []} = run || {};
    return {
      ssh: shouldCombineRoles(runSids, ROLE_ALL.includedRoles, AccessTypes.ssh),
      endpoint: shouldCombineRoles(runSids, ROLE_ALL.includedRoles, AccessTypes.endpoint)
    };
  }

  exportLog = async () => {
    const {runId} = this.props;
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

  refreshRunInfo = async () => {
    if (typeof this.reFetchRunInfo === 'function') {
      await this.reFetchRunInfo();
    }
  };

  stopPipeline = () => {
    const {
      run
    } = this.state;
    if (run) {
      return stopRun(this, this.refreshRunInfo)(run);
    }
  };

  terminatePipeline = () => {
    const {
      run
    } = this.state;
    if (run) {
      return terminateRun(this, this.refreshRunInfo)(run);
    }
  };

  showPauseConfirmDialog = async () => {
    const {run} = this.state;
    const confirmed = await confirmPause({
      id: this.props.runId,
      run
    });
    if (confirmed) {
      await this.pausePipeline();
    }
  };

  showResumeConfirmDialog = () => {
    const {run} = this.state;
    if (run) {
      const dockerImageParts = (run.dockerImage || '').split('/');
      const imageName = dockerImageParts[dockerImageParts.length - 1].split(':')[0];
      const pipelineName = run.pipelineName || imageName || this.localizedString('pipeline');
      Modal.confirm({
        title: `Do you want to resume ${pipelineName}?`,
        style: {
          wordWrap: 'break-word'
        },
        onOk: () => this.resumePipeline(),
        okText: 'RESUME',
        cancelText: 'CANCEL'
      });
    }
  };

  pausePipeline = async (e) => {
    if (e) {
      e.stopPropagation();
    }
    const {runId: id} = this.props;
    const pausePipeline = new PausePipeline(id);
    await pausePipeline.send({});
    if (pausePipeline.error) {
      message.error(pausePipeline.error);
    }
    this.refreshRunInfo();
  };

  resumePipeline = async (e) => {
    if (e) {
      e.stopPropagation();
    }
    const {runId: id} = this.props;
    const resumePipeline = new ResumePipeline(id);
    await resumePipeline.send({});
    if (resumePipeline.error) {
      message.error(resumePipeline.error);
    }
    this.refreshRunInfo();
  };

  reRunPipeline = () => {
    const {run} = this.state;
    if (run) {
      return openReRunForm(run, this.props);
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
    if (/^(input|output|common|path)$/i.test(runParameter.type)) {
      const valueParts = valueSelector().split(/[,|]/);
      return (
        <tr key={runParameter.name}>
          <td className={styles.taskParameterName}>
            <span>{runParameter.name}: </span>
          </td>
          <td>
            <ul>
              {
                valueParts.map((value, index) => (
                  <li
                    key={`${value}-${index}`}
                  >
                    <DataStorageLink
                      key={`link-${value}-${index}`}
                      path={value}
                      isFolder={/^output$/i.test(runParameter.type) ? true : undefined}
                    >
                      {value}
                    </DataStorageLink>
                  </li>
                ))
              }
            </ul>
          </td>
        </tr>
      );
    }
    if (runParameter.name === 'parent-id' && parseInt(valueSelector()) !== 0) {
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
    if (runParameter.name === CP_CAP_LIMIT_MOUNTS) {
      const values = (valueSelector() || '').split(',').map(v => v.trim());
      const isNone = /^none$/i.test(valueSelector());
      return (
        <tr key={runParameter.name}>
          <td
            className={styles.taskParameterName}
            style={{verticalAlign: 'middle'}}
          >
            {runParameter.name}:
          </td>
          <td>
            {
              isNone && (<span>None</span>)
            }
            {
              !isNone && (<DataStorageList identifiers={values} />)
            }
          </td>
        </tr>
      );
    }
    if (/^metadata$/i.test(runParameter.type)) {
      const [metadataFolder, metadataClassName, itemsStr] = (valueSelector() || '').split(':');
      if (metadataFolder && metadataClassName && itemsStr) {
        const itemsCount = itemsStr.split(/[,;]/).length;
        return (
          <tr
            key={runParameter.name}>
            <td className={styles.taskParameterName}>{runParameter.name}:</td>
            <td>
              <Link to={`/folder/${metadataFolder}/metadata/${metadataClassName}`}>
                {metadataClassName} ({itemsCount})
              </Link>
            </td>
          </tr>
        );
      }
      return (
        <tr
          key={runParameter.name}>
          <td className={styles.taskParameterName}>{runParameter.name}:</td>
          <td>{valueSelector()}</td>
        </tr>
      );
    }
    let values = (valueSelector() || '').split(',').map(v => v.trim());
    if (values.length === 1) {
      return (
        <tr
          key={runParameter.name}>
          <td className={styles.taskParameterName}>{runParameter.name}:</td>
          <td>{values[0]}</td>
        </tr>
      );
    }
    if (values.length <= MAX_PARAMETER_VALUES_TO_DISPLAY + 1) {
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
    }
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

  buttonsWrapper = (button) => button
    ? (<div style={{lineHeight: '29px', height: '29px'}}>{button}</div>)
    : undefined;

  renderInstanceHeader = (instance, run) => {
    if (this.state.openedPanels.indexOf('instance') >= 0 || !run) {
      return 'Instance';
    }
    const details = [];
    if (instance) {
      if (RunTags.shouldDisplayTags(run, this.props.preferences, true)) {
        details.push({
          key: 'tags',
          value: (
            <RunTags
              run={run}
              onlyKnown
            />
          ),
          additionalStyle: {backgroundColor: 'transparent', border: '1px solid transparent'}
        });
      }
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
                  style={{verticalAlign: 'top', marginLeft: -3, fontSize: 'larger'}}
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
                style={{verticalAlign: 'top', fontSize: 'larger'}}
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
        <Row>
          Instance: {
            details.map(d => {
              return (
                <span
                  key={d.key}
                  style={d.additionalStyle}
                  className={
                    classNames(
                      styles.instanceHeaderItem,
                      'cp-run-instance-tag'
                    )
                  }
                >
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

  saveRunSchedule = async (rules) => {
    const {runId} = this.props;
    const toRemove = [];
    const toUpdate = [];
    const toCreate = [];

    /**
     * Returns true if the rule has changes
     * */
    const ruleChanged = ({scheduleId, action, cronExpression, timeZone, removed}) => {
      if (!scheduleId || removed) {
        return true;
      }
      const [existed] = this.runSchedule.filter(r => r.scheduleId === scheduleId);
      if (!existed) {
        return true;
      }

      return existed.action !== action ||
        existed.cronExpression !== cronExpression ||
        existed.timeZone !== timeZone;
    };

    rules.forEach(({scheduleId, action, cronExpression, timeZone, removed}) => {
      if (!ruleChanged({scheduleId, action, cronExpression, timeZone, removed})) {
        return;
      }
      const payload = {scheduleId, action, cronExpression, timeZone};
      if (scheduleId) {
        if (removed) {
          toRemove.push(payload);
        } else {
          toUpdate.push(payload);
        }
      } else if (!removed) {
        toCreate.push(payload);
      }
    });
    if (toRemove.length > 0) {
      const request = new RemoveRunSchedules(runId);
      await request.send(toRemove);
      if (request.error) {
        message.error(request.error);
      }
    }
    if (toUpdate.length > 0) {
      const request = new UpdateRunSchedules(runId);
      await request.send(toUpdate);
      if (request.error) {
        message.error(request.error);
      }
    }
    if (toCreate.length > 0) {
      const request = new CreateRunSchedules(runId);
      await request.send(toCreate);
      if (request.error) {
        message.error(request.error);
      }
    }
  };

  onRunScheduleSubmit = (rules) => {
    this.setState({scheduleSaveInProgress: true}, async () => {
      await this.saveRunSchedule(rules);
      if (this.runScheduleRequest) {
        await this.runScheduleRequest.fetch();
      }
      this.setState({scheduleSaveInProgress: false});
    });
  };

  renderRunSchedule = (instance) => {
    const {preferences} = this.props;
    const {
      run,
      scheduleSaveInProgress
    } = this.state;
    if (!run) {
      return null;
    }
    const allowEditing = roleModel.isOwner(run) &&
      !(run.nodeCount > 0) &&
      !(run.parentRunId && run.parentRunId > 0) &&
      instance && instance.spot !== undefined && !instance.spot &&
      ![Statuses.failure, Statuses.stopped, Statuses.success].includes(run.status);

    if (!allowEditing && this.runSchedule.length === 0) {
      return null;
    }
    const configuration = run && run.pipelineId
      ? preferences.pipelineJobMaintenanceConfiguration
      : preferences.toolJobMaintenanceConfiguration;
    if (!configuration.pause && !configuration.resume) {
      return null;
    }
    return (
      <tr>
        <th className={styles.runScheduleHeader}>Maintenance: </th>
        <td className={styles.runSchedule}>
          <RunSchedulingList
            availableActions={[
              configuration.pause ? RunSchedulingList.Actions.pause : false,
              configuration.resume ? RunSchedulingList.Actions.resume : false
            ].filter(Boolean)}
            pending={
              !this.runScheduleRequest ||
              this.runScheduleRequest.pending ||
              scheduleSaveInProgress
            }
            onSubmit={this.onRunScheduleSubmit}
            allowEdit={allowEditing}
            rules={this.runSchedule}
          />
        </td>
      </tr>
    );
  };

  renderInstanceDetails = (instance, run) => {
    const details = [];
    if (instance && run) {
      if (RunTags.shouldDisplayTags(run, this.props.preferences)) {
        const {routing: {location}} = this.props;
        details.push({
          key: 'Tags',
          value: (
            <RunTags
              run={run}
              location={location}
              overflow={false}
            />
          )
        });
      }
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
                style={{marginLeft: -5, verticalAlign: 'top', fontSize: 'larger'}} />
            )
          });
        }
        if (instance.nodeType) {
          details.push({key: 'Node type', value: `${instance.nodeType}`});
        }
        details.push(
          {key: 'Price type', value: getRunSpotTypeName({instance})}
        );
        if (instance.nodeDisk) {
          details.push({key: 'Disk', value: `${instance.nodeDisk} Gb`});
        }
      } else {
        if (run.executionPreferences && run.executionPreferences.coresNumber) {
          details.push({key: 'Cores', value: `${run.executionPreferences.coresNumber}`});
        }
      }
      if (instance.nodeIP) {
        const {startDate, endDate} = run;
        const parts = [
          startDate && `from=${encodeURIComponent(startDate)}`,
          endDate && `to=${encodeURIComponent(endDate)}`
        ].filter(Boolean);
        const query = parts.length > 0 ? `?${parts.join('&')}` : '';
        if (instance.nodeName) {
          details.push({
            key: 'IP',
            value: (
              <Link to={`/cluster/${instance.nodeName}/monitor${query}`}>
                {instance.nodeName} ({instance.nodeIP})
              </Link>
            )});
        } else {
          const parts = instance.nodeIP.split('.');
          if (parts.length === 4) {
            details.push({
              key: 'IP',
              value: (
                <Link to={`/cluster/ip-${parts.join('-')}/monitor${query}`}>
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
      const taskUrl = this.getTaskUrl(task);
      const url = `/run/${this.props.params.runId}/${this.props.params.mode}/${taskUrl}`;
      this.props.router.push(url);
    } else {
      const url = `/run/${this.props.params.runId}/${this.props.params.mode}`;
      this.props.router.push(url);
    }
  };

  // For WDL pipelines actual task name may be like 'cromwell_<some id>_<task name>,
  // so we need to process this format as well.
  getTask = ({task}) => {
    const {
      runTasks = []
    } = this.state;
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
          const maskPart = maskParts.find(p => p.key.toLowerCase() === parts[i].key.toLowerCase());
          if (maskPart && !maskPart.value.startsWith('&') &&
            maskPart.value.toLowerCase() !== parts[i].value.toLowerCase()) {
            return false;
          }
        }
        return true;
      };
      // trying ot get task state by received task name:
      let taskState = runTasks.find(t =>
        t.name === task.name && parametersMatchFn(task.parameters, t.parameters));
      if (!taskState) {
        // trying to get task state by name format 'cromwell_<some id>_<task name>:
        const regExp = new RegExp(`^cromwell_[\\da-zA-Z]+_${task.name}$`, 'i');
        taskState = runTasks.find(t => regExp.test(t.name));
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
    const {run} = this.state;
    if (!run) {
      return (
        <div
          className={styles.container}
        >
          <Spin />
        </div>
      );
    }
    const {
      status,
      pipelineId,
      version
    } = this.state;
    if (pipelineId && version) {
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
            resizerClassName="cp-split-panel-resizer"
            resizerStyle={{
              width: 8,
              margin: 0,
              cursor: 'col-resize',
              boxSizing: 'border-box',
              backgroundClip: 'padding',
              zIndex: 1
            }}>
            <div style={{display: 'flex', flex: 1}}>
              <WorkflowGraph
                canEdit={false}
                onGraphReady={(graph) => { this.graph = graph; }}
                pipelineId={pipelineId}
                version={version}
                onSelect={this.onSelect}
                getNodeInfo={this.getNodeAdditionalInfo}
                selectedTaskId={selectedTask} />
            </div>
            <div
              className={styles.logContent}>
              <RunTaskLogs
                className={styles.logs}
                runId={Number(this.props.runId)}
                taskName={this.props.task ? this.props.task.name : undefined}
                taskParameters={this.props.task ? this.props.task.parameters : undefined}
                taskInstance={this.props.task ? this.props.task.instance : undefined}
                autoUpdate={/^(running|pausing|resuming)$/i.test(status)}
              />
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
    const {runId} = this.props.params;
    const {
      timings,
      run,
      pending,
      runTasks = []
    } = this.state;
    const {
      status
    } = run || {};
    const selectedTask = this.props.task ? this.getTaskUrl(this.props.task) : null;
    let Tasks;

    if (pending) {
      Tasks = <Menu.Item key={-3}>...Loading</Menu.Item>;
    } else if (runTasks.length === 0) {
      Tasks = <Menu.Item key={-2}>No tasks</Menu.Item>;
    } else {
      Tasks = runTasks.map((task, index) => (
        <Menu.Item key={this.getTaskUrl(task, index)}>
          <TaskLink
            to={`/run/${runId}/${this.props.params.mode}/${this.getTaskUrl(task)}`}
            location={location}
            task={task}
            timings={timings} />
        </Menu.Item>
      ));
    }

    const SwitchTimingsButton = (
      <div className={styles.timingBtn}>
        <a onClick={this.switchTimings}>
          <Icon style={{fontSize: 18}}
            type={timings ? 'clock-circle' : 'clock-circle-o'} />
        </a>
      </div>
    );

    return (
      <Row type="flex" style={{flex: 1}}>
        <SplitPane
          style={{display: 'flex', flex: 1, minHeight: 500}}
          defaultSize={300}
          pane1Style={{display: 'flex', flexDirection: 'column'}}
          pane2Style={{display: 'flex', flexDirection: 'column'}}
          resizerClassName="cp-split-panel-resizer"
          resizerStyle={{
            width: 8,
            margin: 0,
            cursor: 'col-resize',
            boxSizing: 'border-box',
            backgroundClip: 'padding',
            zIndex: 1
          }}>
          <div style={{display: 'flex', flex: 1, height: '100%', overflowY: 'auto'}}>
            {SwitchTimingsButton}
            <Menu
              selectedKeys={selectedTask ? [selectedTask] : []}
              mode="inline"
              className={this.state.timings ? styles.taskListTimings : styles.taskList}>
              {Tasks}
            </Menu>
          </div>
          <div
            className={styles.logContent}>
            <RunTaskLogs
              className={styles.logs}
              runId={Number(this.props.runId)}
              taskName={this.props.task ? this.props.task.name : undefined}
              taskParameters={this.props.task ? this.props.task.parameters : undefined}
              taskInstance={this.props.task ? this.props.task.instance : undefined}
              autoUpdate={/^(running|pausing|resuming)$/i.test(status)}
            />
          </div>
        </SplitPane>
      </Row>
    );
  }

  renderContent () {
    const {
      pending,
      language
    } = this.state;
    if (pending) {
      return (
        <LoadingView />
      );
    }
    if (graphIsSupportedForLanguage(language)) {
      if (this.props.params.mode.toLowerCase() === 'plain') {
        return this.renderContentPlainMode();
      } else {
        return this.renderContentGraphMode();
      }
    } else {
      return this.renderContentPlainMode();
    }
  }

  @computed
  get timeFromStart () {
    const {run} = this.state;
    if (!run) {
      return '';
    }
    const {startDate} = run;
    return displayDuration(startDate);
  }

  @computed
  get runningTime () {
    const {
      runTasks = [],
      pending
    } = this.state;
    if (pending || runTasks.length === 0) {
      return '';
    }
    return displayDuration(runTasks[0].started);
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

  openCommitRunForm = async () => {
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
    const {run} = this.state;
    if (!run) {
      return;
    }
    const {
      runId
    } = this.props;
    if ((run.status || '').toLowerCase() !== 'running') {
      message.error('You can commit only running pipelines');
      this.closeCommitRunForm();
    } else {
      const request = new PipelineRunCommit(runId);
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
        await this.refreshRunInfo();
        this.closeCommitRunForm();
      }
    }
  };

  @computed
  get isDtsEnvironment () {
    const {run} = this.state;
    return run && run.executionPreferences &&
      run.executionPreferences.environment === DTS_ENVIRONMENT;
  }

  @computed
  get isFireCloudEnvironment () {
    const {run} = this.state;
    return run && run.executionPreferences &&
      run.executionPreferences.environment === FIRE_CLOUD_ENVIRONMENT;
  }

  @computed
  get initializeEnvironmentFinished () {
    const {run} = this.state;
    return run && run.initialized;
  }

  @computed
  get sshEnabled () {
    const {run} = this.state;
    if (
      run &&
      this.props.runSSH.loaded &&
      this.initializeEnvironmentFinished &&
      !this.isDtsEnvironment
    ) {
      const {status, podIP, sshPassword} = run;
      return status.toLowerCase() === 'running' &&
        (
          roleModel.executeAllowed(run) ||
          sshPassword
        ) &&
        podIP;
    }
    return false;
  }

  @computed
  get fsBrowserEnabled () {
    const {run} = this.state;
    if (
      run &&
      this.props.runFSBrowser.loaded &&
      this.initializeEnvironmentFinished &&
      !this.isDtsEnvironment
    ) {
      const {
        status,
        platform,
        podIP,
        pipelineRunParameters = []
      } = run;
      if (/^windows$/i.test(platform)) {
        return false;
      }
      const cpFSBrowserEnabled = pipelineRunParameters
        .find(p => /^CP_FSBROWSER_ENABLED$/i.test(p.name));
      if (cpFSBrowserEnabled && `${cpFSBrowserEnabled.value}` === 'false') {
        return false;
      }
      return status.toLowerCase() === 'running' &&
        roleModel.executeAllowed(run) &&
        podIP;
    }
    return false;
  }

  @computed
  get endpointAvailable () {
    const {run} = this.state;
    if (run && this.initializeEnvironmentFinished) {
      return run.serviceUrl;
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
      await this.refreshRunInfo();
      hide();
      this.closeShareDialog();
    }
  };

  renderNestedRuns = () => {
    const {
      nestedRuns: originalNestedRuns = [],
      hasNestedRuns,
      totalNestedRuns = 0,
      nestedRunsPending,
      showActiveWorkersOnly
    } = this.state;
    if (!hasNestedRuns) {
      return null;
    }
    const nestedRuns = originalNestedRuns.slice().sort((rA, rB) => {
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
          className={
            classNames(
              styles.nestedRun,
              'cp-run-nested-run-link'
            )
          }
          to={`/run/${run.id}`}
        >
          <StatusIcon run={run} small />
          <b className={styles.runId}>{id}</b>
          {executable && <span className={styles.details}>{executable}</span>}
          {duration && <span className={styles.details}>{duration}</span>}
        </Link>
      );
    };
    const searchParts = [`parent.id=${this.props.runId}`];
    const search = searchParts.join(' and ');
    const nestedRunsInfos = [
      totalNestedRuns,
      'nested'
    ];
    if (showActiveWorkersOnly) {
      nestedRunsInfos.push('active');
    }
    nestedRunsInfos.push(totalNestedRuns === 1 ? 'run' : 'runs');
    return (
      <tr>
        <th
          className={styles.nestedRunsHeader}
        >
          Nested runs:
        </th>
        <td>
          {
            !nestedRunsPending && (
              <div>
                {nestedRunsInfos.join(' ')}
                {' - '}
                <a
                  onClick={this.openNestedRunsModal}
                  style={{
                    marginLeft: 5
                  }}
                >
                  show cluster usage
                </a>
              </div>
            )
          }
          <div
            className={styles.nestedRuns}
          >
            {
              nestedRuns.length === 0 && nestedRunsPending && (
                <Icon type="loading" />
              )
            }
            {nestedRuns.map(renderSingleRun)}
            <Link
              className={styles.allNestedRuns}
              to={`/runs/filter?search=${encodeURIComponent(search)}`}
            >
              show all nested runs
            </Link>
          </div>
        </td>
      </tr>
    );
  };

  openNestedRunsModal = () => {
    this.setState({
      nestedRunsModalVisible: true
    });
  }

  closeNestedRunsModal = () => {
    this.setState({
      nestedRunsModalVisible: false
    });
  }

  closeNestedRunsModalAndNavigateToRun = (runId) => {
    this.setState({
      nestedRunsModalVisible: false
    }, () => {
      this.props.router.push(`/run/${runId}`);
    });
  };

  render () {
    const {
      run,
      pending,
      error,
      runTasks = [],
      language
    } = this.state;
    if (error) {
      return (
        <Alert
          type="error"
          message={error}
        />
      );
    }
    const {router: {location}} = this.props;

    let Details;
    let Parameters;
    let InstanceDetails;
    let Title;
    let PauseResumeButton;
    let ActionButton;
    let SSHButton;
    let FSBrowserButton;
    let ExportLogsButton;
    let ShowLaunchCommandsButton;
    let SwitchModeButton;
    let CommitStatusButton;
    let ResumeFailureReason;
    let ShowMonitorButton;

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

    const {
      pipelineName,
      configName,
      pipelineId,
      version,
      owner: originalOwner,
      podStatus,
      sensitive,
      kubeServiceEnabled,
      serviceUrl,
      runSids = [],
      startDate,
      endDate,
      pipelineRunParameters,
      status,
      instance,
      commitStatus,
      dockerImage,
      pricePerHour,
      initialized,
      nodeCount,
      parentRunId,
      lastChangeCommitTime,
      stateReasonMessage,
      platform,
      sshPassword
    } = run || {};

    if (pending || !run) {
      Title = <h1>Run </h1>;
      Details = <div>Loading details...</div>;
    } else {
      const owner = (originalOwner || '').toLowerCase();
      const isRemovedPipeline = !!version && !pipelineId;
      let kubeServiceInfo;
      if (
        kubeServiceEnabled &&
        /^running$/i.test(status) &&
        this.props.runKubeServices.loaded
      ) {
        kubeServiceInfo = this.props.runKubeServices.value;
      }
      let endpoints;
      let share;
      let kubeServices;
      if (this.endpointAvailable) {
        const regionedUrls = parseRunServiceUrlConfiguration(serviceUrl);
        endpoints = (
          <tr style={{fontSize: '11pt'}}>
            <th style={{verticalAlign: 'middle'}}>
              {
                regionedUrls.length > 1
                  ? 'Endpoints: '
                  : 'Endpoint: '
              }
            </th>
            <td>
              <ul>
                {
                  regionedUrls.map(({name, url, sameTab}, index) =>
                    <li key={index}>
                      <MultizoneUrl
                        target={sameTab ? '_top' : '_blank'}
                        configuration={url}
                        style={{display: 'inline-flex'}}
                        dropDownIconStyle={{marginTop: 5}}
                      >
                        {name}
                      </MultizoneUrl>
                    </li>
                  )
                }
              </ul>
            </td>
          </tr>
        );
      }
      if (kubeServiceInfo) {
        const {hostName, ports = []} = kubeServiceInfo;
        const firstPorts = ports.slice(0, MAX_KUBE_SERVICES_TO_DISPLAY);
        const renderPort = (port) => (
          <span>
            {hostName}:{port.port}
          </span>
        );
        kubeServices = (
          <tr>
            <th style={{verticalAlign: 'top'}}>{ports.length > 1 ? 'Services: ' : 'Service: '}</th>
            <td>
              <ul>
                {
                  firstPorts.map((port) =>
                    <li key={port.port}>{renderPort(port)}</li>
                  )
                }
                {
                  ports.length > MAX_KUBE_SERVICES_TO_DISPLAY && (
                    <li>
                      <Popover
                        placement="right"
                        content={
                          <div style={{maxHeight: '50vh', overflow: 'auto', paddingRight: 20}}>
                            {ports.map((port) => <Row key={port.port}>{renderPort(port)}</Row>)}
                          </div>
                        }>
                        <a>And {ports.length - MAX_KUBE_SERVICES_TO_DISPLAY} more</a>
                      </Popover>
                    </li>
                  )
                }
              </ul>
            </td>
          </tr>
        );
      }
      if (
        this.initializeEnvironmentFinished &&
        status === 'RUNNING' &&
        roleModel.isOwner(run)
      ) {
        let shareList = 'Not shared (click to configure)';
        const {
          ssh: combineSshRoles,
          endpoint: combineEndpointRoles
        } = this.combineRolesIntoAllRoles;
        const filteredRunSids = combineSshRoles || combineEndpointRoles
          ? [ROLE_ALL, ...runSids]
            .filter(({name, accessType}) => {
              if (
                (combineSshRoles && accessType === AccessTypes.ssh) ||
                (combineEndpointRoles && accessType === AccessTypes.endpoint)
              ) {
                return !ROLE_ALL.includedRoles.includes(name);
              }
              return true;
            })
          : runSids;
        if (filteredRunSids.length > 0) {
          shareList = filteredRunSids
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
      const pipeline = pipelineName && version
        ? {name: pipelineName, id: pipelineId, version: version}
        : undefined;
      const {runId} = this.props.params;

      const resumeFailureReason = getResumeFailureReason(run);
      if (resumeFailureReason) {
        ResumeFailureReason = (
          <Alert
            type="warning"
            style={{margin: '5px 0'}}
            message={resumeFailureReason}
          />
        );
      }
      let pipelineLink;
      if (pipeline) {
        if (pipeline.id) {
          pipelineLink = (
            <Link className={styles.pipelineLink} to={`/${pipeline.id}/${pipeline.version}`}>
              {pipeline.name} ({pipeline.version})
            </Link>
          );
        } else {
          pipelineLink = (
            <span
              className={
                classNames(
                  styles.deletedPipeline,
                  'cp-danger'
                )
              }
            >
              {pipeline.name} ({pipeline.version})
              <Popover
                content={(
                  <span>
                    {this.localizedString('Pipeline')} <b>{pipeline.name}</b> has been removed
                  </span>
                )}
              >
                <Icon
                  type="exclamation-circle"
                  style={{
                    marginLeft: 5,
                    fontSize: 'smaller'
                  }}
                />
              </Popover>
            </span>
          );
        }
      }

      const failureReason = status === 'FAILURE' && podStatus
        ? <span style={{fontWeight: 'normal', marginLeft: 5}}>({podStatus})</span> : undefined;

      Title = (
        <h1 className={styles.runTitle}>
          <StatusIcon run={run} />
          <span>
            <span>Run</span>
            <RunName.AutoUpdate
              run={run}
              editable
              onRefresh={this.refreshRunInfo}
            >
              #{runId}
            </RunName.AutoUpdate>
            {failureReason} - </span>
          {pipelineLink}
          <span>{pipelineLink && ' -'} Logs</span>
        </h1>
      );
      const {
        scheduledDate,
        runningDate,
        schedulingDuration,
        totalDuration
      } = getRunDurationInfo(
        run,
        true,
        runTasks
      );
      let startedTime, finishTime;
      const scheduledTime = (
        <tr>
          <th>Scheduled: </th><td>{displayDate(scheduledDate)}</td>
        </tr>
      );

      if (runningDate && runTasks.length) {
        startedTime = (
          <tr>
            <th>Started: </th>
            <td>
              {displayDate(runningDate)} (
              {displayDurationInSeconds(schedulingDuration)}
              )
            </td>
          </tr>
        );
        let statusLabel = 'Running for';
        switch ((status || '').toUpperCase()) {
          case 'SUCCESS':
          case 'FAILURE':
            statusLabel = 'Finished';
            break;
          case 'STOPPED':
            statusLabel = 'Stopped at';
            break;
          default:
            statusLabel = 'Running for';
            break;
        }
        finishTime = (
          <tr>
            <th>{statusLabel}{`: `}</th>
            <td>
              <RunTimelineInfo
                run={run}
                runTasks={runTasks}
                analyseSchedulingPhase
              />
            </td>
          </tr>
        );
      } else {
        startedTime = (
          <tr>
            <th>Waiting for: </th>
            <td>{displayDurationInSeconds(totalDuration)}</td>
          </tr>
        );
      }

      let price;
      if (pricePerHour) {
        const adjustPrice = (value) => {
          if (value === 0) {
            return 0;
          }
          let cents = Math.ceil(value * 100);
          if (cents < 1) {
            cents = 1;
          }
          return cents / 100;
        };
        price = (
          <tr>
            <th>Estimated price:</th>
            <td>
              <JobEstimatedPriceInfo>
                {
                  adjustPrice(evaluateRunPrice(
                    run,
                    {
                      analyseSchedulingPhase: true,
                      runTasks
                    }
                  ).total).toFixed(2)
                }
                $
              </JobEstimatedPriceInfo>
            </td>
          </tr>
        );
      }

      Details =
        <div>
          <table className={styles.runDetailsTable}>
            <tbody>
              {
                sensitive ? (
                  <tr>
                    <th
                      className="cp-sensitive"
                      colSpan={2}
                    >
                      SENSITIVE
                    </th>
                  </tr>
                ) : undefined
              }
              {endpoints}
              {kubeServices}
              {share}
              <tr>
                <th>Owner: </th><td><UserName userName={owner} /></td>
              </tr>
              {
                configName
                  ? (
                    <tr>
                      <th>Configuration:</th>
                      <td>{configName}</td>
                    </tr>
                  ) : undefined
              }
              {scheduledTime}
              {startedTime}
              {finishTime}
              {price}
              {this.renderNestedRuns()}
              {this.renderRunSchedule(instance)}
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
            header={this.renderInstanceHeader(instance, run)}>
            <ul>
              {
                this.renderInstanceDetails(instance, run)
              }
            </ul>
          </Collapse.Panel>
        </Collapse>;
      switch (status.toLowerCase()) {
        case 'paused':
          if (
            roleModel.executeAllowed(run) &&
            roleModel.isOwner(run)
          ) {
            ActionButton = (
              <a
                className="cp-danger"
                onClick={() => this.terminatePipeline()}
              >
                TERMINATE
              </a>
            );
          }
          break;
        case 'running':
        case 'pausing':
        case 'resuming':
          if (
            (roleModel.executeAllowed(run) || sshPassword) &&
            (roleModel.isOwner(run) || sshPassword) &&
            canStopRun(run)
          ) {
            ActionButton = (
              <a
                className="cp-danger"
                onClick={() => this.stopPipeline()}
              >
                STOP
              </a>
            );
          }
          break;
        case 'stopped':
        case 'failure':
        case 'success':
          if (
            roleModel.executeAllowed(run) &&
            !isRemovedPipeline
          ) {
            ActionButton = (
              <a
                onClick={() => this.reRunPipeline()}
              >
                RERUN
              </a>
            );
          }
          break;
      }
      if (
        roleModel.executeAllowed(run) &&
        roleModel.isOwner(run) &&
        initialized &&
        !(nodeCount > 0) &&
        !(parentRunId && parentRunId > 0) &&
        instance &&
        instance.spot !== undefined &&
        !instance.spot
      ) {
        switch (status.toLowerCase()) {
          case 'running':
            if (canPauseRun(run, this.props.preferences)) {
              PauseResumeButton = this.maintenanceMode
                ? getMaintenanceDisabledButton('PAUSE')
                : (<a onClick={this.showPauseConfirmDialog}>PAUSE</a>);
            }
            break;
          case 'paused':
            PauseResumeButton = this.maintenanceMode
              ? getMaintenanceDisabledButton('RESUME')
              : (<a onClick={this.showResumeConfirmDialog}>RESUME</a>);
            break;
          case 'pausing':
            PauseResumeButton = (<span>PAUSING</span>);
            break;
          case 'resuming':
            PauseResumeButton = (<span>RESUMING</span>);
            break;
        }
      }

      if (this.sshEnabled) {
        SSHButton = (
          <MultizoneUrl
            configuration={this.props.runSSH.value}
            dropDownIconStyle={{
              paddingLeft: 4,
              marginLeft: -2
            }}
          >
            SSH
          </MultizoneUrl>
        );
      }
      if (this.fsBrowserEnabled) {
        FSBrowserButton = (
          <MultizoneUrl
            configuration={this.props.runFSBrowser.value}
            dropDownIconStyle={{
              paddingLeft: 4,
              marginLeft: -2
            }}
          >
            BROWSE
          </MultizoneUrl>
        );
      }

      if (this.state.commitAllowed && runIsCommittable(run)) {
        if (canCommitRun(run) && roleModel.executeAllowed(run)) {
          let previousStatus;
          const commitDate = displayDate(lastChangeCommitTime);
          switch ((commitStatus || '').toLowerCase()) {
            case 'not_committed': break;
            case 'committing':
              previousStatus = (
                <span>
                  <Icon type="loading" /> COMMITTING...
                </span>
              );
              break;
            case 'failure': previousStatus = (<span>COMMIT FAILURE ({commitDate})</span>); break;
            case 'success': previousStatus = (<span>COMMIT SUCCEEDED ({commitDate})</span>); break;
            default: break;
          }
          if (previousStatus) {
            CommitStatusButton = (
              <Row>
                {previousStatus}. {
                  this.maintenanceMode
                    ? getMaintenanceDisabledButton('COMMIT')
                    : (<a onClick={this.openCommitRunForm}>COMMIT</a>)
                }
              </Row>
            );
          } else {
            CommitStatusButton = this.maintenanceMode
              ? getMaintenanceDisabledButton('COMMIT')
              : (<a onClick={this.openCommitRunForm}>COMMIT</a>);
          }
        } else {
          const commitDate = displayDate(lastChangeCommitTime);
          switch ((commitStatus || '').toLowerCase()) {
            case 'not_committed': break;
            case 'committing':
              CommitStatusButton = (<span><Icon type="loading" /> COMMITTING...</span>);
              break;
            case 'failure':
              CommitStatusButton = (<span>COMMIT FAILURE ({commitDate})</span>);
              break;
            case 'success':
              CommitStatusButton = (<span>COMMIT SUCCEEDED ({commitDate})</span>);
              break;
            default: break;
          }
        }
      }

      if (status !== 'RUNNING') {
        ExportLogsButton = (<a onClick={this.exportLog}>EXPORT LOGS</a>);
      }

      let switchModeUrl;
      if (graphIsSupportedForLanguage(language)) {
        if (this.props.params.mode.toLowerCase() === 'graph') {
          switchModeUrl = `/run/${this.props.runId}/plain`;
        } else {
          switchModeUrl = `/run/${this.props.runId}/graph`;
        }
      }

      if (switchModeUrl && selectedTask) {
        switchModeUrl += `/${selectedTask}`;
      }

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

      if (instance && instance.nodeName) {
        const parts = [
          startDate && `from=${encodeURIComponent(startDate)}`,
          endDate && `to=${encodeURIComponent(endDate)}`
        ].filter(Boolean);
        const query = parts.length > 0 ? `?${parts.join('&')}` : '';
        ShowMonitorButton = (
          <Link to={`/cluster/${instance.nodeName}/monitor${query}`}>
            MONITOR
          </Link>
        );
      }
    }

    const navigateToRun = (runId) => {
      if (Number(this.props.runId) !== Number(runId)) {
        this.closeNestedRunsModalAndNavigateToRun(runId);
      }
    };

    return (
      <Card
        className={
          classNames(
            styles.logCard,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )
        }
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
              stateReasonMessage && (
                <Alert
                  message={`Server failure reason: ${stateReasonMessage}`}
                  type="error"
                />
              )
            }
            {ResumeFailureReason}
            <RestartedRunsInfo
              style={{margin: '5px 0'}}
              run={run}
            />
            <Row>
              {Details}
            </Row>
          </Col>
          <Col span={6}>
            <Row type="flex" justify="end" className={styles.actionButtonsContainer}>
              {
                this.buttonsWrapper(
                  platform !== 'windows' &&
                  PauseResumeButton
                )
              }
              {this.buttonsWrapper(ActionButton)}
              {this.buttonsWrapper(SSHButton)}
              {this.buttonsWrapper(FSBrowserButton)}
              {this.buttonsWrapper(ExportLogsButton)}
            </Row>
            <br />
            <Row type="flex" justify="end" className={styles.actionButtonsContainer}>
              {SwitchModeButton}{ShowLaunchCommandsButton}{ShowMonitorButton}
            </Row>
            <br />
            <Row type="flex" justify="end" className={styles.actionButtonsContainer}>
              {CommitStatusButton}
            </Row>
            <br />
            {
              !sensitive &&
              this.props.vsActions.available && (
                <Row type="flex" justify="end" className={styles.actionButtonsContainer}>
                  <VSActions
                    run={run}
                    showDownIcon
                    trigger={['click']}
                  >
                    VERSIONED STORAGE
                  </VSActions>
                </Row>
              )
            }
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
          sids={(runSids || []).map(s => s)}
          pending={this.state.operationInProgress}
          onSave={this.operationWrapper(this.saveShareSids)}
          onClose={this.closeShareDialog}
          runSharing
        />
        <CommitRunDialog
          runId={this.props.runId}
          defaultDockerImage={dockerImage}
          pending={this.state.operationInProgress}
          visible={this.state.commitRun}
          onCancel={this.closeCommitRunForm}
          onSubmit={this.operationWrapper(this.commitRun)}
        />
        <LaunchCommand
          payload={this.runPayload}
          visible={this.state.showLaunchCommands}
          onClose={this.hideLaunchCommands}
        />
        <NestedRunsModal
          runId={this.props.runId}
          visible={this.state.nestedRunsModalVisible}
          onCancel={this.closeNestedRunsModal}
          onRunClick={navigateToRun}
        />
      </Card>
    );
  }

  componentDidUpdate (prevProps) {
    if (prevProps.runId !== this.props.runId) {
      this.updateFromProps();
    }
    const {
      pending
    } = this.state;
    if (!pending && this.graph) {
      this.graph.updateData();
    }
  }
}

export default Logs;
