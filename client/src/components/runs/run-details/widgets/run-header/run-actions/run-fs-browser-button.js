import React from 'react';
import PropTypes from 'prop-types';
import {inject} from 'mobx-react';
import classNames from 'classnames';
import {computed} from 'mobx';
import roleModel from '../../../../../../utils/roleModel';
import cache from '../../../../../../models/pipelines/PipelineRunFSBrowserCache';
import MultizoneUrl from '../../../../../special/multizone-url';
import styles from './run-actions.css';
import {checkRunActionAvailable, runActions} from '../../../../actions/actions-availability';
import {Icon} from 'antd';

const DTS_ENVIRONMENT = 'DTS';

@inject('preferences')
class RunFsBrowserButton extends React.Component {
  state = {
    runFsBrowser: undefined
  };

  componentDidMount () {
    this.updateRunFsBrowser();
  }

  componentDidUpdate (prevProps) {
    const {run: prevRun = {}} = prevProps;
    const {run = {}} = this.props;
    if (prevRun.id !== run.id) {
      this.updateRunFsBrowser();
    }
  }

  updateRunFsBrowser = () => {
    const {
      run
    } = this.props;
    if (!run) {
      this.setState({runFsBrowser: undefined});
    } else {
      (async () => {
        try {
          const request = cache.getPipelineRunFSBrowser(run.id);
          await request.fetch();
          if (request.error) {
            throw new Error(request.error);
          }
          const runFsBrowser = request.value;
          this.setState({runFsBrowser});
        } catch {
          this.setState({runFsBrowser: undefined});
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
  get initializeEnvironmentFinished () {
    const {run} = this.props;
    return run && run.initialized;
  }

  @computed
  get fsBrowserEnabled () {
    const {run} = this.props;
    const {runFsBrowser} = this.state;
    if (
      run &&
      runFsBrowser &&
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
        podIP &&
        checkRunActionAvailable(run, runActions.browse);
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
    const {runFsBrowser} = this.state;
    if (this.fsBrowserEnabled && runFsBrowser) {
      return (
        <MultizoneUrl
          className={classNames(className, styles.runAction)}
          style={style}
          configuration={runFsBrowser}
          dropDownIconStyle={{
            paddingLeft: 4,
            marginLeft: -2
          }}
        >
          {
            icon && <Icon type={icon} style={{marginRight: 5}} />
          }
          <span>BROWSE</span>
        </MultizoneUrl>
      );
    }
    return null;
  }
}

RunFsBrowserButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  icon: PropTypes.string
};

export default RunFsBrowserButton;
