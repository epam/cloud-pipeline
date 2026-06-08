import React from 'react';
import PropTypes from 'prop-types';
import {inject} from 'mobx-react';
import classNames from 'classnames';
import {Button} from 'antd';
import roleModel from '../../../../../../utils/roleModel';
import {canStopRun, openReRunForm, stopRun, terminateRun} from '../../../../actions';
import styles from './run-actions.css';

@inject('routing', 'preferences')
class RunActionButton extends React.Component {
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

  reRunPipeline = () => {
    const {run} = this.props;
    if (run) {
      return openReRunForm(run, this.props);
    }
  };

  render () {
    const {
      className,
      style,
      run
    } = this.props;
    if (!run) {
      return null;
    }
    const {
      status,
      sshPassword,
      version,
      pipelineId
    } = run;
    const isRemovedPipeline = !!version && !pipelineId;
    const actionButton = (() => {
      switch (status.toLowerCase()) {
        case 'paused':
          if (
            roleModel.executeAllowed(run) &&
            roleModel.isOwner(run)
          ) {
            return (
              <Button
                className={classNames(className, styles.runAction)}
                style={style}
                size="small"
                onClick={() => this.terminatePipeline()}
                danger
              >
                TERMINATE
              </Button>
            );
          }
          return undefined;
        case 'running':
        case 'pausing':
        case 'resuming':
          if (
            (roleModel.executeAllowed(run) || sshPassword) &&
            (roleModel.isOwner(run) || sshPassword) &&
            canStopRun(run)
          ) {
            return (
              <Button
                className={classNames(className, styles.runAction)}
                style={style}
                size="small"
                onClick={() => this.stopPipeline()}
                danger
              >
                STOP
              </Button>
            );
          }
          return undefined;
        case 'stopped':
        case 'failure':
        case 'success':
          if (
            roleModel.executeAllowed(run) &&
            !isRemovedPipeline
          ) {
            return (
              <Button
                className={classNames(className, styles.runAction)}
                style={style}
                size="small"
                onClick={() => this.reRunPipeline()}
                type="primary"
              >
                RERUN
              </Button>
            );
          }
          return undefined;
        default:
          return undefined;
      }
    })();
    if (!actionButton) {
      return null;
    }
    return actionButton;
  }
}

RunActionButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  onRefreshRunInfo: PropTypes.func
};

export default RunActionButton;
