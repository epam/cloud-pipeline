import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Icon} from 'antd';
import styles from './initialize-wrapper.css';

function InitializeWrapper (props) {
  const {
    className,
    style,
    run,
    children
  } = props;
  const disclaimer = (
    <div className={classNames(className, styles.runInitializeWrapper)} style={style}>
      <div className={classNames(styles.disclaimer, 'cp-text-not-important')}>
        <Icon type="loading" />
        <span>Run is initializing</span>
      </div>
    </div>
  );
  if (!run) {
    return disclaimer;
  }
  const {
    initialized = false
  } = run;
  if (!initialized) {
    return disclaimer;
  }
  return children;
}

InitializeWrapper.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  children: PropTypes.node
};

export default InitializeWrapper;
