import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Icon} from 'antd';
import LaunchFormParameterInput from './inputs';
import styles from './launch-form-parameter.css';

class LaunchFormParameter extends React.PureComponent {
  render () {
    const {
      className,
      style,
      parameter,
      onChange,
      readOnly,
      disabled,
      onRemoveParameter
    } = this.props;
    if (!parameter) {
      return null;
    }
    const onRemoveParameterClicked = () => {
      if (typeof onRemoveParameter === 'function' && !readOnly && !disabled) {
        onRemoveParameter(parameter);
      }
    };
    return (
      <div
        className={classNames(className, styles.launchFormParameter)}
        style={style}
      >
        <span className="ant-form-item-title" style={{textWrap: 'nowrap'}}>
          {parameter.name}
        </span>
        <div style={{display: 'flex', flexWrap: 'nowrap', fontSize: 'larger'}}>
          <LaunchFormParameterInput
            style={{flex: 1, minHeight: 32}}
            parameter={parameter}
            onChange={onChange}
            disabled={disabled}
            readOnly={readOnly}
          />
          {!readOnly && typeof onRemoveParameter === 'function' ? (
            <Icon
              className="dynamic-delete-button"
              type="minus-circle-o"
              onClick={onRemoveParameterClicked}
              style={{
                verticalAlign: 'middle',
                marginTop: '2px',
                fontSize: 'larger',
                cursor: 'pointer',
                alignSelf: 'flex-start',
                margin: 'auto -2px auto 15px',
                height: '100%'
              }}
            />
          ) : (
            <div
              style={{
                marginLeft: 15,
                width: 15,
                display: 'inline-block'
              }}>{'\u00A0'}</div>
          )}
        </div>
      </div>
    );
  }
}

LaunchFormParameter.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameter: PropTypes.object,
  onChange: PropTypes.func,
  readOnly: PropTypes.bool,
  disabled: PropTypes.bool,
  onRemoveParameter: PropTypes.func
};

export default LaunchFormParameter;
