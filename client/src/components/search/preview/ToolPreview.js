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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {Icon, Row} from 'antd';
import LoadTool from '../../../models/tools/LoadTool';
import LoadToolAttributes from '../../../models/tools/LoadToolAttributes';
import ToolImage from '../../../models/tools/ToolImage';
import renderHighlights from './renderHighlights';
import renderSeparator from './renderSeparator';
import {metadataLoad, renderAttributes} from './renderAttributes';
import {PreviewIcons} from './previewIcons';
import styles from './preview.css';
import Remarkable from 'remarkable';
import {ScanStatuses} from '../../tools/utils';
import VersionScanResult from '../../tools/elements/VersionScanResult';
import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';

const MarkdownRenderer = new Remarkable('full', {
  html: true,
  xhtmlOut: true,
  breaks: false,
  langPrefix: 'language-',
  linkify: true,
  linkTarget: '',
  typographer: true,
  highlight: function (str, lang) {
    lang = lang || 'bash';
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(lang, str).value;
      } catch (__) {}
    }
    try {
      return hljs.highlightAuto(str).value;
    } catch (__) {}
    return '';
  }
});

@inject('metadataCache', 'preferences')
@inject((stores, params) => {
  return {
    ...stores,
    tool: params.item && params.item.id ? new LoadTool(params.item.id) : null,
    versions: params.item && params.item.id ? new LoadToolAttributes(params.item.id) : null,
    dockerRegistries: stores.dockerRegistries,
    metadata: metadataLoad(params, 'TOOL', stores)
  };
})
@observer
export default class ToolPreview extends React.Component {
  static propTypes = {
    item: PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      parentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      name: PropTypes.string,
      description: PropTypes.string
    })
  };

  @computed
  get dockerRegistry () {
    if (this.props.dockerRegistries.loaded && this.props.tool.loaded) {
      return (this.props.dockerRegistries.value.registries || [])
        .filter(r => r.id === this.props.tool.value.registryId)[0];
    }
    return null;
  }

  @computed
  get name () {
    if (this.props.tool && this.props.tool.loaded && this.props.dockerRegistries.loaded) {
      const [, tool] = this.props.tool.value.image.split('/');
      return tool;
    }
    return this.props.item.name;
  }

  @computed
  get path () {
    if (this.props.tool && this.props.tool.loaded && this.props.dockerRegistries.loaded) {
      const {registries} = this.props.dockerRegistries.value;
      let {registry} = this.props.tool.value;
      if (registries && registries.length) {
        const [r] = registries.filter(r => `${r.id}` === `${this.props.tool.value.registryId}`);
        if (r) {
          registry = r.description || r.externalUrl || r.path;
        }
      }
      const [group] = this.props.tool.value.image.split('/');
      const style = {
        marginRight: 0
      };
      return [
        <span style={style}>{registry}</span>,
        <Icon type="caret-right" style={style} />,
        <span style={style}>{group}</span>
      ];
    }
    return null;
  }

  @computed
  get description () {
    if (this.props.tool && this.props.tool.loaded) {
      return this.props.tool.value.shortDescription || this.props.item.description;
    }
    return this.props.item.description;
  }

  renderFullDescription = () => {
    if (this.props.tool && this.props.tool.loaded && this.props.tool.value.description) {
      return (
        <div className={styles.contentPreview}>
          <div className={styles.mdPreview}>
            <div
              dangerouslySetInnerHTML={{__html: MarkdownRenderer.render(this.props.tool.value.description)}} />
          </div>
        </div>
      );
    }
    return null;
  };

  @computed
  get toolVersionScanResults () {
    const data = [];
    if (this.props.versions.loaded &&
      this.props.versions.value &&
      this.props.versions.value.versions) {
      let keyIndex = 0;
      const versionsByDigest = {};
      const versions = this.props.versions.value.versions;

      versions.forEach(version => {
        if (version.attributes && version.attributes.digest) {
          if (!versionsByDigest[version.attributes.digest]) {
            versionsByDigest[version.attributes.digest] = [];
          }
          versionsByDigest[version.attributes.digest].push(version.version);
        }
      });

      versions.forEach(currentVersion => {
        const scanResult = currentVersion.scanResult || {};
        const versionAttributes = currentVersion.attributes;

        const vulnerabilities = scanResult.vulnerabilities || [];
        const countCriticalVulnerabilities =
          vulnerabilities.filter(vulnerabilitie => vulnerabilitie.severity === 'Critical').length;
        const countHighVulnerabilities =
          vulnerabilities.filter(vulnerabilitie => vulnerabilitie.severity === 'High').length;
        const countMediumVulnerabilities =
          vulnerabilities.filter(vulnerabilitie => vulnerabilitie.severity === 'Medium').length;
        const countLowVulnerabilities =
          vulnerabilities.filter(vulnerabilitie => vulnerabilitie.severity === 'Low').length;
        const countNegligibleVulnerabilities =
          vulnerabilities.filter(vulnerabilitie => vulnerabilitie.severity === 'Negligible').length;

        const digestAliases = versionAttributes && versionAttributes.digest
          ? versionsByDigest[versionAttributes.digest]
            .filter(version => version !== currentVersion.version)
          : [];

        data.push({
          key: keyIndex,
          name: currentVersion.version,
          digest: versionAttributes && versionAttributes.digest ? versionAttributes.digest : '',
          digestAliases,
          size: versionAttributes && versionAttributes.size ? versionAttributes.size : '',
          modificationDate: versionAttributes && versionAttributes.modificationDate
            ? versionAttributes.modificationDate : '',
          scanDate: scanResult.scanDate || '',
          status: scanResult.status,
          successScanDate: scanResult.successScanDate || '',
          allowedToExecute: scanResult.allowedToExecute,
          vulnerabilitiesStatistics: scanResult.status === ScanStatuses.notScanned ? null : {
            critical: countCriticalVulnerabilities,
            high: countHighVulnerabilities,
            medium: countMediumVulnerabilities,
            low: countLowVulnerabilities,
            negligible: countNegligibleVulnerabilities
          }
        });
        keyIndex += 1;
      });
    }
    return data;
  }

  renderVersions = () => {
    if (this.props.versions) {
      if (this.props.versions.pending) {
        return <Row className={styles.contentPreview} type="flex" justify="center"><Icon type="loading"/></Row>;
      }
      if (this.props.versions.error) {
        return (
          <div className={styles.contentPreview}>
            <span style={{color: '#ff556b'}}>{this.props.versions.error}</span>
          </div>
        );
      }
      if (this.toolVersionScanResults.length) {
        const cellStyle = {
          padding: '5px 5px 5px 0px',
          whiteSpace: 'nowrap'
        };
        return (
          <div className={styles.contentPreview}>
            <table style={{minWidth: '100%'}}>
              <tbody>
                {
                  this.toolVersionScanResults.map((version, index) => {
                    return (
                      <tr key={index}>
                        <td style={cellStyle}>{version.name}</td>
                        <td style={cellStyle}>
                          {
                            this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) &&
                            <VersionScanResult
                              className={styles.toolVersionScanResults}
                              result={version.vulnerabilitiesStatistics} />
                          }
                        </td>
                        <td style={cellStyle}>{version.digest}</td>
                      </tr>
                    );
                  })
                }
              </tbody>
            </table>
          </div>
        );
      }
    }
    return null;
  };

  renderHeader = () => {
    const renderMainInfo = () => {
      const name = (
        <Row key="name" className={styles.title} type="flex" align="middle">
          <Icon type={PreviewIcons[this.props.item.type]} />
          <span>{this.name}</span>
        </Row>
      );
      const path = this.path &&
        (
          <Row key="path" className={styles.subTitle} type="flex" align="middle">
            {
              this.path
                .map((n, index) => <span style={{marginRight: 3}} key={index}>{n}</span>)
            }
          </Row>
        );
      const description = this.description &&
        (
          <Row key="description" className={styles.toolDescription}>
            {this.description}
          </Row>
        );
      return [name, path, description].filter(i => !!i);
    };
    if (this.props.tool && this.props.tool.loaded && this.props.tool.value.iconId) {
      return (
        <Row type="flex" className={styles.header} align="middle">
          <img
            style={{
              backgroundColor: 'rgba(255, 255, 255, 0.05)',
              width: 80,
              borderRadius: 5
            }}
            alt={this.props.tool.value.image}
            src={ToolImage.url(this.props.item.id, this.props.tool.value.iconId)} />
          <div style={{paddingLeft: 10, flex: 1}}>
            {renderMainInfo()}
          </div>
        </Row>
      );
    }
    return (
      <div className={styles.header}>
        {renderMainInfo()}
      </div>
    );
  };

  render () {
    if (!this.props.item) {
      return null;
    }
    const highlights = renderHighlights(this.props.item);
    const fullDescription = this.renderFullDescription();
    const attributes = renderAttributes(this.props.metadata);
    const versions = this.renderVersions();
    return (
      <div className={styles.container}>
        {this.renderHeader()}
        <div className={styles.content}>
          {highlights && renderSeparator()}
          {highlights}
          {versions && renderSeparator()}
          {versions}
          {attributes && renderSeparator()}
          {attributes}
          {fullDescription && renderSeparator()}
          {fullDescription}
        </div>
      </div>
    );
  }
}
