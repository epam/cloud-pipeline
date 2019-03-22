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
import {
  generateTreeData,
  ItemTypes
} from '../../../../components/pipelines/model/treeStructureFunctions';
import {computed, observable} from 'mobx';
import ReactDOMServer from 'react-dom/server';
import Remarkable from 'remarkable';
import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';
import {Icon} from 'antd';

const ELEMENT_LINK_STYLE = {
  whiteSpace: 'nowrap',
  padding: '2px 4px',
  fontSize: '90%',
  color: '#2572c7',
  backgroundColor: 'f2f4f9',
  borderRadius: 4,
  boxShadow: 'inset 0 -1px 0 rgba(0, 0, 0, 0.25)',
  fontWeight: 'lighter'
};

const STYLE =
  '.md-preview {' +
  'flex: 1;' +
  'overflow-y: auto;' +
  'font-size: 9pt;' +
  'padding: 5px;' +
  '}' +
  '.md-preview code {' +
  'padding: 2px 4px;' +
  'font-size: 90%;' +
  'color: #c7254e;' +
  'background-color: #f9f2f4;' +
  'border-radius: 4px;' +
  'box-shadow: inset 0 -1px 0 rgba(0, 0, 0, 0.25);' +
  '}' +
  '.md-preview img {' +
  'max-width: 100%;' +
  '}' +
  '.md-preview h1 {' +
  '  font-size: large;' +
  '}' +
  '.md-preview h2,' +
  '.md-preview h3,' +
  '.md-preview h4,' +
  '.md-preview h5,' +
  '.md-preview h6 {' +
  'font-size: larger;' +
  '}' +
  '.md-preview p {' +
  'margin: 5px 0;' +
  '}' +
  '.md-preview p a {' +
  'margin: 0 2px;' +
  '}' +
  '.md-preview pre {' +
  'display: block;' +
  'padding: 10px;' +
  'margin: 10px 0;' +
  'line-height: 1.5;' +
  'word-break: break-all;' +
  'word-wrap: break-word;' +
  'color: #333;' +
  'background-color: #f5f5f5;' +
  'border: 1px solid #ccc;' +
  'border-radius: 4px;' +
  '}' +
  '.md-preview pre > code {' +
  'padding: 0;' +
  'font-size: inherit;' +
  'color: inherit;' +
  'white-space: pre-wrap;' +
  'background-color: transparent;' +
  'border-radius: 0;' +
  'box-shadow: none;' +
  '}' +
  '.md-preview ul,' +
  '.md-preview ol {' +
  'margin-top: 0;' +
  'margin-bottom: 10px;' +
  'display: block;' +
  'list-style: disc inside;' +
  '}' +
  '.md-preview li {' +
  'display: list-item;' +
  'margin: 5px;' +
  '}';

export default class IssueRenderer {

  @observable _pipelinesLibrary;
  @observable _dockerRegistries;
  @observable _preferences;
  _markdownRenderer; // used for GUI renderer
  _simpleMarkdownRenderer; // used for email body renderer

