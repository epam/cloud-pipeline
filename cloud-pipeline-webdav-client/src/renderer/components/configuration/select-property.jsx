import React, { useMemo } from 'react';
import PropTypes from 'prop-types';
import { Select } from 'antd';
import classNames from 'classnames';
import './configuration.css';

function useValues(values) {
  return useMemo(() => (values || []).map((aValue) => {
    if (typeof aValue === 'string') {
      return {
        name: aValue,
        value: aValue,
      };
    }
    return aValue;
  }), [values]);
}

function SelectProperty(
  {
    className,
    style,
    property,
    propertyStyle,
    value,
    onChange,
    disabled,
    values: propValues,
  },
) {
  const values = useValues(propValues);
  return (
    <div
      className={
        classNames(
          className,
          'configuration-row',
        )
      }
      style={style}
    >
      <div
        className="property-title-container"
        style={propertyStyle}
      >
        {property}
        :
      </div>
      <Select
        className="property-value-container"
        value={value}
        onChange={onChange}
        disabled={disabled}
      >
        {
          values.map((aValue) => (
            <Select.Option key={aValue.value} value={aValue.value}>
              {aValue.name}
            </Select.Option>
          ))
        }
      </Select>
    </div>
  );
}

const SelectOptionPropType = PropTypes.oneOfType([
  PropTypes.string,
  PropTypes.shape({
    name: PropTypes.string,
    value: PropTypes.string,
  }),
]);

SelectProperty.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  property: PropTypes.string.isRequired,
  propertyStyle: PropTypes.object,
  value: PropTypes.string,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  values: PropTypes.arrayOf(SelectOptionPropType).isRequired,
};

SelectProperty.defaultProps = {
  className: undefined,
  style: undefined,
  value: undefined,
  disabled: false,
  onChange: undefined,
  propertyStyle: undefined,
};

export default SelectProperty;
