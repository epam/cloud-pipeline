import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Form, Select} from 'antd';
import styles from './launch-form-parameter.css';
import StaticParameterName from './name-input/static-parameter-name';

function RootEntityTypeParameter (props) {
  const {
    className,
    style,
    disabled,
    onChange,
    currentMetadataEntity = [],
    rootEntityId
  } = props;
  if (currentMetadataEntity.length === 0) {
    return null;
  }
  return (
    <div
      className={classNames(className, styles.launchFormParameter)}
      style={style}
    >
      <StaticParameterName
        style={{paddingRight: 30}}
      >
        Root entity type
      </StaticParameterName>
      <div style={{display: 'flex', flexWrap: 'nowrap', fontSize: 'larger', paddingRight: 30}}>
        <Form.Item
          hasFeedback
          style={{flex: 1, marginBottom: 0}}>
          <Select
            disabled={disabled}
            allowClear
            value={rootEntityId}
            onChange={onChange}
            placeholder="Select root entity type">
            {currentMetadataEntity.map(entity => {
              return (
                <Select.Option key={entity.metadataClass.id}>
                  {entity.metadataClass.name}
                </Select.Option>
              );
            })}
          </Select>
        </Form.Item>
      </div>
    </div>
  );
}

RootEntityTypeParameter.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  currentProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  rootEntityId: PropTypes.string,
  currentMetadataEntity: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  onChange: PropTypes.func
};

export default RootEntityTypeParameter;
