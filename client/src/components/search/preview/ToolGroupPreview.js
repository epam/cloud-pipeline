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
import PropTypes from 'prop-types';
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {Icon, Row} from 'antd';
import renderHighlights from './renderHighlights';
import renderSeparator from './renderSeparator';
import {PreviewIcons} from './previewIcons';
import styles from './preview.css';
import {metadataLoad, renderAttributes} from './renderAttributes';
import ToolImage from '../../../models/tools/ToolImage';

const SHOW_DESCRIPTIONS = true;

@inject((stores, params) => {
  const {dockerRegistries} = stores;

  return {
    dockerRegistries,
    metadata: metadataLoad(params, 'TOOL_GROUP', stores)
  };
})
@observer
export default class ToolGroupPreview extends React.Component {

  static propTypes = {
    item: PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      parentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      name: PropTypes.string,
      description: PropTypes.string
    })
  };

  @computed
  get registries () {
    if (this.props.dockerRegistries.loaded) {
      return (this.props.dockerRegistries.value.registries || []).map(r => r);
    }
    return [];
  }

  @computed
  get currentGroup () {
    if (!this.props.item.id || this.registries.length === 0) {
      return null;
    }
    for (let i = 0; i < this.registries.length; i++) {
      const registry = this.registries[i];
      if (registry.groups) {
        const [group] = registry.groups.filter(g => `${g.id}` === `${this.props.item.id}`);
        if (group) {
          return {...group, registry};
        }
      }
    }
    return null;
  }

  @computed
  get tools () {
    let tools = [];

    if (this.currentGroup) {
      tools = (this.currentGroup.tools || [])
        .map(t => t)
        .sort((a, b) => a.image.localeCompare(b.image));
    }

    return tools;
  }

  renderImageName = (tool) => {
    let nameComponent;
    let nameStyle;

    if (SHOW_DESCRIPTIONS) {
      nameStyle = {
        fontWeight: 'bold'
      };
    }

    if (tool.hasIcon) {}

    nameComponent = (
      <span style={nameStyle}>
        {tool.endpoints && tool.endpoints.length > 0
          ? <Icon type="export" style={{marginRight: 3, fontWeight: 'normal'}} />
          : undefined}
        {tool.image}
      </span>
    );

    if (SHOW_DESCRIPTIONS && (tool.shortDescription || tool.labels)) {
      nameComponent = (
        <Row style={{flex: 1}}>
          <Row style={{marginTop: 2}}>{nameComponent}</Row>
          <Row
            style={{
              marginTop: 5
            }}>
            {
              tool.shortDescription &&
              <Row
                style={
                  tool.labels
                    ? {marginBottom: 5, wordWrap: 'normal'}
                    : {wordWrap: 'normal'}
                }>{tool.shortDescription}</Row>
            }
            {
              tool.labels && tool.labels.length &&
              <Row type="flex">
                {
                  (tool.labels || []).map((label, key) => (
                    <div key={key} className={styles.attribute}>
                      <div className={styles.attributeValue}>{label}</div>
                    </div>
                  ))
                }
              </Row>
            }
          </Row>
        </Row>
      );
    }

    return <Row type="flex">
      {
        tool.iconId &&
        <div style={{marginRight: 5, overflow: 'hidden', width: 33, height: 33, alignSelf: 'center'}}>
          <img src={ToolImage.url(tool.id, tool.iconId)} style={{width: '100%'}} />
        </div>
      }
      {nameComponent}
    </Row>;
  };

  renderTools = () => {
    if (!this.props.dockerRegistries) {
      return null;
    }
    if (this.props.dockerRegistries.pending) {
      return (
        <Row className={styles.contentPreview} type="flex" justify="center">
          <Icon type="loading" />
        </Row>
      );
    }
    if (this.props.dockerRegistries.error) {
      return (
        <div className={styles.contentPreview}>
          <span style={{color: '#ff556b'}}>{this.props.dockerRegistries.error}</span>
        </div>
      );
    }
    if (!this.tools.length) {
      return null;
    }

    const padding = 20;
    const firstCellStyle = {
      paddingRight: padding
    };

    return (
      <div className={styles.contentPreview}>
        <table style={{width: '100%'}}>
          <tbody>
            {
              this.tools.map((item, index) => {
                return (
                  <tr
                    key={item.id}
                    style={index % 2 === 0 ? {backgroundColor: 'rgba(255, 255, 255, 0.05)'} : {}}>
                    <td style={firstCellStyle}>
                      {this.renderImageName(item)}
                    </td>
                  </tr>
                );
              })
            }
          </tbody>
        </table>
      </div>
    );
  };

  @computed
  get name () {
    if (this.currentGroup) {
      return this.currentGroup.name;
    }
    return this.props.item.name;
  }

  @computed
  get path () {
    if (this.currentGroup) {
      const {registry} = this.currentGroup;
      const registryName = registry.description || registry.externalUrl || registry.path;
      const style = {
        marginRight: 0
      };
      return [
        <span key="registry" style={style}>{registryName}</span>,
        <Icon key="arrow" type="caret-right" style={style} />,
        <span key="name" style={style}>{this.name}</span>
      ];
    }
    return <span>{this.name}</span>;
  }

  render () {
    if (!this.props.item) {
      return null;
    }

    const highlights = renderHighlights(this.props.item);
    const attributes = renderAttributes(this.props.metadata);
    const tools = this.renderTools();

    return (
      <div className={styles.container}>
        <div className={styles.header}>
          <Row key="name" className={styles.title} type="flex" align="middle">
            <Icon type={PreviewIcons[this.props.item.type]} />
            <span>{this.path}</span>
          </Row>
          {
            this.props.item.description &&
            <Row className={styles.description}>
              {this.props.item.description}
            </Row>
          }
        </div>
        <div className={styles.content}>
          {highlights && renderSeparator()}
          {highlights}
          {attributes && renderSeparator()}
          {attributes}
          {tools && renderSeparator()}
          {tools}
        </div>
      </div>
    );
  }

}
