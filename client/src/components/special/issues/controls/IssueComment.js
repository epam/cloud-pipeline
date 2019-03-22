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
import {observable} from 'mobx';
import {Mention, Row, Tabs, Icon} from 'antd';
import {ItemTypes} from '../../../pipelines/model/treeStructureFunctions';
import IssueCommentPreview from './IssueCommentPreview';
import FileDropContainer from './FileDropContainer';
import UserFind from '../../../../models/user/UserFind';
import IssueAttachmentUpload from '../../../../models/issues/IssueAttachmentUpload';
import IssueAttachmentLoad from '../../../../models/issues/IssueAttachmentLoad';
import styles from './IssueComment.css';

@inject('issuesRenderer')
@observer
export default class IssueComment extends React.Component {

  static propTypes = {
    height: PropTypes.number,
    value: PropTypes.shape({
      text: PropTypes.string
    }),
    disabled: PropTypes.bool,
    onChange: PropTypes.func,
    placeholder: PropTypes.string,
    style: PropTypes.object,
    links: PropTypes.arrayOf(PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
      link: PropTypes.string,
      displayName: PropTypes.string,
      type: PropTypes.string
    }))
  };
  state = {
    searchValue: null,
    searchType: null,
    suggestions: [],
    pending: false,
    rawText: '',
    clear: false,
    attachments: []
  };
  lastFetchId = 0;

  @observable _links = [];
  mentionControl;

  initializeMentionControl = (control) => {
    this.mentionControl = control;
  };

  userFindPromise = async (search, fetchId) => {
    const request = new UserFind(search);
    await request.fetch();
    if (fetchId === this.lastFetchId) {
      let suggestions = [];
      if (!request.error) {
        suggestions = (request.value || []).map(user => {
          return (
            <Mention.Nav
              value={user.userName}
              data={user}>
              {this.renderUserName(user)}
            </Mention.Nav>
          );
        });
      }
      this.setState({
        pending: false,
        suggestions
      });
    }
  };

  linkFindPromise = async (search, fetchId) => {
    return new Promise((resolve) => {
      const result = (this._links || []).filter(link => {
        return search && search.length > 0 &&
          (link.displayName || '').toLowerCase().indexOf((search || '').toLowerCase()) >= 0;
      });
      const renderIcon = (type) => {
        switch (type) {
          case ItemTypes.pipeline: return <Icon type="fork" />;
          case ItemTypes.configuration: return <Icon type="setting" />;
          case ItemTypes.storage: return <Icon type="hdd" />;
          case 'tool': return <Icon type="tool" />;
        }
        return undefined;
      };
      const suggestions = result.map(link => {
        return (
          <Mention.Nav
            value={`[${link.type}:${link.id}:${link.displayName}]`}
            data={link}>
            <Row align="middle">
              {renderIcon(link.type)} {link.displayName}
            </Row>
          </Mention.Nav>
        );
      });
      this.setState({
        pending: false,
        suggestions
      });
      resolve();
    });
  };

  onSearchChange = (value, prefix) => {
    this.lastFetchId += 1;
    const fetchId = this.lastFetchId;
    this.setState({
      pending: true,
      searchValue: value,
      searchType: prefix
    }, () => {
      if (prefix === '@') {
        return this.userFindPromise(value, fetchId);
      } else if (prefix === '#') {
        return this.linkFindPromise(value, fetchId);
      }
    });
  };

  onSelect = () => {
    this.setState({
      suggestions: [],
      searchValue: null,
      searchType: null
    });
  };

  onChange = (contentState) => {
    this.setState({
      rawText: Mention.toString(contentState)
    }, () => {
      this.props.onChange && this.props.onChange({text: this.state.rawText, attachments: this.state.attachments});
    });
  };

  renderUserName = (user) => {
    if (user.attributes) {
      const getAttributesValues = () => {
        const values = [];
        for (let key in user.attributes) {
          if (user.attributes.hasOwnProperty(key)) {
            values.push(user.attributes[key]);
          }
        }
        return values;
      };
      const attributesString = getAttributesValues().join(', ');
      return (
        <Row type="flex" style={{flexDirection: 'column'}}>
          <Row>{user.userName}</Row>
          <Row><span style={{fontSize: 'smaller'}}>{attributesString}</span></Row>
        </Row>
      );
    } else {
      return user.userName;
    }
  };

  onAttachmentsLoaded = (files) => {
    const attachments = this.state.attachments;
    attachments.push(...files);
    this.setState({attachments});
    let rawText = this.state.rawText || (this.props.value ? (this.props.value.text || '') : '');
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      if ((file.type && file.type.toLowerCase().indexOf('image') >= 0) ||
        (file.type === undefined && (/\.(gif|jpe?g|tiff|png)$/i).test(file.name.toLowerCase()))) {
        rawText += `\n![${file.name}](${IssueAttachmentLoad.generateUrl(file.id)})`;
      } else {
        rawText += ` [${file.name}](${IssueAttachmentLoad.generateUrl(file.id)})`;
      }
    }
    this.props.onChange && this.props.onChange({text: rawText, attachments});
    this.setState({
      attachments,
      rawText,
      clear: true
    });
  };

  render () {
    let notFoundContent = 'Elements not found';
    switch (this.state.searchType) {
      case '@':
        notFoundContent = this.state.searchValue && this.state.searchValue.length > 0
          ? 'Users not found'
          : 'Search users';
        break;
      case '#':
        notFoundContent = this.state.searchValue && this.state.searchValue.length > 0
          ? 'Elements not found'
          : 'Search pipelines, configurations, storages, tools';
        break;
    }
    return (
      <div className={styles.container} style={this.props.style}>
        <Tabs type="card">
          <Tabs.TabPane tab="Write" key="write">
            <FileDropContainer
              onFilesLoaded={this.onAttachmentsLoaded}
              action={IssueAttachmentUpload.url}>
              {
                !this.state.clear &&
                <Mention
                  ref={this.initializeMentionControl}
                  placeholder={this.props.placeholder || 'Description'}
                  disabled={this.props.disabled}
                  defaultValue={Mention.toContentState(this.props.value ? (this.props.value.text || '') : '')}
                  loading={this.state.pending}
                  className={styles.issueDescription}
                  style={{height: this.props.height || 300}}
                  suggestions={this.state.suggestions}
                  onSearchChange={this.onSearchChange}
                  onSelect={this.onSelect}
                  onChange={this.onChange}
                  notFoundContent={notFoundContent}
                  multiLines={true}
                  prefix={['@', '#']}
                />
              }
            </FileDropContainer>
          </Tabs.TabPane>
          <Tabs.TabPane tab="Preview" key="preview">
            <div
              className={styles.issueDescription}
              style={{
                display: 'flex',
                flexDirection: 'column',
                height: this.props.height || 300
              }}>
              <IssueCommentPreview text={this.state.rawText} />
            </div>
          </Tabs.TabPane>
        </Tabs>
      </div>
    );
  }

  componentWillReceiveProps (nextProps) {
    const nextPropsText = nextProps.value ? nextProps.value.text : undefined;
    const text = this.props.value ? this.props.value.text : undefined;
    if (nextPropsText !== text && nextPropsText !== this.state.rawText) {
      this.setState({
        rawText: nextPropsText || '',
        clear: true
      });
    }
  }

  componentDidMount () {
    this.setState({
      rawText: this.props.value ? (this.props.value.text || '') : '',
      clear: true
    }, async () => {
      await this.props.issuesRenderer.fetch();
      this._links = this.props.issuesRenderer.getLinks();
    });
  }

  componentDidUpdate (prevProps, prevState) {
    if (prevState.clear !== this.state.clear && this.state.clear) {
      this.setState({clear: false});
    }
  }
}
