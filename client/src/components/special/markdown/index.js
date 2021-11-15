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
import PropTypes from 'prop-types';
import Remarkable from 'remarkable';
import hljs from 'highlight.js';

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

class Markdown extends React.PureComponent {
  render () {
    const {
      className,
      md,
      style
    } = this.props;
    if (!md) {
      return null;
    }
    const html = MarkdownRenderer.render(md);
    console.log(html);
    return (
      <div
        className={className}
        dangerouslySetInnerHTML={{__html: html}}
        style={style}
      />
    );
  }
}

Markdown.propTypes = {
  className: PropTypes.string,
  md: PropTypes.string,
  style: PropTypes.object
};

export default Markdown;
