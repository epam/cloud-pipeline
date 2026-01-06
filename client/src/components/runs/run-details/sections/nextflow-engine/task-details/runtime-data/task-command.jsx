import React from 'react';
import PropTypes from 'prop-types';
import {TaskRuntimeDataDetails} from './task-runtime-data-details';
import CodeEditor from '../../../../../../special/CodeEditor';
import styles from './runtime-data.css';

function Renderer (props) {
  const {
    className,
    style,
    data,
    task,
    errorComponent
  } = props;
  const {attributes = {}} = task || {};
  const {workdir, env = ''} = attributes || {};
  const envs = (env || '').split(/\s/)
    .map((o) => o.trim())
    .filter((o) => o.length > 0)
    .map((o) => {
      const [key, ...value] = o.split('=');
      return {
        key,
        value: value.join('=')
      };
    });
  console.log(env, envs);

  const renderWorkdir = () => {
    console.log('props', props)
    const isWorkdirUrl = (path) => path && /^(s3|as|gs|nfs|https?):\/\//i.test(path);
    if (!workdir) {
      return '-';
    };
    if (isWorkdirUrl(workdir)) {
      return <a href={workdir} target="_blank">{workdir}</a>;
    }
    return workdir;
  };

  return (
    <div
      className={className}
      style={{
        display: 'flex',
        flexDirection: 'column',
        ...style
      }}
    >
      <table className={styles.runtimeMetricsTable}>
        <tbody>
          <tr>
            <th
              className="cp-divider bottom light"
              key="key"
              style={{borderWidth: 5}}>
              Working directory:
            </th>
            <td
              className="cp-divider bottom light"
              key="value"
              style={{borderWidth: 5}}>
              {renderWorkdir()}
            </td>
          </tr>
          {
            envs.map((param, pIdx) => (
              <tr
                key={`env-${param.key}-${pIdx}`}
              >
                <th className="cp-divider bottom light" key="key">{param.key}:</th>
                <td className="cp-divider bottom light" key="value">{param.value}</td>
              </tr>
            ))
          }
        </tbody>
      </table>
      {errorComponent ? (
        <div className={styles.errorComponentContainer}>
          {errorComponent}
        </div>
      ) : null}
      {!errorComponent ? (
        <CodeEditor
          className={styles.taskCommandCodeEditor}
          code={data}
          readOnly
        />
      ) : null}
    </div>
  );
}

Renderer.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  task: PropTypes.object,
  data: PropTypes.string,
  passErrorToContent: PropTypes.bool
};

function TaskCommand (props) {
  return (
    <TaskRuntimeDataDetails
      {...props}
      component={Renderer}
      detailsType="command"
      reload={false}
      passErrorToContent
    />
  );
}

TaskCommand.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  task: PropTypes.object,
  errorMessage: PropTypes.string,
  errorMessageRunning: PropTypes.string
};

export default TaskCommand;
