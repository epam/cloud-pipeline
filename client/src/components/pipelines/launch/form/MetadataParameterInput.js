/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {Input, Icon} from 'antd';
import MetadataBrowser from './../dialogs/MetadataBrowser';
import styles from './LaunchPipelineForm.css';

const mapMetadataParameter = ({folderId, entitiesIds, metadataClassName}) => {
  return `${folderId}:${metadataClassName}:${entitiesIds}`;
};

const unmapMetadataParameter = (string = '') => {
  if (!string) {
    return {};
  }
  const [folderId, metadataClassName, ids = ''] = string.split(':');
  return {
    folderId: Number(folderId),
    metadataClassName,
    entitiesIds: ids.split(',').map(id => Number(id))
  };
};

export default class MetadataParameterInput extends React.Component {
  state = {
    showMetadataBrowser: false
  }

  get inputMask () {
    const {value} = this.props;
    if (!value) {
      return '';
    }
    const {metadataClassName, entitiesIds} = unmapMetadataParameter(value);
    return `${metadataClassName} (${entitiesIds.length})`;
  }

  showMetadataBrowser = () => {
    const {disabled} = this.props;
    if (!disabled) {
      this.setState({showMetadataBrowser: true});
    }
  }

  closeMetadataBrowser = () => {
    this.setState({showMetadataBrowser: false});
  };

  selectMetadataParameter = (
    entitiesIds,
    metadataClass,
    expansionExpression,
    folderId,
    metadataLibraryLocation
  ) => {
    const {onSelectMetadata} = this.props;
    onSelectMetadata && onSelectMetadata(mapMetadataParameter({
      entitiesIds,
      folderId: metadataLibraryLocation.folderId,
      metadataClassName: metadataLibraryLocation.metadataClassName
    }));
    this.closeMetadataBrowser();
  };

  render () {
    const {
      disabled,
      style,
      value,
      currentProjectId,
      rootEntityId,
      currentMetadataEntity
    } = this.props;
    const {showMetadataBrowser} = this.state;
    return (
      <div>
        <Input
          disabled={disabled}
          style={style}
          value={this.inputMask}
          size="large"
          addonBefore={
            <div
              className={styles.pathType}
              onClick={this.showMetadataBrowser}
            >
              <Icon type="folder" />
            </div>
          }
          placeholder="Select metadata"
        />
        <MetadataBrowser
          readOnly
          onCancel={this.closeMetadataBrowser}
          onSelect={this.selectMetadataParameter}
          visible={showMetadataBrowser}
          initialFolderId={null}
          initialActiveFolderId={currentProjectId}
          rootEntityId={rootEntityId}
          currentMetadataEntity={currentMetadataEntity}
          selection={unmapMetadataParameter(value)}
          browseLibrary
          hideExpansionExpression
          disableMetadataFolderSelection
        />
      </div>
    );
  }
}

MetadataParameterInput.PropTypes = {
  value: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  onSelectMetadata: PropTypes.func,
  currentProjectId: PropTypes.number,
  rootEntityId: PropTypes.number,
  currentMetadataEntity: PropTypes.array
};
