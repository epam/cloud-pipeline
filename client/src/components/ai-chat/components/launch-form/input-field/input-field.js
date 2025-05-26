import React from 'react';
import PropTypes from 'prop-types';
import {Input} from 'antd';
import styles from './input-field.css';
import {observer} from 'mobx-react';

@observer
export default class InputField extends React.Component {
  state = {
    error: null
  };

  handleChange = (value) => {
    const {onChange, validator} = this.props;
    let error = null;

    if (validator) {
      error = validator(value);
    }

    this.setState({error});
    onChange(value);
  };

  validate = () => {
    const {value, validator} = this.props;

    if (validator) {
      const error = validator(value);
      this.setState({error});
      return !error;
    }

    return true;
  };

  render () {
    const {value, label, disabled, placeholder} = this.props;
    const {error} = this.state;

    return (
      <div className={styles.inputField}>
        <label className={styles.label}>{label}</label>
        <div className={styles.inputContainer}>
          <Input
            className={`${error ? styles.inputError : ''}`}
            style={{width: '100%'}}
            value={value}
            onChange={(e) => this.handleChange(e.target.value)}
            disabled={disabled}
            placeholder={placeholder || 'Enter input'}
          />
          {error && <div className={styles.error}>{error}</div>}
        </div>
      </div>
    );
  }
}

InputField.propTypes = {
  value: PropTypes.string,
  onChange: PropTypes.func.isRequired,
  label: PropTypes.string,
  disabled: PropTypes.bool,
  placeholder: PropTypes.string,
  validator: PropTypes.func
};

InputField.defaultProps = {
  value: '',
  label: '',
  disabled: false,
  placeholder: '',
  validator: null
};
