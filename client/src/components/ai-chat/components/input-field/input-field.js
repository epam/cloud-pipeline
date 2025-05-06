import React from 'react';
import PropTypes from 'prop-types';
import {Input} from 'antd';
import styles from './input-field.css';
import {ChatIconBtn} from '../index';

export default class InputField extends React.Component {
  render () {
    const {
      value,
      onChange,
      onPressEnter,
      disabled,
      onClick,
      onKeyDown
    } = this.props;

    return (
      <div className={styles.field}>
        <Input.TextArea
          style={{height: 20}}
          value={value}
          onChange={onChange}
          onKeyDown={onKeyDown}
          onPressEnter={onPressEnter}
          disabled={disabled}
          autosize={{minRows: 1, maxRows: 6}}
        />
        <button disabled={disabled} onClick={onClick} className={styles.iconChat}>
          <ChatIconBtn />
        </button>
      </div>
    );
  }
}

InputField.propTypes = {
  value: PropTypes.string,
  onChange: PropTypes.func,
  onPressEnter: PropTypes.func,
  onSubmit: PropTypes.func,
  onClick: PropTypes.func,
  onKeyDown: PropTypes.func
};
