import React from 'react';
import {Alert} from 'antd';
import PipelineRunInfo from '../../../models/pipelines/PipelineRunInfo';
import LoadingView from '../../special/LoadingView';
import RunDetails from '../run-details';
import Logs from './Log';
import {isMlflowEngine, isNextflowEngine} from '../run-details/utilities/helpers';
import {inject, observer} from 'mobx-react';

export const runDetailsPresentation = {
  default: 'basic',
  nextflow: 'nextflow',
  mlflow: 'mlflow'
};

function getRunDetailsPresentation (run, preferences) {
  if (isNextflowEngine(run)) {
    return runDetailsPresentation.nextflow;
  }
  if (
    isMlflowEngine(run) &&
    preferences &&
    preferences.uiMlflowSettings &&
    preferences.uiMlflowSettings.mlflow_base
  ) {
    return runDetailsPresentation.mlflow;
  }
  return runDetailsPresentation.default;
}

@inject('preferences')
@observer
class LogsRedirect extends React.Component {
  state = {
    pending: true,
    run: undefined,
    error: undefined,
    presentation: undefined,
    modes: []
  };

  componentDidMount () {
    this.checkRun();
  }

  componentDidUpdate (prevProps) {
    const {params: currentParams = {}} = this.props;
    const {params: prevParams = {}} = prevProps;
    const {runId: currentRunId} = currentParams;
    const {runId: prevRunId} = prevParams;
    if (currentRunId !== prevRunId) {
      this.checkRun();
    }
  }

  componentWillUnmount () {
    this.token = undefined;
  }

  checkRun = () => {
    const {params: currentParams = {}, preferences} = this.props;
    const {runId: currentRunId} = currentParams;
    const token = this.token = {};
    const commit = (fn) => {
      if (token === this.token) {
        fn();
      }
    };
    if (currentRunId) {
      this.setState({
        pending: true,
        error: undefined,
        run: undefined,
        presentation: undefined,
        modes: []
      }, async () => {
        try {
          const runRequest = new PipelineRunInfo(currentRunId);
          await Promise.all([runRequest.fetch(), preferences.fetchIfNeededOrWait()]);
          if (runRequest.error) {
            throw new Error(runRequest.error);
          }
          if (!runRequest.value) {
            throw new Error(`Run #${currentRunId} not found`);
          }
          const run = {...runRequest.value};
          const presentation = getRunDetailsPresentation(run, preferences);
          const modes = [...new Set([
            presentation,
            runDetailsPresentation.default
          ])];
          commit(() => {
            this.setState({
              pending: false,
              run,
              presentation,
              modes
            });
          });
        } catch (error) {
          commit(() => {
            this.setState({
              pending: false,
              error: error.message,
              presentation: undefined,
              modes: []
            });
          });
        }
      });
    } else {
      this.setState({
        pending: false,
        error: 'Run is not specified',
        run: undefined,
        presentation: undefined,
        modes: []
      });
    }
  }

  onChangeMode = (mode) => this.setState({presentation: mode});

  render () {
    const {
      error,
      pending,
      run,
      presentation,
      modes
    } = this.state;
    if (pending) {
      return (
        <div style={{width: '100%', height: '100%'}}>
          <LoadingView />
        </div>
      );
    }
    if (run) {
      switch (presentation) {
        case runDetailsPresentation.nextflow:
        case runDetailsPresentation.mlflow:
          return (
            <RunDetails
              {...this.props}
              preLoadedRun={run}
              currentMode={presentation}
              modes={modes}
              onChangeMode={this.onChangeMode}
            />
          );
        case runDetailsPresentation.default:
        default:
          return (
            <Logs
              {...this.props}
              currentMode={presentation}
              modes={modes}
              onChangeMode={this.onChangeMode}
            />
          );
      }
    }
    return (
      <div style={{width: '100%', height: '100%'}}>
        <Alert message={error || 'Run is not specified'} type="error" />
      </div>
    );
  }
}

export default LogsRedirect;
