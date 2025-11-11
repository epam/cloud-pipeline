import React from 'react';
import PropTypes from 'prop-types';
import {Input, Select} from 'antd';
import classNames from 'classnames';
import styles from './launch-form-parameter-input.css';

class MetadataAutoComplete extends React.PureComponent {
  state = {
    filteredEntityFields: []
  };

  componentDidMount () {
    this.handleSearch();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.value !== this.props.value) {
      this.handleSearch();
    }
  }

  getMetadataEntity = (name) => {
    const {currentMetadataEntity = []} = this.props;
    return currentMetadataEntity.find((m) => `${m.metadataClass.id}` === String(name));
  };

  getFieldType = (metadataEntity, field) => {
    if (!metadataEntity || !field) {
      return undefined;
    }
    const currentField = metadataEntity.fields.find((f) => f.name === field);
    return currentField ? currentField.type : undefined;
  };

  handleSearch = () => {
    const {value} = this.props;
    if (!value || !/^(this\.|project\.)$/.test(value)) {
      this.setState({
        filteredEntityFields: []
      });
    } else if (/^this\./.test(value)) {
      const parsed = value.split('.').slice(1);
      const lastPart = parsed.pop();
      let entity = this.getMetadataEntity(this.props.rootEntityId);
      for (const part of parsed) {
        const type = this.getFieldType(entity, part);
        if (!type) {
          entity = undefined;
          break;
        }
        entity = this.getMetadataEntity(type);
      }
      const filteredEntityFields =
        ((entity ? entity.fields : undefined) || [])
          .filter((field) => field.name.toLowerCase().indexOf(lastPart.toLowerCase()) >= 0)
          .map((field) => ({
            ...field,
            value: ['this', ...parsed, field.name].join('.')
          }));
      this.setState({
        filteredEntityFields
      });
    } else if (/^project\./.test(value)) {
      const parsed = value.split('.');
      const filteredEntityFields = (() => {
        if (parsed.length === 2) {
          const {currentProjectMetadata = {}} = this.props;
          return Object.keys(currentProjectMetadata)
            .filter((m) => m.toLowerCase().indexOf(parsed[1].toLowerCase()) >= 0)
            .map((m) => ({name: m, value: `project.${m}`}));
        }
        return [];
      })();
      this.setState({
        filteredEntityFields
      });
    } else {
      this.setState({
        filteredEntityFields: []
      });
    }
  };

  onSelect = (value) => {
    const {onChange} = this.props;
    if (onChange) {
      onChange(value);
    }
  };

  onChange = (value) => {
    const {onChange} = this.props;
    if (onChange) {
      onChange(value);
    }
  }

  render () {
    const {
      className,
      style,
      defaultInput,
      value,
      onChange,
      addonBefore,
      size,
      disabled,
      placeholder
    } = this.props;
    if (defaultInput) {
      const onInputChange = (e) => {
        if (typeof onChange === 'function') {
          onChange(e.target.value || '');
        }
      };
      return (
        <Input
          className={className}
          style={style}
          value={value}
          onChange={onInputChange}
          disabled={disabled}
          addonBefore={addonBefore}
          placeholder={placeholder}
          size={size}
        />
      );
    }
    const {filteredEntityFields = []} = this.state;
    return (
      <Input.Group compact style={{display: 'flex'}}>
        {
          addonBefore &&
          <span className={classNames(styles.metadataAutoComplete, 'cp-input-group-addon')}>
            {addonBefore}
          </span>
        }
        <Select
          className={className}
          style={style}
          value={value}
          onChange={this.onChange}
          onSelect={this.onSelect}
          disabled={disabled}
          placeholder={placeholder}
          size={size}
          mode="combobox"
          filterOption={false}
        >
          {
            filteredEntityFields.map((field) => (
              <Select.Option
                key={field.name}
                value={field.value}>
                {field.name}
              </Select.Option>
            ))
          }
        </Select>
      </Input.Group>
    );
  }
}

MetadataAutoComplete.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  value: PropTypes.string,
  onChange: PropTypes.func,
  placeholder: PropTypes.string,
  currentProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currentProjectMetadata: PropTypes.object,
  currentMetadataEntity: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  rootEntityId: PropTypes.string,
  size: PropTypes.oneOf(['small', 'default', 'large']),
  addonBefore: PropTypes.node,
  defaultInput: PropTypes.bool
};

export default MetadataAutoComplete;
