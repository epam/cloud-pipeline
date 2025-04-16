import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import RunPipeline from './run-pipeline';
import RunDockerImage from './run-docker-image';
import styles from './run-executable.css';

function RunExecutable (props) {
  const {
    className,
    style,
    run
  } = props;
  if (!run) {
    return null;
  }
  const {
    pipelineName
  } = run;
  if (pipelineName) {
    return (
      <RunPipeline
        run={run}
        className={classNames(className, styles.runExecutable)}
        style={style}
      />
    );
  }
  return (
    <RunDockerImage
      className={classNames(className, styles.runExecutable)}
      style={style}
      run={run}
    />
  );
}

RunExecutable.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object
};

export default RunExecutable;
