import React from 'react';
import PropTypes from 'prop-types';
import {inject} from 'mobx-react';
import classNames from 'classnames';
import {computed} from 'mobx';
import roleModel from '../../../../../../utils/roleModel';
import pipelineRunSSHCache from '../../../../../../models/pipelines/PipelineRunSSHCache';
import MultizoneUrl from '../../../../../special/multizone-url';
import styles from './run-actions.css';
import {Icon} from "antd";

const FIRE_CLOUD_ENVIRONMENT = 'FIRECLOUD';
const DTS_ENVIRONMENT = 'DTS';

@inject('preferences')
class RunSSHButton extends React.Component {
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

  render () {
    const {
      className,
      style,
      run,
      icon
    } = this.props;
    if (!run) {
      return null;
    }
    const {runSSH} = this.state;
    if (this.sshEnabled && runSSH) {
      return (
        <MultizoneUrl
          className={classNames(className, styles.runAction)}
          style={style}
          configuration={runSSH}
          dropDownIconStyle={{
            paddingLeft: 4,
            marginLeft: -2
          }}
        >
          {
            icon && <Icon type={icon} style={{marginRight: 5}} />
          }
          <span>SSH</span>
        </MultizoneUrl>
      );
    }
    return null;
  }
}

RunSSHButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  icon: PropTypes.string
};

export default RunSSHButton;
