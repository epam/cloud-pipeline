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

import {visit, SKIP} from 'unist-util-visit';
import {unified} from 'unified';
import remarkParse from 'remark-parse';
import remarkGfm from 'remark-gfm';
import remarkRehype from 'remark-rehype';
import rehypeRaw from 'rehype-raw';
import rehypeHighlight from 'rehype-highlight';
import rehypeStringify from 'rehype-stringify';
import {
  fetchCloudPipelineLinks,
  getCloudPipelineLinks,
  getCloudPipelineAbsoluteURLFn,
  prepareCloudPipelineLinks,
  processLinks,
} from './utilities';
import 'highlight.js/styles/github.css';

const elementLinkRegex = /@\[([A-Za-z]+):(\d+):([^\]]+)]/;
const userNameRegex = /@\S+/;

/**
 * Remark plugin: parses @[Type:id:Name] and @username inline syntax into custom mdast nodes.
 * Custom nodes use data.hName / data.hProperties so remark-rehype converts them automatically.
 */
export function remarkPipelineLinks() {
  return (tree) => {
    visit(tree, 'text', (node, index, parent) => {
      if (!parent || index === null) return;
      const {value} = node;
      const pattern = new RegExp(`${elementLinkRegex.source}|${userNameRegex.source}`, 'g');
      const parts = [];
      let lastIndex = 0;
      let match;
      while ((match = pattern.exec(value)) !== null) {
        if (match.index > lastIndex) {
          parts.push({type: 'text', value: value.slice(lastIndex, match.index)});
        }
        const full = match[0];
        if (full.startsWith('@[')) {
          parts.push({
            type: 'pipelineLink',
            data: {
              hName: 'pipeline-link',
              hProperties: {
                'data-link-type': match[1],
                'data-id': match[2],
                'data-name': match[3],
              },
            },
            children: [],
          });
        } else {
          parts.push({
            type: 'userNameTag',
            data: {hName: 'strong'},
            children: [{type: 'text', value: full.slice(1)}],
          });
        }
        lastIndex = match.index + full.length;
      }
      if (lastIndex < value.length) {
        parts.push({type: 'text', value: value.slice(lastIndex)});
      }
      if (parts.length > 1 || (parts.length === 1 && parts[0].type !== 'text')) {
        parent.children.splice(index, 1, ...parts);
        return [SKIP, index + parts.length];
      }
    });
  };
}

/**
 * Rehype plugin: resolves <pipeline-link> elements to <a> or <span> using the provided links array.
 * Used in renderHtml (string output path) only — the React component uses a components prop instead.
 * @param {{links: Array, getLink: function}} options
 */
export function rehypePipelineLinks({links = [], getLink = (url) => url} = {}) {
  return (tree) => {
    visit(tree, 'element', (node, index, parent) => {
      if (node.tagName !== 'pipeline-link') return;
      const {dataLinkType, dataId, dataName} = node.properties ?? {};
      const realLink = links.find((l) => `${l.id}` === `${dataId}` && l.type === dataLinkType);
      const replacement = {
        type: 'element',
        tagName: realLink ? 'a' : 'span',
        properties: realLink
          ? {href: getLink(realLink.url), className: ['cp-issue-markdown-link']}
          : {className: ['cp-issue-markdown-link']},
        children: [{type: 'text', value: dataName ?? ''}],
      };
      parent.children.splice(index, 1, replacement);
      return [SKIP, index + 1];
    });
  };
}

/**
 * Returns an HTML string for the given markdown source.
 * Async: fetches pipeline link data from stores before rendering.
 * @param {string} md
 * @param {import('./utilities').MarkdownRendererOptions & import('./utilities').CloudPipelineLinksProps & {target?: string, renderPipelineLinks?: function}} options
 * @returns {Promise<string|undefined>}
 */
export function renderHtml(md, options = {}) {
  return new Promise((resolve) => {
    fetchCloudPipelineLinks(options)
      .then(() => getCloudPipelineLinks(options))
      .then((links) => {
        const {renderPipelineLinks = () => true, target} = options;
        const getLink = getCloudPipelineAbsoluteURLFn(options);
        const source = renderPipelineLinks() ? prepareCloudPipelineLinks(md) : md;
        const processor = unified()
          .use(remarkParse)
          .use(remarkGfm)
          .use(remarkPipelineLinks)
          .use(remarkRehype, {allowDangerousHtml: true})
          .use(rehypeRaw)
          .use(rehypePipelineLinks, {links, getLink})
          .use(rehypeHighlight)
          .use(rehypeStringify);
        let html = String(processor.processSync(source));
        if (target) {
          html = processLinks(html, target);
        }
        resolve(html);
      })
      .catch(() => resolve(undefined));
  });
}
