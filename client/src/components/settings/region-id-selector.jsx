import React from 'react';
import PropTypes from 'prop-types';
import {Input, Select} from 'antd';
import AWSRegionTag from '../special/AWSRegionTag';

class RegionIdSelector extends React.Component {
  render() {
    const {
      className,
      style,
      disabled,
      value,
      onChange,
      regions = [],
      provider,
      internalId,
    } = this.props;
    const internalIdComponent =
      internalId === undefined ? undefined : (
        <span className="cp-text-not-important" style={{marginLeft: 5}}>
          Internal ID {internalId}
        </span>
      );
    if (!/^local$/i.test(provider) && regions.length > 0) {
      return (
        <div
          className={className}
          style={{
            ...(style || {}),
            display: 'inline-flex',
            alignItems: 'center',
          }}
        >
          <Select
            style={{flex: 1}}
            size="small"
            showSearch
            value={value}
            onChange={onChange}
            allowClear={false}
            placeholder="Region ID"
            optionFilterProp="children"
            filterOption={(input, option) =>
              option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }
            disabled={disabled}
          >
            {(regions || []).map((r) => {
              return (
                <Select.Option key={r} value={r} title={r}>
                  <AWSRegionTag
                    showProvider={false}
                    provider={provider}
                    regionUID={r}
                    style={{marginRight: 5}}
                  />
                  {r}
                </Select.Option>
              );
            })}
          </Select>
          {internalIdComponent}
        </div>
      );
    }
    return (
      <div
        className={className}
        style={{
          ...(style || {}),
          display: 'inline-flex',
          alignItems: 'center',
        }}
      >
        <Input
          size="small"
          placeholder="Region ID"
          disabled={disabled}
          value={value}
          onChange={onChange}
          style={{flex: 1}}
        />
        {internalIdComponent}
      </div>
    );
  }
}

RegionIdSelector.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  value: PropTypes.string,
  onChange: PropTypes.func,
  regions: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  provider: PropTypes.string,
  internalId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
};

export default RegionIdSelector;
