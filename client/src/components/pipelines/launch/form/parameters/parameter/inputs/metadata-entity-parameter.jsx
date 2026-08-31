import React from 'react';
import classNames from 'classnames';
import {Select} from 'antd';
import styles from './launch-form-parameter-input.css';

function unMapValue (value, parameter) {
  if (parameter?.config?.multiple && Array.isArray(value)) {
    return value.join(',');
  }
  return value;
}

function mapValue (value = '', parameter) {
  if (parameter?.config?.multiple) {
    return (value || '').split(',').filter(Boolean);
  }
  return String(value);
}

class LaunchFormMetadataEntityParameter extends React.PureComponent {
  get metadata () {
    const {parameter, parametersMetadata = {}} = this.props;
    return parametersMetadata[parameter.name]?.elements || [];
  }

  get enum () {
    return this.metadata.map(entry => ({
      key: entry.id,
      value: entry.externalId
    }));
  }

  getDisplayLabel = (externalId) => {
    const {parameter} = this.props;
    const metadataConfig = parameter?.config?.metadata_config || {};
    const entry = this.metadata.find(m => m.externalId === externalId);
    if (entry?.data && metadataConfig.nameField) {
      return entry.data[metadataConfig.nameField]?.value || externalId;
    }
    return externalId;
  };

  filterOption = (input, option) => {
    const label = option.props.label || '';
    return label.toLowerCase().includes(input.toLowerCase());
  };

  render () {
    const {
      className,
      style,
      value: valueProps,
      parameter,
      onChange,
      disabled
    } = this.props;
    const onInputChange = (e) => {
      if (typeof onChange === 'function') {
        onChange(unMapValue(e, parameter));
      }
    };
    return (
      <Select
        className={classNames(className, styles.launchParameterInput)}
        style={style}
        value={mapValue(valueProps, parameter)}
        onChange={onInputChange}
        disabled={disabled}
        size="large"
        mode={parameter?.config?.multiple ? 'multiple' : 'default'}
        filterOption={this.filterOption}
        optionLabelProp="label"
        showSearch
      >
        {this.enum.map((v) => (
          <Select.Option key={v.key} value={v.value} label={this.getDisplayLabel(v.value)}>
            {this.getDisplayLabel(v.value)}
          </Select.Option>
        ))}
      </Select>
    );
  }
}

export default LaunchFormMetadataEntityParameter;
