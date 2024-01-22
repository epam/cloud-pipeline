import React from 'react';
import PropTypes from 'prop-types';
import {Input, Select} from 'antd';
import AWSRegionTag from '../special/AWSRegionTag';

class RegionIdSelector extends React.Component {
  render () {
    const {
      className,
      style,
      disabled,
      value,
      onChange,
      regions = [],
      provider
    } = this.props;
    if (!/^local$/i.test(provider) && regions.length > 0) {
      return (
        <Select
          className={className}
          size="small"
          showSearch
          value={value}
          onChange={onChange}
          allowClear={false}
          placeholder="Region ID"
          optionFilterProp="children"
          style={style}
          filterOption={
            (input, option) =>
              option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0}
          disabled={disabled}>
          {
            (regions || []).map(r => {
              return (
                <Select.Option key={r} value={r} title={r}>
                  <AWSRegionTag
                    showProvider={false}
                    provider={provider}
                    regionUID={r}
                    style={{marginRight: 5}}
                  />{r}
                </Select.Option>
              );
            })
          }
        </Select>
      );
    }
    return (
      <Input
        size="small"
        placeholder="Region ID"
        disabled={disabled}
        className={className}
        style={style}
        value={value}
        onChange={onChange}
      />
    );
  }
}

RegionIdSelector.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  value: PropTypes.string,
  onChange: PropTypes.func,
  regions: PropTypes.oneOfType([
    PropTypes.array,
    PropTypes.object
  ]),
  provider: PropTypes.string
};

export default RegionIdSelector;
