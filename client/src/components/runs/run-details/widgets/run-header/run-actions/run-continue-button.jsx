import React from 'react';
import PropTypes from 'prop-types';
import {inject} from 'mobx-react';
import classNames from 'classnames';
import {Button} from 'antd';
import roleModel from '../../../../../../utils/roleModel';
import {
  confirmRunContinuation,
  continueRun,
  runSupportsContinue,
} from '../../../../actions/continue-run';
import styles from './run-actions.module.css';

@inject('routing', 'preferences')
class RunContinueButton extends React.Component {
  refreshRunInfo = () => {
    const {onRefreshRunInfo} = this.props;
    if (onRefreshRunInfo) {
      onRefreshRunInfo();
    }
  };

  closeNestedRunsModalAndNavigateToRun = (runId) => {
    this.setState(
      {
        nestedRunsModalVisible: false,
      },
      () => {
        const {routing} = this.props;
        if (routing && routing.push && typeof routing.push === 'function') {
          routing.push(`/run/${runId}`);
        }
      },
    );
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

  render() {
    const {className, style, run} = this.props;
    if (!run) {
      return null;
    }
    const {status, version, pipelineId} = run;
    const isRemovedPipeline = !!version && !pipelineId;
    switch (status.toLowerCase()) {
      case 'stopped':
      case 'failure':
      case 'success':
        if (roleModel.executeAllowed(run) && !isRemovedPipeline && runSupportsContinue(run)) {
          return (
            <Button
              className={classNames(className, styles.runAction)}
              style={style}
              onClick={() => this.onContinueRunClick()}
              type="primary"
              size="small"
            >
              CONTINUE
            </Button>
          );
        }
        break;
    }
    return null;
  }
}

RunContinueButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  onRefreshRunInfo: PropTypes.func,
};

export default RunContinueButton;
