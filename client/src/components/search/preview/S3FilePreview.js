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
import AWSRegionTag from '../../special/AWSRegionTag';
import {Icon, Row} from 'antd';
import renderHighlights from './renderHighlights';
import renderSeparator from './renderSeparator';
import {metadataLoad, renderAttributes} from './renderAttributes';
import {PreviewIcons} from './previewIcons';
import {SearchItemTypes} from '../../../models/search';
import styles from './preview.css';
import EmbeddedMiew from '../../applications/miew/EmbeddedMiew';
import Papa from 'papaparse';
import Remarkable from 'remarkable';
import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';

const MarkdownRenderer = new Remarkable('commonmark', {
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

const previewLoad = (params, dataStorageCache) => {
  if (params.item && params.item.parentId && params.item.id) {
    return dataStorageCache.getContent(
      params.item.parentId,
      params.item.id
    );
  } else {
    return null;
  }
};

const downloadUrlLoad = (params, dataStorageCache) => {
  if (params.item && params.item.parentId && params.item.id) {
    return dataStorageCache.getDownloadUrl(
      params.item.parentId,
      params.item.id
    );
  } else {
    return null;
  }
};

@inject('metadataCache', 'dataStorageCache')
@inject((stores, params) => {
  const {dataStorageCache, dataStorages} = stores;
  return {
    preview: previewLoad(params, dataStorageCache),
    downloadUrl: downloadUrlLoad(params, dataStorageCache),
    dataStorageInfo: params.item && params.item.parentId ? dataStorages.load(params.item.parentId) : null,
    metadata: metadataLoad(params, 'DATA_STORAGE_ITEM', stores)
  };
})
@observer
export default class S3FilePreview extends React.Component {
  static propTypes = {
    item: PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      parentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      name: PropTypes.string,
      description: PropTypes.string,
    })
  };

  state = {
    pdbError: null,
    imageError: null
  };

  @computed
  get filePreview () {
    if (this.props.preview) {
      if (this.props.preview.pending) {
        return null;
      }
      const preview = this.props.preview.value.content
        ? atob(this.props.preview.value.content)
        : '';
      const truncated = this.props.preview.value.truncated;
      const noContent = !preview;
      const mayBeBinary = this.props.preview.value.mayBeBinary;
      const error = this.props.preview.error;
      return {
        preview,
        truncated,
        noContent,
        error,
        mayBeBinary
      };
    }
    return null;
  }

  @computed
  get structuredTableData () {
    if (this.filePreview && this.filePreview.preview &&
      this.props.item && this.props.item.id.split('.').pop().toLowerCase() === 'csv') {
      const result = {};
      const parseRes = Papa.parse(this.filePreview.preview);
      if (parseRes.errors.length) {
        const firstErr = parseRes.errors.shift();
        result.error = true;
        result.message = `${firstErr.code}: ${firstErr.message}. at row ${firstErr.row + 1}`;
        return result;
      }
      result.data = parseRes.data;
      return result;
    }
    return null;
  }

  renderInfo = () => {
    if (!this.props.dataStorageInfo) {
      return null;
    }
    if (this.props.dataStorageInfo.pending) {
      return (
        <Row className={styles.info}>
          <Icon type="loading" />
        </Row>
      );
    }
    if (this.props.dataStorageInfo.error) {
      return <span style={{color: '#ff556b'}}>{this.props.dataStorageInfo.error}</span>;
    }
    const path = this.props.item.type !== SearchItemTypes.NFSFile
      ? [this.props.dataStorageInfo.value.pathMask, ...this.props.item.id.split('/')]
      : this.props.item.id.split('/').filter(p => !!p);
    return (
      <div className={styles.info}>
        <table>
          <tbody>
            <tr>
              <td style={{whiteSpace: 'nowrap', verticalAlign: 'top'}}>Storage:</td>
              <td style={{paddingLeft: 5}}>
                {this.props.dataStorageInfo.value.name}
                <AWSRegionTag
                  regionId={this.props.dataStorageInfo.value.regionId}
                  size="normal" />
              </td>
            </tr>
            <tr>
              <td style={{whiteSpace: 'nowrap', verticalAlign: 'top'}}>Full path:</td>
              <td style={{paddingLeft: 5}}>
                {
                  path.reduce((result, current, index, arr) => {
                    result.push(<code key={index}>{current}</code>);
                    if (index < arr.length - 1) {
                      result.push(<Icon key={`sep_${index}`} type="caret-right" />);
                    }
                    return result;
                  }, [])
                }
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    );
  };

  renderTextFilePreview = () => {
    if (!this.props.preview) {
      return <div className={styles.contentPreview}>Preview not available.</div>;
    }
    if (this.props.preview.pending) {
      return (
        <Row type="flex" justify="center">
          <Icon type="loading" />
        </Row>
      );
    }
    if (!this.filePreview) {
      return <div className={styles.contentPreview}>Preview not available.</div>;
    }
    if (this.filePreview.error) {
      return (
        <div className={styles.contentPreview}>
          <span style={{color: '#ff556b'}}>{this.filePreview.error}</span>
        </div>
      );
    }
    if (this.filePreview.mayBeBinary) {
      if (this.props.downloadUrl.loaded) {
        return <div className={styles.contentPreview}>Preview not available. <a href={this.props.downloadUrl.value.url} target="_blank" download={this.props.item.name}>Download file</a> to view full contents</div>;
      }
      return <div className={styles.contentPreview}>Preview not available.</div>;
    }
    if (!this.filePreview.preview) {
      return <div className={styles.contentPreview}>Preview not available.</div>;
    }
    return (
      <div className={styles.contentPreview}>
        {
          this.state.pdbError &&
          <div style={{marginBottom: 5}}>
            <span style={{color: '#ff556b'}}>Error loading .pdb visualization: {this.state.pdbError}</span>
          </div>
        }
        {
          this.structuredTableData && this.structuredTableData.error &&
          <div style={{marginBottom: 5}}>
            <span style={{color: '#ff556b'}}>Error loading .csv visualization: {this.structuredTableData.message}</span>
          </div>
        }
        <pre dangerouslySetInnerHTML={{__html: this.filePreview.preview}} />
      </div>
    );
  };

  renderCSVPreview = () => {
    if (this.structuredTableData && !this.structuredTableData.error) {
      return (
        <div className={styles.contentPreview}>
          <table className={styles.csvTable}>
            {
              this.structuredTableData.data.map((row, rowIndex) => {
                return (
                  <tr key={`row-${rowIndex}`}>
                    {
                      row.map((cell, columnIndex) => {
                        return (
                          <td className={styles.csvCell} key={`col-${columnIndex}`}>{cell}</td>
                        );
                      })
                    }
                  </tr>
                );
              })
            }
          </table>
        </div>
      );
    }
  };

  renderMDPreview = () => {
    if (this.filePreview && this.filePreview.preview) {
      return (
        <div className={styles.contentPreview}>
          <div className={styles.mdPreview}>
            <div
              dangerouslySetInnerHTML={{__html: MarkdownRenderer.render(this.filePreview.preview)}} />
          </div>
        </div>
      );
    }
    return null;
  };

  renderImagePreview = () => {
    if (this.props.downloadUrl) {
      if (this.props.downloadUrl.pending) {
        return (
          <Row className={styles.contentPreview} type="flex" justify="center">
            <Icon type="loading" />
          </Row>
        );
      }
      if (this.props.downloadUrl.error) {
        return null;
      }
      if (this.props.downloadUrl.loaded) {
        const onError = () => {
          this.setState({
            imageError: true
          });
        };
        if (this.state.imageError) {
          return this.renderTextFilePreview();
        }
        return (
          <div className={styles.contentPreview}>
            <img
              style={{width: '100%'}}
              onError={onError}
              src={this.props.downloadUrl.value.url} alt={this.props.item.id} />
          </div>
        );
      }
    }
    return null;
  };

  renderPDBPreview = () => {
    const onError = (message) => {
      this.setState({
        pdbError: message
      });
    };
    if (this.state.pdbError) {
      return this.renderTextFilePreview();
    }
    return (
      <div
        className={styles.contentPreview} style={{height: '50vh'}}>
        <EmbeddedMiew
          s3item={{storageId: this.props.item.parentId, path: this.props.item.id}}
          onError={onError} />
      </div>
    );
  };

  renderPreview = () => {
    const extension = this.props.item.id.split('.').pop().toLowerCase();
    const previewRenderers = {
      pdb: this.renderPDBPreview,
      csv: this.renderCSVPreview,
      png: this.renderImagePreview,
      jpg: this.renderImagePreview,
      jpeg: this.renderImagePreview,
      gif: this.renderImagePreview,
      tiff: this.renderImagePreview,
      svg: this.renderImagePreview,
      pdf: this.renderImagePreview,
      md: this.renderMDPreview
    };
    if (previewRenderers[extension]) {
      const preview = previewRenderers[extension]();
      if (preview) {
        return preview;
      }
    }
    return this.renderTextFilePreview();
  };

  render () {
    if (!this.props.item) {
      return null;
    }
    const highlights = renderHighlights(this.props.item);
    const info = this.renderInfo();
    const attributes = renderAttributes(this.props.metadata, true);
    const preview = this.renderPreview();
    return (
      <div className={styles.container}>
        <div className={styles.header}>
          <Row className={styles.title}>
            <Icon type={PreviewIcons[this.props.item.type]} />
            <span>{this.props.item.name}</span>
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
          {info && renderSeparator()}
          {info}
          {attributes && renderSeparator()}
          {attributes}
          {preview && renderSeparator()}
          {preview}
        </div>
      </div>
    );
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.item !== this.props.item) {
      this.setState({pdbError: null, imageError: null});
    }
  }
}
