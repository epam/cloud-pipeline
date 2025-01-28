import React from 'react';
import PropTypes from 'prop-types';
import roleModel from '../../../../../utils/roleModel';
import {canPauseRun, canStopRun, stopRun, terminateRun} from '../../../actions';
import {
  confirmRunContinuation,
  continueRun,
  runSupportsContinue
} from '../../../actions/continue-run';
import {inject} from 'mobx-react';
import getMaintenanceDisabledButton from '../../../controls/get-maintenance-mode-disabled-button';
import styles from './run-header.css';
import classNames from 'classnames';
import {computed} from 'mobx';
import pipelineRunSSHCache from '../../../../../models/pipelines/PipelineRunSSHCache';
import MultizoneUrl from '../../../../special/multizone-url';
import confirmPause from "../../../actions/pause-confirmation";
import PausePipeline from "../../../../../models/pipelines/PausePipeline";
import {message} from "antd";

const FIRE_CLOUD_ENVIRONMENT = 'FIRECLOUD';
const DTS_ENVIRONMENT = 'DTS';

@inject('routing', 'preferences')
class RunActions extends React.Component {
  state = {
    runSSH: undefined
  };

  componentDidMount () {
    this.updateRunSSH();
  }

  componentDidUpdate (prevProps) {
    const {run: prevRun = {}} = prevProps;
    const {run = {}} = this.props;
    if (prevRun.id !== run.id) {
      this.updateRunSSH();
    }
  }

  updateRunSSH = () => {
    const {
      run
    } = this.props;
    if (!run) {
      this.setState({runSSH: undefined});
    } else {
      (async () => {
        try {
          const request = pipelineRunSSHCache.getPipelineRunSSH(run.id);
          await request.fetch();
          if (request.error) {
            throw new Error(request.error);
          }
          const runSSH = request.value;
          this.setState({runSSH});
        } catch {
          this.setState({runSSH: undefined});
        }
      })();
    }
  };

  @computed
  get isDtsEnvironment () {
    const {run} = this.props;
    return run && run.executionPreferences &&
      run.executionPreferences.environment === DTS_ENVIRONMENT;
  }

  @computed
  get isFireCloudEnvironment () {
    const {run} = this.props;
    return run && run.executionPreferences &&
      run.executionPreferences.environment === FIRE_CLOUD_ENVIRONMENT;
  }

  @computed
  get initializeEnvironmentFinished () {
    const {run} = this.props;
    return run && run.initialized;
  }

  @computed
  get sshEnabled () {
    const {run} = this.props;
    const {runSSH} = this.state;
    if (
      run &&
      runSSH &&
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

  refreshRunInfo = () => {
    const {
      onRefreshRunInfo
    } = this.props;
    if (onRefreshRunInfo) {
      onRefreshRunInfo();
    }
  };

  terminatePipeline = () => {
    const {
      run
    } = this.props;
    if (run) {
      return terminateRun(this, this.refreshRunInfo)(run);
    }
  };

  stopPipeline = () => {
    const {
      run
    } = this.props;
    if (run) {
      return stopRun(this, this.refreshRunInfo)(run);
    }
  };

  closeNestedRunsModalAndNavigateToRun = (runId) => {
    this.setState({
      nestedRunsModalVisible: false
    }, () => {
      const {routing} = this.props;
      if (routing && routing.push && typeof routing.push === 'function') {
        routing.push(`/run/${runId}`);
      }
    });
  };

  onContinueRunClick = () => {
    const {run} = this.props;
    if (run) {
      (async () => {
        try {
          const confirmed = await confirmRunContinuation(run);
          if (confirmed) {
            const runId = await continueRun(run);
            if (runId) {
              this.closeNestedRunsModalAndNavigateToRun(runId);
            }
          }
        } catch (e) {
          // noop
        }
      })();
    }
  };

  pausePipeline = async (e) => {
    if (e) {
      e.stopPropagation();
    }
    const {run} = this.props;
    const pausePipeline = new PausePipeline(run.id);
    await pausePipeline.send({});
    if (pausePipeline.error) {
      message.error(pausePipeline.error);
    }
    this.refreshRunInfo();
  };

  showPauseConfirmDialog = async () => {
    const {run} = this.props;
    const confirmed = await confirmPause({
      id: run.id,
      run
    });
    if (confirmed) {
      await this.pausePipeline();
    }
  };

  render () {
    const {
      className,
      style,
      run,
      preferences
    } = this.props;
    if (!run) {
      return null;
    }
    let PauseResumeButton;
    let ActionButton;
    let ContinueButton;
    let SSHButton;
    const {
      status,
      sshPassword,
      version,
      pipelineId,
      initialized,
      nodeCount,
      parentRunId,
      instance
    } = run;
    const {runSSH} = this.state;
    if (this.sshEnabled && runSSH) {
      SSHButton = (
        <MultizoneUrl
          configuration={runSSH}
          dropDownIconStyle={{
            paddingLeft: 4,
            marginLeft: -2
          }}
        >
          SSH
        </MultizoneUrl>
      );
    }
    const isRemovedPipeline = !!version && !pipelineId;
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
        if (
          roleModel.executeAllowed(run) &&
          !isRemovedPipeline &&
          runSupportsContinue(run)
        ) {
          ContinueButton = (
            <a
              onClick={() => this.onContinueRunClick()}
            >
              CONTINUE
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
          if (canPauseRun(run, preferences)) {
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
    const actions = [];
    const addButton = (button, key) => {
      if (button) {
        actions.push(
          <div key={key} style={{display: 'inline-flex', alignItems: 'center'}}>
            {button}
          </div>
        );
      }
    };
    addButton(SSHButton, 'ssh');
    addButton(PauseResumeButton, 'pause-resume');
    addButton(ActionButton, 'action');
    addButton(ContinueButton, 'continue');
    if (actions.length === 0) {
      return null;
    }
    return (
      <div
        className={classNames(className, styles.runActions)}
        style={{...(style || {}), display: 'inline-flex', alignItems: 'center'}}
      >
        {actions}
      </div>
    );
  }
}

RunActions.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  onRefreshRunInfo: PropTypes.func
};

export default RunActions;
