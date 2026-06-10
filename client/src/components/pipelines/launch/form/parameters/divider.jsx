import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './parameters.module.css';

function Divider(props) {
  const {className, style, children, highlighted} = props;
  const divider = (
    <div
      className={classNames('cp-divider horizontal', styles.parametersGroupSectionDivider)}
      style={highlighted ? {borderTopColor: 'currentcolor'} : {}}
    />
  );
  return (
    <div
      className={classNames(className, {
        'cp-primary': highlighted,
      })}
      style={Object.assign({}, style || {}, {
        display: 'flex',
        alignItems: 'center',
        width: '100%',
        margin: '20px 0',
      })}
    >
      <div style={{flex: 1}}>{divider}</div>
      <div
        className={classNames(styles.parametersGroupSectionTitle, {
          'cp-primary': highlighted,
        })}
        style={{
          margin: '0 10px',
          fontWeight: 'bold',
          textTransform: 'uppercase',
        }}
      >
        {children}
      </div>
      <div style={{flex: 1}}>{divider}</div>
    </div>
  );
}

Divider.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  children: PropTypes.node,
  highlighted: PropTypes.bool,
};

export default Divider;
