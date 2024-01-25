import React, {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';
import PropTypes from 'prop-types';
import ReactDOM from 'react-dom/client';
import { Button, Checkbox, Input } from 'antd';
import {
  registerDialogPropertiesCallback,
  registerDialogResponseValidationCallback,
  dialogResponse,
} from '../ipc-events';
import './default-dialog.css';
import 'antd/dist/reset.css';
import '../../../styles/antd.css';

/**
 * @typedef {Object} ButtonConfiguration
 * @property {number} id
 * @property {string} name
 * @property {string} [type]
 */

/**
 * @typedef {string|ButtonConfiguration} DialogButton
 */

/**
 * @typedef {Object} DialogOptions
 * @property {string} [title]
 * @property {string} [message]
 * @property {string} [details]
 * @property {boolean|string} [input=false]
 * @property {string} [inputPlaceholder]
 * @property {boolean|string} [checkbox]
 * @property {boolean} [checkboxDefaultValue]
 * @property {DialogButton[]} [buttons]
 */

/**
 * @param {DialogOptions} options
 */
function configurationCallback(options = {}) {
  const {
    title,
    message,
    details,
    buttons,
    checkbox,
    checkboxDefaultValue,
    input,
    inputPlaceholder,
  } = options;
  const root = ReactDOM.createRoot(document.getElementById('root'));
  const onClick = (clickOptions) => dialogResponse(clickOptions);
  root.render(
    <DefaultDialog
      title={title}
      message={message}
      details={details}
      input={input}
      inputPlaceholder={inputPlaceholder}
      checkbox={checkbox}
      checkboxDefaultValue={checkboxDefaultValue}
      buttons={buttons}
      onButtonClick={onClick}
    />,
  );
}

registerDialogPropertiesCallback(configurationCallback);

function useInput(input, clearError) {
  const [value, setValue] = useState(typeof input === 'string' ? input : undefined);
  const onChange = useCallback(
    (event) => {
      setValue(event.target.value);
      clearError();
    },
    [setValue, clearError],
  );
  return useMemo(() => ({
    value,
    onChange,
    enabled: !!input,
  }), [value, onChange, input]);
}

function useCheckbox(checkbox, checkboxDefaultValue = false) {
  const [value, setValue] = useState(checkboxDefaultValue);
  const onChange = useCallback((event) => setValue(event.target.checked), [setValue]);
  return useMemo(() => ({
    value,
    onChange,
    enabled: !!checkbox,
  }), [value, onChange, checkbox]);
}

/**
 * @param {DialogButton} button
 * @param {number} idx
 * @returns {ButtonConfiguration}
 */
function parseButton(button, idx) {
  if (typeof button === 'string') {
    return {
      id: idx,
      name: button,
    };
  }
  return {
    id: idx,
    name: button.name,
    ...button,
  };
}

/**
 * @param {DialogButton[]} buttons
 */
function useButtonsConfig(buttons) {
  return useMemo(() => buttons.map(parseButton), [buttons]);
}

function useValidationError(onValidationResponse) {
  const [error, setError] = useState(undefined);
  const validationCallback = useCallback((response) => {
    setError(response?.error);
    onValidationResponse();
  }, [setError, onValidationResponse]);
  const clear = useCallback(() => setError(undefined), [setError]);
  useEffect(() => {
    registerDialogResponseValidationCallback(validationCallback);
  }, [validationCallback]);
  return useMemo(() => ({
    error,
    clear,
  }), [error, clear]);
}

function DefaultDialog(
  {
    title,
    message,
    details,
    input,
    inputPlaceholder,
    checkbox,
    checkboxDefaultValue,
    buttons: dialogButtons,
    onButtonClick,
  },
) {
  const [disabled, setDisabled] = useState(false);
  const responseReceived = useCallback(() => setDisabled(false), [setDisabled]);
  const { error, clear } = useValidationError(responseReceived);
  const {
    value,
    onChange,
    enabled: inputEnabled,
  } = useInput(input, clear);
  const {
    value: checked,
    onChange: onCheckedChange,
    enabled: checkboxEnabled,
  } = useCheckbox(checkbox, checkboxDefaultValue);
  const buttons = useButtonsConfig(dialogButtons);
  const onClick = useCallback((event, button) => {
    if (typeof onButtonClick === 'function') {
      setDisabled(true);
      onButtonClick({
        id: button.id,
        input: value,
        checked,
      });
    }
  }, [onButtonClick, value, checked, setDisabled]);
  return (
    <div
      className="dialog"
    >
      {
        title && (
          <div className="title">
            {title}
          </div>
        )
      }
      <div className="body">
        {
          message && (
            <div className="message">
              {message}
            </div>
          )
        }
        {
          inputEnabled && (
            <div className="input-container">
              <Input
                disabled={disabled}
                autoFocus
                className="input"
                value={value}
                onChange={onChange}
                placeholder={inputPlaceholder}
              />
            </div>
          )
        }
        {
          checkboxEnabled && (
            <div className="checkbox-container">
              <Checkbox
                disabled={disabled}
                className="checkbox"
                checked={checked}
                onChange={onCheckedChange}
              >
                {typeof checkbox === 'boolean' ? undefined : checkbox}
              </Checkbox>
            </div>
          )
        }
        {
          details && (
            <div className="details">
              {details}
            </div>
          )
        }
        {
          error && (
            <div className="error">
              {error}
            </div>
          )
        }
      </div>
      <div
        className="actions"
      >
        {
          buttons.map((button) => (
            <Button
              disabled={disabled}
              key={`button-${button.id}`}
              className="action"
              type={button.type}
              danger={button.danger}
              onClick={(event) => onClick(event, button)}
            >
              {button.name}
            </Button>
          ))
        }
      </div>
    </div>
  );
}

const ButtonConfigurationPropType = PropTypes.shape({
  name: PropTypes.string,
  type: PropTypes.string,
});

DefaultDialog.propTypes = {
  title: PropTypes.string,
  message: PropTypes.string,
  details: PropTypes.string,
  input: PropTypes.oneOfType([PropTypes.string, PropTypes.bool]),
  inputPlaceholder: PropTypes.string,
  checkbox: PropTypes.oneOfType([PropTypes.string, PropTypes.bool]),
  checkboxDefaultValue: PropTypes.bool,
  buttons: PropTypes.arrayOf(PropTypes.oneOfType([PropTypes.string, ButtonConfigurationPropType])),
  onButtonClick: PropTypes.func,
};

DefaultDialog.defaultProps = {
  title: undefined,
  message: undefined,
  details: undefined,
  input: false,
  inputPlaceholder: undefined,
  checkbox: false,
  checkboxDefaultValue: false,
  buttons: ['OK'],
  onButtonClick: undefined,
};
