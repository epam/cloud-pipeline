import React from 'react';
import PropTypes from 'prop-types';
import {inject} from 'mobx-react';
import classNames from 'classnames';
import {Button, message, Modal} from 'antd';
import roleModel from '../../../../../../utils/roleModel';
import {canPauseRun} from '../../../../actions';
import confirmPause from '../../../../actions/pause-confirmation';
import PausePipeline from '../../../../../../models/pipelines/PausePipeline';
import getMaintenanceDisabledButton from '../../../../controls/get-maintenance-mode-disabled-button';
import ResumePipeline from '../../../../../../models/pipelines/ResumePipeline';
import localization from '../../../../../../utils/localization';
import styles from './run-actions.module.css';

@inject('routing', 'preferences')
@localization.localizedComponent
class RunPauseResumeButton extends React.Component {
  refreshRunInfo = () => {
    const {onRefreshRunInfo} = this.props;
    if (onRefreshRunInfo) {
      onRefreshRunInfo();
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
      run,
    });
    if (confirmed) {
      await this.pausePipeline();
    }
  };

  resumePipeline = async (e) => {
    if (e) {
      e.stopPropagation();
    }
    const {run} = this.props;
    const resumePipeline = new ResumePipeline(run.id);
    await resumePipeline.send({});
    if (resumePipeline.error) {
      message.error(resumePipeline.error);
    }
    this.refreshRunInfo();
  };

  showResumeConfirmDialog = () => {
    const {run} = this.props;
    if (run) {
      const toolAndVersion = (run.dockerImage || '').split('/').pop();
      const [imageName] = toolAndVersion.split(':');
      const pipelineName = run.pipelineName || imageName || this.localizedString('pipeline');
      Modal.confirm({
        title: `Do you want to resume ${pipelineName}?`,
        style: {
          wordWrap: 'break-word',
        },
        onOk: () => this.resumePipeline(),
        okText: 'RESUME',
        cancelText: 'CANCEL',
      });
    }
  };

  render() {
    const {className, style, run, preferences} = this.props;
    if (!run) {
      return null;
    }
    const {status, initialized, nodeCount, parentRunId, instance} = run;
    const pauseResumeButton = (() => {
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
              return this.maintenanceMode ? (
                getMaintenanceDisabledButton('PAUSE', 'pause-button', {className, style})
              ) : (
                <Button
                  className={classNames(className, styles.runAction)}
                  style={style}
                  onClick={this.showPauseConfirmDialog}
                  size="small"
                >
                  PAUSE
                </Button>
              );
            }
            break;
          case 'paused':
            return this.maintenanceMode ? (
              getMaintenanceDisabledButton('RESUME', 'resume-button', {className, style})
            ) : (
              <Button
                className={classNames(className, styles.runAction)}
                style={style}
                onClick={this.showResumeConfirmDialog}
                size="small"
              >
                RESUME
              </Button>
            );
          case 'pausing':
            return (
              <span
                className={classNames(className, styles.runAction)}
                style={style}
                id="pausing-status"
              >
                PAUSING
              </span>
            );
          case 'resuming':
            return (
              <span
                className={classNames(className, styles.runAction)}
                style={style}
                id="resuming-status"
              >
                RESUMING
              </span>
            );
        }
      }
      return undefined;
    })();
    if (!pauseResumeButton) {
      return null;
    }
    return pauseResumeButton;
  }
}

RunPauseResumeButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  onRefreshRunInfo: PropTypes.func,
};

export default RunPauseResumeButton;
