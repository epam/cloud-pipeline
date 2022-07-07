import React, {useState, useEffect} from 'react';
import classNames from 'classnames';
import './application-settings.css';

export default function StringInput (
  {
    title,
    linkText,
    value,
    onChange = (() => {}),
    hiddenUnderLink = false,
    linkRenderFn,
    formatterFn,
    correctFn,
    validateFn
  }
) {
  const [expanded, setExpanded] = useState(false);
  const [errors, setErrors] = useState([]);
  const [tempValue, setTempValue] = useState('');
  const containerRef = React.createRef();
  const inputRef = React.createRef();

  useEffect(() => {
    resetChanges();
  }, []);

  useEffect(() => {
    if (hiddenUnderLink && expanded) {
      inputRef.current.focus();
    }
  }, [expanded, hiddenUnderLink]);

  useEffect(() => {
    if (validateFn) {
      validate(tempValue);
    }
  }, [value, tempValue]);

  const validate = () => {
    const validationErrors = validateFn(tempValue);
    if (validationErrors && validationErrors.length) {
      setErrors(validationErrors);
      return;
    }
    if (errors && errors.length > 0) {
      setErrors([]);
    }
  };

  const handleKeyDown = (event) => {
    if (event.key === 'Enter') {
      submitChanges(tempValue);
    }
    if (event.key === 'Escape') {
      resetChanges();
    }
  };

  const submitChanges = (value) => {
    if (errors && errors.length > 0) {
      return resetChanges();
    }
    onChange(typeof correctFn === 'function' ? correctFn(value) : value);
    setTempValue(correctFn(value));
    setExpanded(false);
  };

  const resetChanges = () => {
    setTempValue(value);
    setExpanded(false);
  };

  const handleInputChange = (event) => {
    const value = formatterFn
      ? formatterFn(event.target.value, event.nativeEvent.data)
      : event.target.value;
    setTempValue(value);
  };

  const renderInput = () => {
    return (
      <>
        {!hiddenUnderLink ? (
          <div
            className="application-setting-title"
          >
            {title}
          </div>
        ) : null}
        <input
          ref={inputRef}
          value={tempValue}
          onChange={handleInputChange}
          onBlur={() => submitChanges(tempValue)}
          onKeyDown={handleKeyDown}
        />
      </>
    )
  };
  return (
    <div
      className={classNames(
        "application-setting string-setting",
        {
          error: errors.length > 0,
          ['show-message']: expanded && errors.length > 0
        }
      )}
      ref={containerRef}
    >
      {hiddenUnderLink && !expanded ? (
        <a
          href="#"
          onClick={() => hiddenUnderLink && setExpanded(true)}
          className="link"
        >
          {linkRenderFn ? linkRenderFn(value) : linkText}
        </a>
      ) : renderInput()}
      {expanded && errors && errors.length > 0 ? (
        <span className="error-text">
          {errors[0]}
        </span>
      ) : null }
    </div>
  );
}
