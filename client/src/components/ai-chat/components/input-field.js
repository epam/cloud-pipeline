import React from 'react';
import {Input} from 'antd';
import styles from './input-field.css';

export default class InputField extends React.Component {
  render () {
    const {
      value,
      onChange,
      onPressEnter,
      disabled,
      onClick
    } = this.props;

    return (
      <div className={styles.field}>
        <Input.TextArea
          style={{height: 20}}
          value={value}
          onChange={onChange}
          onPressEnter={onPressEnter}
          disabled={disabled}
          autosize={{minRows: 1, maxRows: 6}}
        />
        <button disabled={disabled} onClick={onClick} className={styles.iconChat}>
          <img src="icons/chat-bot/airplane.svg" width={20} height={20} />
        </button>
        {/*<button disabled={disabled} onClick={()=> {}} className={styles.iconClip}>*/}
        {/*  <Icon type="paper-clip" style={{fontSize: 16}} />*/}
        {/*</button>*/}
      </div>
    );
  }
}
