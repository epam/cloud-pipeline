/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import ReactDOMServer from 'react-dom/server';
import Remarkable from 'remarkable';
import highlightJs from 'highlight.js';
import {Icon} from 'antd';
import {ItemTypes} from '../../pipelines/model/treeStructureFunctions';
import {
  fetchCloudPipelineLinks,
  getCloudPipelineLinks,
  getCloudPipelineAbsoluteURLFn,
  getCloudPipelineUrl,
  prepareCloudPipelineLinks,
  processLinks
} from './utilities';
import 'highlight.js/styles/github.css';

/**
 * @typedef {Object} MarkdownRendererOptions
 * @property {function: boolean} [renderPipelineLinks=()=>true]
 * @property {function: boolean} [renderPipelineLinkIcon=()=>false]
 * @property {function(): CloudPipelineLink[]} [links]
 * @property {function} [getLink]
 */

/**
 * Builds markdown renderer
 * @param {MarkdownRendererOptions} [options]
 * @returns {Remarkable}
 */
export default function getMarkdownRenderer (options = {}) {
  const {
    getLink = getCloudPipelineUrl,
    links = () => [],
    renderPipelineLinkIcon = () => false,
    renderPipelineLinks = () => true
  } = options;
  const renderer = new Remarkable('full', {
    html: true,
    xhtmlOut: true,
    breaks: false,
    langPrefix: 'language-',
    linkify: true,
    linkTarget: '',
    typographer: true,
    highlight: function (str, lang) {
      lang = lang || 'bash';
      if (lang && highlightJs.getLanguage(lang)) {
        try {
          return highlightJs.highlight(lang, str).value;
        } catch (__) {}
      }
      try {
        return highlightJs.highlightAuto(str).value;
      } catch (__) {}
      return '';
    }
  });
  renderer.use((o) => {
    o.renderer.rules['user-name-tag-open'] = () => '<span style="font-weight: bold;">';
    o.renderer.rules['user-name-tag-close'] = () => '</span>';
    o.renderer.rules['element-link'] = (tokens, index) => {
      const token = tokens[index] || {};
      const {linkType, elementName, identifier} = token;
      let icon;
      if (renderPipelineLinkIcon()) {
        switch (linkType) {
          case ItemTypes.pipeline:
            icon = <Icon type="fork" />;
            break;
          case ItemTypes.versionedStorage:
            icon = <Icon type="inbox" className="cp-versioned-storage" />;
            break;
          case ItemTypes.configuration:
            icon = <Icon type="setting" />;
            break;
          case ItemTypes.storage:
            icon = <Icon type="hdd" />;
            break;
          case 'tool':
            icon = <Icon type="tool" />;
            break;
        }
      }
      const realLink = (links() || [])
        .find(link => `${link.id}` === `${identifier}` && link.type === linkType);
      if (realLink) {
        return ReactDOMServer.renderToStaticMarkup(
          <a
            href={getLink(realLink.url)}
            className="cp-issue-markdown-link"
          >
            {icon} {elementName}
          </a>
        );
      } else {
        return ReactDOMServer.renderToStaticMarkup(
          <span
            className="cp-issue-markdown-link"
          >
            {elementName}
          </span>
        );
      }
    };
    const elementLinkRegex = /@\[([A-Za-z]+):([\d]+):([^W\]]+)\]/;
    const userNameRegex = /@[^ ]+[^\W]/;
    o.inline.ruler.push('pipeline-rules', (state, check) => {
      if (!renderPipelineLinks()) {
        return false;
      }
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
  return renderer;
}

/**
 * Returns html content for md source
 * @param {string} md
 * @param {MarkdownRendererOptions & CloudPipelineLinksProps & {target: string}} options
 * @returns {Promise<unknown>}
 */
export function renderHtml (md, options = {}) {
  return new Promise((resolve) => {
    fetchCloudPipelineLinks(options)
      .then(() => getCloudPipelineLinks(options))
      .then(links => {
        const renderer = getMarkdownRenderer({
          ...options,
          links: () => links,
          getLink: getCloudPipelineAbsoluteURLFn(options)
        });
        const {
          renderPipelineLinks = () => true,
          target
        } = options;
        let html = renderer.render(
          renderPipelineLinks()
            ? prepareCloudPipelineLinks(md)
            : md
        );
        if (target) {
          html = processLinks(html, target);
        }
        resolve(html);
      })
      .catch(() => resolve(undefined));
  });
}
