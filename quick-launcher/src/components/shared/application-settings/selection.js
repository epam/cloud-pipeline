import React, {useCallback} from 'react';
import SelectionValue from './selection-value';
import './application-settings.css';

export default function Selection(
  {
    config,
    value,
    onChange = (() => {}),
    dependency,
    multiple = false,
    filter
  }
) {
  if (!config) {
    return null;
  }
  const {
    values = [],
    itemValue = (o => o),
    itemName = (o => o)
  } = config;
  const onChangeSelection = (item) => ((newValue, e) => {
    if (multiple) {
      if (!newValue) {
        onChange((value || []).filter(o => o !== itemValue(item)), e);
      } else {
        onChange((value || []).concat(newValue), e);
      }
    } else {
      onChange(newValue, e);
    }
    return value === itemValue(item);
  });
  const isSelected = (item) => {
    if (multiple) {
      return (value || []).includes(itemValue(item));
    }
    return value === itemValue(item);
  };
  if (!values.length) {
    return null;
  }
  return (
    <>
      {
        values
          .filter(item => !filter || (itemName(item) || '').toLowerCase().includes(filter.toLowerCase()))
          .map(item => (
            <SelectionValue
              key={itemValue(item)}
              value={itemValue(item)}
              name={itemName(item)}
              selected={isSelected(item)}
              config={config}
              onChange={onChangeSelection(item)}
              dependency={dependency}
            />
        ))
      }
    </>
  );
}