  constructor (pipelinesLibrary, dockerRegistries, preferences) {
    this._pipelinesLibrary = pipelinesLibrary;
    this._dockerRegistries = dockerRegistries;
    this._preferences = preferences;
    this._markdownRenderer = new Remarkable('commonmark', {
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
    this._simpleMarkdownRenderer = new Remarkable('commonmark', {
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
    this._markdownRenderer.use((renderer) => {
      renderer.renderer.rules['user-name-tag-open']= () => '<span style="font-weight: bold;">';
      renderer.renderer.rules['user-name-tag-close']= () => '</span>';
      renderer.renderer.rules['element-link']= (tokens, index) => {
        const token = tokens[index] || {};
        const {linkType, elementName, identifier} = token;
        let icon;
        switch (linkType) {
          case ItemTypes.pipeline: icon = <Icon type="fork" />; break;
          case ItemTypes.configuration: icon = <Icon type="setting" />; break;
          case ItemTypes.storage: icon = <Icon type="hdd" />; break;
          case 'tool': icon = <Icon type="tool" />; break;
        }
        const [realLink] = (renderer.pipelineLinks || []).filter(link => {
          return `${link.id}` === `${identifier}` && link.type === linkType;
        });
        if (realLink) {
          return renderer.renderReactNode(
            <a
              style={ELEMENT_LINK_STYLE}
              href={`#${realLink.url.startsWith('/') ? realLink.url : `/${realLink.url}`}`}>
              {icon} {elementName}
            </a>
          );
        } else {
          return renderer.renderReactNode(
            <span
              style={ELEMENT_LINK_STYLE}>
              {elementName}
            </span>
          );
        }
      };
      const elementLinkRegex = /@\[([A-Za-z]+):([\d]+):([^W\]]+)\]/;
      const userNameRegex = /@[^ ]+[^\W]/;
      renderer.inline.ruler.push('pipeline-rules', (state, check) => {
        const start = state.pos;
        const marker = state.src[start];
        if (marker !== '@' || state.level >= state.options.maxNesting) {
          return false;
        }
        const test = state.src.substring(state.pos, state.posMax);
        let matchResult = test.match(elementLinkRegex);
        if (matchResult && matchResult.index === 0 && matchResult.length === 4 && !check) {
          const end = state.pos + matchResult[0].length;
          const type = matchResult[1];
          const identifier = matchResult[2];
          const name = matchResult[3];
          state.push({
            type: 'element-link',
            level: state.level + 1,
            linkType: type,
            identifier: identifier,
            elementName: name
          });
          state.pos = end;
          return true;
        } else {
          matchResult = test.match(userNameRegex);
          if (matchResult && matchResult.index === 0 && matchResult.length > 0 && !check) {
            const end = state.pos + matchResult[0].length;
            const name = matchResult[0].substring(1);
            state.push({
              type: 'user-name-tag-open',
              level: state.level + 1
            });
            state.push({
              type: 'text',
              content: name,
              level: state.level + 1
            });
            state.push({
              type: 'user-name-tag-close',
              level: state.level + 1
            });
            state.pos = end;
            return true;
          }
        }
        return false;
      });
    });
    this._simpleMarkdownRenderer.use((renderer) => {
      renderer.renderer.rules['user-name-tag-open']= () => '<span style="font-weight: bold;">';
      renderer.renderer.rules['user-name-tag-close']= () => '</span>';
      renderer.renderer.rules['element-link']= (tokens, index) => {
        const token = tokens[index] || {};
        const {linkType, elementName, identifier} = token;
        const [realLink] = (renderer.pipelineLinks || []).filter(link => {
          return `${link.id}` === `${identifier}` && link.type === linkType;
        });
        if (realLink) {
          return renderer.renderReactNode(
            <a
              style={ELEMENT_LINK_STYLE}
              href={`${renderer.server || ''}#${realLink.url.startsWith('/') ? realLink.url : `/${realLink.url}`}`}>
              {elementName}
            </a>
          );
        } else {
          return renderer.renderReactNode(
            <span
              style={ELEMENT_LINK_STYLE}>
              {elementName}
            </span>
          );
        }
      };
      const elementLinkRegex = /@\[([A-Za-z]+):([\d]+):([^W\]]+)\]/;
      const userNameRegex = /@[^ ]+[^\W]/;
      renderer.inline.ruler.push('pipeline-rules', (state, check) => {
        const start = state.pos;
        const marker = state.src[start];
        if (marker !== '@' || state.level >= state.options.maxNesting) {
          return false;
        }
        const test = state.src.substring(state.pos, state.posMax);
        let matchResult = test.match(elementLinkRegex);
        if (matchResult && matchResult.index === 0 && matchResult.length === 4 && !check) {
          const end = state.pos + matchResult[0].length;
          const type = matchResult[1];
          const identifier = matchResult[2];
          const name = matchResult[3];
          state.push({
            type: 'element-link',
            level: state.level + 1,
            linkType: type,
            identifier: identifier,
            elementName: name
          });
          state.pos = end;
          return true;
        } else {
          matchResult = test.match(userNameRegex);
          if (matchResult && matchResult.index === 0 && matchResult.length > 0 && !check) {
            const end = state.pos + matchResult[0].length;
            const name = matchResult[0].substring(1);
            state.push({
              type: 'user-name-tag-open',
              level: state.level + 1
            });
            state.push({
              type: 'text',
              content: name,
              level: state.level + 1
            });
            state.push({
              type: 'user-name-tag-close',
              level: state.level + 1
            });
            state.pos = end;
            return true;
          }
        }
        return false;
      });
    });
    this._markdownRenderer.pipelineLinks = [];
    this._markdownRenderer.renderReactNode = ReactDOMServer.renderToStaticMarkup;
    this._simpleMarkdownRenderer.pipelineLinks = [];
    this._simpleMarkdownRenderer.renderReactNode = ReactDOMServer.renderToStaticMarkup;
  }

  @computed
  get ready () {
    return this._pipelinesLibrary &&
      this._pipelinesLibrary.loaded &&
      this._dockerRegistries &&
      this._dockerRegistries.loaded &&
      this._preferences &&
      this._preferences.loaded;
  }

  getLinks = () => {
    const links = [];
    if (this._pipelinesLibrary && this._pipelinesLibrary.loaded) {
      const items = generateTreeData(
        this._pipelinesLibrary.value,
        false,
        null,
        [],
        [ItemTypes.folder, ItemTypes.pipeline, ItemTypes.configuration, ItemTypes.storage]
      );
      const makeFlat = (children) => {
        const result = [];
        for (let i = 0; i < (children || []).length; i++) {
          const child = children[i];
          if (child.type === ItemTypes.folder) {
            result.push(...makeFlat(child.children));
          } else {
            result.push({
              id: child.id,
              type: child.type,
              displayName: child.name,
              url: child.url()
            });
          }
        }
        return result;
      };
      links.push(...makeFlat(items));
    }
    if (this._dockerRegistries && this._dockerRegistries.loaded) {
      for (let r = 0; r < (this._dockerRegistries.value.registries || []).length; r++) {
        const registry = this._dockerRegistries.value.registries[r];
        for (let g = 0; g < (registry.groups || []).length; g++) {
          const group = registry.groups[g];
          for (let t = 0; t < (group.tools || []).length; t++) {
            const tool = group.tools[t];
            const [, toolName] = tool.image.split('/');
            links.push({
              type: 'tool',
              displayName: toolName,
              id: tool.id,
              url: `tool/${tool.id}`
            });
          }
        }
      }
    }
    return links;
  };

  static prepare = (raw) => {
    let text = raw || '';
    const elementLinkRegex = /#\[([A-Za-z]+):([\d]+):([^W\]]+)\]/;
    let matchResult = text.match(elementLinkRegex);
    while (matchResult && matchResult.length === 4) {
      const type = matchResult[1];
      const identifier = matchResult[2];
      const name = matchResult[3];
      const start = matchResult.index;
      const end = matchResult.index + matchResult[0].length;
      text = text.substring(0, start) + `@[${type}:${identifier}:${name}]` + text.substring(end);
      matchResult = text.match(elementLinkRegex);
    }
    return text;
  };

  fetch = async () => {
    if (this._pipelinesLibrary) {
      await this._pipelinesLibrary.fetchIfNeededOrWait();
    }
    if (this._dockerRegistries) {
      await this._dockerRegistries.fetchIfNeededOrWait();
    }
    if (this._preferences) {
      await this._preferences.fetchIfNeededOrWait();
    }
  };

  renderAsync = async (raw, embeddedMode = true) => {
    await this.fetch();
    return this.render(raw, embeddedMode);
  };

  render = (raw, embeddedMode = true) => {
    const links = this.getLinks();
    const renderer = !embeddedMode ? this._simpleMarkdownRenderer : this._markdownRenderer;
    renderer.pipelineLinks = links;
    if (!embeddedMode && this._preferences) {
      renderer.server = this._preferences.getPreferenceValue('base.pipe.distributions.url');
    }
    const body = renderer.render(IssueRenderer.prepare(raw));
    if (!embeddedMode) {
      return `<div><style type="text/css" scoped>${STYLE}</style><div class="md-preview">${body}</div></div>`;
    } else {
      return body;
    }
  };

}
