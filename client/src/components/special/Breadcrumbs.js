/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {Link} from 'react-router';
import {Icon} from 'antd';
import classNames from 'classnames';
import EditableField from './EditableField';
import {
  findPath,
  generateTreeData,
  generateUrl,
  ItemTypes
} from '../pipelines/model/treeStructureFunctions';
import Owner from './owner';
import styles from './Breadcrumbs.css';
import HiddenObjects from '../../utils/hidden-objects';

@inject('pipelinesLibrary', 'preferences')
@HiddenObjects.injectTreeFilter
@observer
export default class Breadcrumbs extends React.Component {
  static propTypes = {
    id: PropTypes.number,
    type: PropTypes.string,
    textEditableField: PropTypes.string,
    readOnlyEditableField: PropTypes.bool,
    classNameEditableField: PropTypes.string,
    styleEditableField: PropTypes.object,
    editStyleEditableField: PropTypes.object,
    onSaveEditableField: PropTypes.func,
    onNavigate: PropTypes.func,
    displayTextEditableField: PropTypes.oneOfType([PropTypes.string, PropTypes.object]),
    icon: PropTypes.string,
    iconClassName: PropTypes.string,
    lock: PropTypes.bool,
    lockClassName: PropTypes.string,
    sensitive: PropTypes.bool,
    subject: PropTypes.object
  };

  @computed
  get inlineMetadataEntities () {
    const {
      preferences
    } = this.props;
    return preferences.inlineMetadataEntities;
  }

  @computed
  get rootItems () {
    const {
      pipelinesLibrary,
      preferences
    } = this.props;
    if (!pipelinesLibrary.loaded || !preferences.loaded) {
      return [];
    }
    const rootElements = [{
      id: 'root',
      name: 'Library',
      ...pipelinesLibrary.value
    }];
    return generateTreeData(
      {childFolders: rootElements},
      {
        filter: this.props.hiddenObjectsTreeFilter(),
        inlineMetadataEntities: this.inlineMetadataEntities
      }
    );
  }

  @computed
  get items () {
    let items = [];
    const rootItems = this.rootItems;
    if (this.props.type === ItemTypes.metadata) {
      items = findPath(`${ItemTypes.folder}_${this.props.id || 'root'}`, rootItems);
      if (!items) {
        return [];
      }
      if (!this.inlineMetadataEntities) {
        const metadataFolder = {
          name: 'Metadata',
          type: ItemTypes.metadataFolder,
          id: `${this.props.id}/metadata`,
          parentId: this.props.id
        };
        items.push({
          ...metadataFolder,
          url: () => generateUrl(metadataFolder)
        });
      }
      items.push({
        name: this.props.displayTextEditableField,
        id: `${ItemTypes.folder}_${this.props.id}`,
        url: null
      });
    } else if (this.props.type === ItemTypes.metadataFolder) {
      items = findPath(`${ItemTypes.folder}_${this.props.id || 'root'}`, rootItems);
      if (!items) {
        return [];
      }
      items.push({
        name: this.props.displayTextEditableField,
        id: `${ItemTypes.metadataFolder}_${this.props.id}/metadata`,
        url: null
      });
    } else {
      items = findPath(`${this.props.type}_${this.props.id || 'root'}`, rootItems);
    }
    if (items && items.length > 0) {
      items[0].icon = this.props.icon;
      items[0].iconClassName = this.props.iconClassName;
      items[0].lock = this.props.lock;
      items[0].lockClassName = this.props.lockClassName;
      items[0].sensitive = this.props.sensitive;
    }
    return items || [];
  }

  renameItem = async (name) => {
    if (this.props.onSaveEditableField) {
      await this.props.onSaveEditableField(name);
    }
  };

  navigateToItem = item => () => {
    this.props.onNavigate && this.props.onNavigate(item);
  }

  render () {
    if (!this.props.pipelinesLibrary.loaded && this.props.pipelinesLibrary.pending) {
      return <Icon type="loading" />;
    }
    if (this.props.pipelinesLibrary.error) {
      return null;
    }
    return (
      <div
        style={{
          padding: 5,
          minWidth: '150px',
          display: 'inline-block',
          fontSize: '18px'
        }}>
        {
          this.items.map((item, index, array) => {
            const isLast = index === array.length - 1;
            const icon = item.icon ? (
              <Icon
                type={item.icon}
                className={classNames(item.iconClassName, {'cp-sensitive': item.sensitive})}
                style={{marginRight: 5}}
              />
            ) : null;
            const lock = item.lock ? (
              <Icon
                type="lock"
                className={classNames(item.lockClassName, {'cp-sensitive': item.sensitive})}
                style={{marginRight: 5}}
              />
            ) : null;
            if (isLast) {
              return [
                <div
                  key={`item-${index}`}
                  style={{
                    display: 'inline-block',
                    marginLeft: -2
                  }}>
                  {icon}
                  {lock}
                  <EditableField
                    text={this.props.textEditableField}
                    displayText={
                      this.props.displayTextEditableField ||
                      `${this.props.textEditableField || item.name}`
                    }
                    editClassName={styles.breadcrumbsInput}
                    style={{
                      paddingLeft: '0px',
                      background: 'transparent',
                      textDecoration: 'none',
                      outline: 'none',
                      transition: 'color .3s ease',
                      padding: 1
                    }}
                    readOnly={this.props.readOnlyEditableField}
                    className={this.props.classNameEditableField}
                    allowEpmty={false}
                    onSave={this.renameItem}
                    editStyle={this.props.editStyleEditableField}
                  />
                </div>
              ];
            } else if (this.props.onNavigate) {
              return [
                <div
                  key={`item-${index}`}
                  onClick={this.navigateToItem(item)}
                  style={{
                    color: 'inherit',
                    cursor: 'pointer',
                    display: 'inline-block',
                    verticalAlign: 'baseline'
                  }}>
                  {icon}
                  {lock}
                  {item.name}
                </div>,
                <Icon
                  key={`divider-${index}`}
                  type="caret-right"
                  style={{
                    lineHeight: 2,
                    verticalAlign: 'middle',
                    margin: '0px 5px',
                    fontSize: 'small'
                  }}
                />
              ];
            }
            return [
              <Link
                key={`item-${index}`}
                to={item.url}
                style={{
                  verticalAlign: 'baseline',
                  color: 'inherit'
                }}>
                {icon}
                {lock}
                {item.name}
              </Link>,
              <Icon
                key={`divider-${index}`}
                type="caret-right"
                style={{
                  lineHeight: 2,
                  verticalAlign: 'middle',
                  margin: '0px 5px',
                  fontSize: 'small'
                }}
              />
            ];
          }).reduce((result, itemsArray) => {
            result.push(...itemsArray.filter(i => !!i));
            return result;
          }, [])
        }
        {
          this.props.subject ? (<Owner subject={this.props.subject} />) : null
        }
      </div>
    );
  }
}
