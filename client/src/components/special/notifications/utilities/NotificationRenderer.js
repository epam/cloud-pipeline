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
import ReactDOMServer from 'react-dom/server';
import Remarkable from 'remarkable';
import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';

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

export default class NotificationRenderer {

  _markdownRenderer;

  constructor () {
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
    this._markdownRenderer.renderReactNode = ReactDOMServer.renderToStaticMarkup;
  }

  render = (raw) => {
    const renderer = this._markdownRenderer;
    const body = renderer.render(raw);

    return `<div><style type="text/css" scoped>${STYLE}</style><div class="md-preview">${body}</div></div>`;
  };

}
