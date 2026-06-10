import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './parameter-name-input.module.css';

function StaticParameterName(props) {
  const {className, style, children} = props;
  return (
    <div className={classNames(className, styles.parameterNameInputContainer)} style={style}>
      <div className={styles.parameterNameInputRow}>
        <span
          className={classNames(
            'ant-form-item-title',
            styles.parameterName,
            styles.editingDisabled,
          )}
          style={{textWrap: 'nowrap'}}
        >
          {children}
        </span>
      </div>
    </div>
  );
}

StaticParameterName.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  children: PropTypes.node,
};

export default StaticParameterName;
