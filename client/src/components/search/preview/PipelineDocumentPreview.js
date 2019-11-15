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
import VersionFile from '../../../models/pipelines/VersionFile';
import renderHighlights from './renderHighlights';
import renderSeparator from './renderSeparator';
import {PreviewIcons} from './previewIcons';
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

const previewLoad = (params) => {
  if (params.item && params.item.parentId && params.item.description && params.item.id) {
    return new VersionFile(params.item.parentId, params.item.id, params.item.description);
  } else {
    return null;
  }
};

@inject('pipelines')
@inject((stores, params) => {
  const {pipelines} = stores;
  return {
    preview: previewLoad(params),
    version: params.item.description,
    pipeline: params.item && params.item.parentId
      ? pipelines.getPipeline(params.item.parentId)
      : null
  };
})
@observer
export default class PipelineDocumentPreview extends React.Component {
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
      if (this.props.preview.error) {
        return {
          error: this.props.preview.error
        };
      }
      const preview = this.props.preview.response
        ? atob(this.props.preview.response)
        : '';
      const noContent = !preview;
      return {
        preview,
        truncated: false,
        noContent,
        error: null,
        mayBeBinary: false
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

  @computed
  get parentFolders () {
    if (this.props.item.id) {
      return this.props.item.id.split('/').slice(0, -1);
    }
    return [];
  }

  @computed
  get fileName () {
    if (this.props.item.id || this.props.item.name) {
      return (
        <span>
          <Icon type={PreviewIcons[this.props.item.type]} />
          {(this.props.item.id || this.props.item.name).split('/').pop()}
        </span>
      );
    }
    return null;
  }

  renderDescription = () => {
    const paths = [];
    if (this.props.pipeline && this.props.pipeline.loaded) {
      paths.push(
        <span>
          <Icon type="fork" />
          {this.props.pipeline.value.name}
        </span>
      );
    }
    if (this.props.version) {
      paths.push(
        <span>
          <Icon type="tag" />
          {this.props.version}
        </span>
      );
    }
    paths.push(...this.parentFolders);
    return paths.reduce((result, current, index, arr) => {
      result.push(<span key={index * 2} style={{marginRight: 0}}>{current}</span>);
      if (index < arr.length - 1) {
        result.push(<Icon key={index * 2 + 1} type="caret-right" />);
      }
      return result;
    }, []);
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
    const preview = this.renderPreview();
    return (
      <div className={styles.container}>
        <div className={styles.header}>
          <Row className={styles.title}>
            <span>{this.fileName}</span>
          </Row>
          <Row className={styles.description}>
            {this.renderDescription()}
          </Row>
        </div>
        <div className={styles.content}>
          {highlights && renderSeparator()}
          {highlights}
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
