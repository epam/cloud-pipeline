import React from 'react';
import classNames from 'classnames';
import PropTypes from 'prop-types';
import styles from './scheme-parameter-input.css';
import {Button} from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import LaunchFormSchemeParameterEntryProp from './scheme-parameter-entry-prop';

function LaunchFormSchemeParameterEntry (props) {
  const {
    className,
    style,
    disabled,
    parameter,
    entry,
    properties = [],
    onChange,
    onRemove,
    currentProjectId,
    currentProjectMetadata,
    currentMetadataEntity,
    rootEntityId,
    metadataAutoComplete
  } = props;
  if (properties.length === 0) {
    return null;
  }
  return (
    <tr
      className={classNames(className)}
      style={style}
    >
      {
        properties.map((prop) => (
          <LaunchFormSchemeParameterEntryProp
            key={prop.name}
            property={prop}
            parameter={parameter}
            onChange={onChange}
            entry={entry}
            currentProjectId={currentProjectId}
            currentProjectMetadata={currentProjectMetadata}
            currentMetadataEntity={currentMetadataEntity}
            rootEntityId={rootEntityId}
            metadataAutoComplete={metadataAutoComplete}
          />
        ))
      }
      <td className={styles.entryAction}>
        <Button disabled={disabled} onClick={onRemove} type="danger" size="small">
          <DeleteOutlined />
        </Button>
      </td>
    </tr>
  );
}

LaunchFormSchemeParameterEntry.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameter: PropTypes.object,
  properties: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  entry: PropTypes.any,
  onChange: PropTypes.func,
  onRemove: PropTypes.func,
  disabled: PropTypes.bool,
  currentProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currentProjectMetadata: PropTypes.object,
  currentMetadataEntity: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  rootEntityId: PropTypes.string,
  metadataAutoComplete: PropTypes.bool
};

export default LaunchFormSchemeParameterEntry;
