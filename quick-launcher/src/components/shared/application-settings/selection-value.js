import React, {useCallback} from 'react';
import classNames from 'classnames';
import Arrow, {ArrowDirection} from '../arrow';
import './application-settings.css';

export default function SelectionValue(
  {
    config,
    selected,
    value,
    name,
    onChange = (() => {}),
    dependency
  }
) {
  if (!config) {
    return null;
  }
  const hasSubOptions = config.valueHasSubOptions(value);
  const handleClick = useCallback((e) => {
    e && e.preventDefault();
    e && e.stopPropagation();
    if (selected && !hasSubOptions && !config.required) {
      onChange && onChange(undefined);
    } else {
      onChange && onChange(value, e);
    }
  }, [selected, onChange, value, config]);
  const dependencies = dependency && dependency.__parent__ === value
    ? Object.entries(dependency || {})
      .filter(([key]) => !/^__.+__$/i.test(key))
      .map(([key, value]) => `${key} ${value}`)
    : [];
  return (
    <div
      key={value}
      className={
        classNames(
          'application-settings-selection-value',
          {selected}
        )
      }
      onClick={handleClick}
      style={{
        display: 'flex',
        alignItems: 'center'
      }}
    >
      <span>{name}</span>
      <div className="application-settings-selection-value-details">
        <span className="dependencies">
          {dependencies.join(', ')}
        </span>
        <Arrow
          direction={ArrowDirection.right}
          style={{
            display: hasSubOptions ? 'block' : 'none'
          }}
        />
      </div>
    </div>
  );
}
