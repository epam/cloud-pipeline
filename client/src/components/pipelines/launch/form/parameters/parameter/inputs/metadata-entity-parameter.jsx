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
    const {displayField, nameField} = metadataConfig;
    const field = displayField || nameField;
    const entry = this.metadata.find(m => m.externalId === externalId);
    if (entry?.data && field) {
      return entry.data[field]?.value || externalId;
    }
    return externalId;
  };

  getDescription = (externalId) => {
    const {parameter} = this.props;
    const metadataConfig = parameter?.config?.metadata_config || {};
    const {descriptionField} = metadataConfig;
    if (!descriptionField) {
      return undefined;
    }
    const entry = this.metadata.find(m => m.externalId === externalId);
    return entry?.data?.[descriptionField]?.value;
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
        {this.enum.map((v) => {
          const label = this.getDisplayLabel(v.value);
          const description = this.getDescription(v.value);
          return (
            <Select.Option key={v.key} value={v.value} label={label}>
              <div className={styles.metadataEntityOption}>
                <div>{label}</div>
                {
                  description && (
                    <div
                      title={description}
                      className={
                        classNames(styles.metadataEntityOptionDescription, 'cp-text-not-important')
                      }
                    >
                      {description}
                    </div>
                  )
                }
              </div>
            </Select.Option>
          );
        })}
      </Select>
    );
  }
}

export default LaunchFormMetadataEntityParameter;
