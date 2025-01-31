import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import GetRunTaskRuntimeData from '../../../../../../../models/run-engines/fetch-task-runtime-data';
import styles from './runtime-data.css';
import {Alert, Icon} from 'antd';

class TaskRuntimeDataDetails extends React.Component {
  state = {
    pending: true,
    error: undefined,
    data: undefined
  };

  componentDidMount () {
    this.fetchTaskRuntimeData();
  }

  componentDidUpdate (prevProps) {
    const {task: prevTask = {}, detailsType: prevDetailsType, reload: prevReload} = prevProps;
    const {task = {}, detailsType, reload} = this.props;
    const {runId: prevRunId, taskKey: prevTaskKey} = prevTask;
    const {runId, taskKey} = task;
    if (
      runId !== prevRunId ||
      taskKey !== prevTaskKey ||
      detailsType !== prevDetailsType ||
      reload !== prevReload
    ) {
      const clear = runId !== prevRunId ||
        taskKey !== prevTaskKey ||
        detailsType !== prevDetailsType;
      this.fetchTaskRuntimeData(clear);
    }
  }

  componentWillUnmount () {
    this.token = {};
    this.abortReload();
  }

  abortReload = () => {
    if (this.reloadTimeout) {
      clearTimeout(this.reloadTimeout);
      this.reloadTimeout = undefined;
    }
  };

  fetchTaskRuntimeData = (clear = false) => {
    this.abortReload();
    const {task = {}, detailsType, reload = false} = this.props;
    const {runId, taskKey} = task;
    const token = this.token = {};
    if (runId && taskKey && detailsType) {
      const statePayload = {
        pending: true,
        error: undefined
      };
      if (clear) {
        statePayload.data = undefined;
      }
      this.setState(statePayload, async () => {
        const commit = (fn) => {
          if (token === this.token) {
            fn();
          }
        };
        try {
          const request = new GetRunTaskRuntimeData(runId);
          await request.send({
            hash: taskKey,
            type: detailsType
          });
          if (request.error) {
            throw new Error(`Error fetching task logs: ${request.error}`);
          }
          const {data = {}} = request.value || {};
          const {
            content
          } = data;
          commit(() => {
            this.setState({
              pending: false,
              error: undefined,
              data: content
            });
          });
        } catch (error) {
          commit(() => {
            this.setState({
              pending: false,
              error: error.message,
              data: undefined
            });
          });
        } finally {
          commit(() => {
            if (reload) {
              this.abortReload();
              this.reloadTimeout = setTimeout(
                () => this.fetchTaskRuntimeData(),
                typeof reload === 'number' ? reload : 5000
              );
            }
          });
        }
      });
    } else {
      this.setState({
        pending: false,
        error: undefined,
        data: undefined
      });
    }
  };

  render () {
    const {
      className,
      style,
      task,
      component,
      errorMessage
    } = this.props;
    if (!task) {
      return null;
    }
    const {
      pending,
      error,
      data
    } = this.state;
    if (pending && !data) {
      return (
        <div
          className={classNames(className)}
          style={style}
        >
          <div className={styles.runtimeDataStateContainer}>
            <div
              className="cp-text-not-important"
              style={{display: 'flex', alignItems: 'center'}}
            >
              <Icon type="loading" />
              <span style={{marginLeft: 5}}>Loading data...</span>
            </div>
          </div>
        </div>
      );
    }
    if (error || !data) {
      return (
        <div
          className={classNames(className)}
          style={style}
        >
          <div className={styles.runtimeDataStateContainer}>
            <div style={{display: 'flex', alignItems: 'center'}}>
              <Alert
                type={error ? 'error' : 'warning'}
                message={errorMessage || error || 'Data not available'}
              />
            </div>
          </div>
        </div>
      );
    }
    return React.createElement(
      component,
      {...this.props, data}
    );
  }
}

const TaskRuntimeDataDetailsProps = {
  className: PropTypes.string,
  style: PropTypes.object,
  task: PropTypes.object,
  detailsType: PropTypes.oneOf([
    'exit',
    'log',
    'out',
    'err',
    'trace',
    'command',
    'run',
    'begin'
  ]),
  reload: PropTypes.oneOfType([PropTypes.bool, PropTypes.number])
};

TaskRuntimeDataDetails.propTypes = {
  ...TaskRuntimeDataDetailsProps,
  component: PropTypes.func,
  errorMessage: PropTypes.string
};

export {TaskRuntimeDataDetails, TaskRuntimeDataDetailsProps};

export function injectRuntimeData (detailsType, errorMessage = undefined) {
  function WrappedComponent (component) {
    return (props) => (
      <TaskRuntimeDataDetails
        {...props}
        detailsType={detailsType}
        component={component}
        errorMessage={errorMessage}
      />
    );
  }
  WrappedComponent.propTypes = TaskRuntimeDataDetailsProps;
  return WrappedComponent;
}
