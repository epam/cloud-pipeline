import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Input, Popover} from 'antd';
import {EditOutlined} from '@ant-design/icons';
import styles from './parameter-name-input.module.css';

class ParameterNameInput extends React.PureComponent {
  state = {
    editMode: false,
  };

  componentDidMount() {
    const {parameter = {}} = this.props;
    const {userParameter} = parameter;
    if (userParameter) {
      this.setState({editMode: true});
    }
  }

  componentWillUnmount() {
    clearTimeout(this.checkFocusTimeout);
  }

  get editingEnabled() {
    const {parameter = {}, editConfiguration = false, disabled, rawEdit} = this.props;
    if (disabled) {
      return false;
    }
    const {system, userParameter, config = {}} = parameter;
    const {readOnly: readOnlyValue} = config;
    const readOnly = rawEdit ? false : readOnlyValue;
    return userParameter || (!system && editConfiguration && !readOnly);
  }

  get editMode() {
    const {parameter = {}, editConfiguration, disabled} = this.props;
    if (disabled) {
      return false;
    }
    const {userParameter} = parameter;
    const {editMode} = this.state;
    if (userParameter && !editConfiguration) {
      // if we're not in "edit configuration" mode, then we should always display
      // parameter name input (i.e., user clicked "add parameter" on a launch form)
      return true;
    }
    return this.editingEnabled ? editMode : false;
  }

  onSetEditMode = () => this.setState({editMode: true});
  onUnSetEditMode = () => this.setState({editMode: false});

  onInitNameInput = (input) => {
    this.input = input;
  };

  onInitContainer = (container) => {
    this.container = container;
  };

  onBlur = () => {
    clearTimeout(this.checkFocusTimeout);
    this.checkFocusTimeout = setTimeout(() => this.checkFocus(), 100);
  };

  checkFocus = () => {
    clearTimeout(this.checkFocusTimeout);
    if (this.container && this.container.contains(document.activeElement)) {
      return;
    }
    this.onUnSetEditMode();
  };

  onNameChange = (event) => {
    const {onChange, parameter} = this.props;
    if (onChange) {
      const value = event.target.value;
      const payload = {
        ...parameter,
        name: value,
        config: {
          ...parameter.config,
          name: value,
        },
        configs: parameter.configs.map((cfg) => ({
          ...cfg,
          name: value,
        })),
      };
      onChange(payload);
    }
  };

  onPrettyNameChange = (event) => {
    const {onChange, parameter} = this.props;
    if (onChange) {
      const value = event.target.value;
      const payload = {
        ...parameter,
        config: {
          ...parameter.config,
          prettyName: value,
        },
        configs: parameter.configs.map((cfg) => ({
          ...cfg,
          prettyName: value,
        })),
      };
      onChange(payload);
    }
  };

  render() {
    const {className, style, parameter = {}, editConfiguration} = this.props;
    const {name, system, config = {}, nameError} = parameter;
    const {prettyName} = config;
    const {editMode} = this;
    const displayName = prettyName && prettyName.trim().length > 0 ? prettyName : name;
    const popoverContent = (
      <div>
        {name && name.trim().length > 0 ? (
          name
        ) : (
          <span className="cp-text-not-important">Parameter name is not specified</span>
        )}
      </div>
    );
    return (
      <div
        ref={this.onInitContainer}
        className={classNames(className, styles.parameterNameInputContainer)}
        style={style}
      >
        <div className={styles.parameterNameInputRow}>
          {editMode ? (
            <>
              <span>Name:</span>
              <Input
                placeholder="Parameter name"
                className={classNames(styles.parameterNameInput, {'cp-error': nameError})}
                autoFocus
                ref={this.onInitNameInput}
                onBlur={this.onBlur}
                value={name}
                onChange={this.onNameChange}
              />
              {editConfiguration && <span>Pretty name:</span>}
              {editConfiguration && (
                <Input
                  placeholder="Parameter pretty name"
                  className={styles.parameterNameInput}
                  ref={this.onInitPrettyNameInput}
                  onBlur={this.onBlur}
                  value={prettyName}
                  onChange={this.onPrettyNameChange}
                />
              )}
            </>
          ) : (
            <Popover content={popoverContent}>
              <span
                className={classNames('ant-form-item-title', styles.parameterName, {
                  'cp-error': nameError,
                  [styles.editingEnabled]: this.editingEnabled,
                  [styles.editingDisabled]: !this.editingEnabled,
                  'cp-text-not-important':
                    !nameError && (!displayName || displayName.trim().length === 0),
                })}
                style={{textWrap: 'nowrap'}}
                onClick={system ? undefined : this.onSetEditMode}
              >
                {displayName && displayName.trim().length > 0 ? displayName : '<parameter name>'}
                <EditOutlined className={styles.parameterNameEditIcon} />
              </span>
              {nameError && (
                <span className="cp-error" style={{marginLeft: 5}}>
                  {' - '}
                  {nameError}
                </span>
              )}
            </Popover>
          )}
        </div>
        {nameError && editMode && (
          <div className="cp-error" style={{margin: 0}}>
            {nameError}
          </div>
        )}
      </div>
    );
  }
}

ParameterNameInput.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  rawEdit: PropTypes.bool,
  parameter: PropTypes.object,
  onChange: PropTypes.func,
  editConfiguration: PropTypes.bool,
};

export default ParameterNameInput;
