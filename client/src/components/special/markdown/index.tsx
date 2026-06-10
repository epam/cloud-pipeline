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

import {type CSSProperties, type MouseEvent, type ReactNode, useCallback, useMemo} from 'react';
import classNames from 'classnames';
import ReactMarkdown, {type Components, type ExtraProps} from 'react-markdown';
import type {PluggableList} from 'unified';
import remarkGfm from 'remark-gfm';
import rehypeHighlight from 'rehype-highlight';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, {defaultSchema} from 'rehype-sanitize';
import type {Element} from 'hast';
import {
  ForkOutlined,
  HddOutlined,
  InboxOutlined,
  SettingOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import {ItemTypes} from '../../pipelines/model/treeStructureFunctions';
import {useStringPreferenceValue} from '../../../queries/preferences/hooks.ts';
import {prepareCloudPipelineLinks, getCloudPipelineUrl, processLinks} from './utilities';
import {remarkPipelineLinks, renderHtml} from './renderer';
import {type MarkdownLink, useMarkdownLinks} from './hooks.ts';

export {
  injectCloudPipelineLinksHelpers,
  fetchCloudPipelineLinks,
  getCloudPipelineLinks,
  getCloudPipelineAbsoluteURL,
} from './utilities';
export {renderHtml, processLinks};

export type CloudPipelineLinksOptions =
  | boolean
  | {
      absoluteUri?: boolean;
      icon?: boolean;
    };

export type MarkdownSanitizeOptions = {
  tagsToRemove?: string[];
  attributesToRemove?: string[];
};

export type MarkdownProps = {
  className?: string;
  id?: string;
  md?: string;
  style?: CSSProperties;
  target?: string;
  cloudPipelineLinks?: CloudPipelineLinksOptions;
  onClick?: (event: MouseEvent<HTMLDivElement>) => void;
  sanitize?: boolean;
  sanitizeOptions?: MarkdownSanitizeOptions;
};

type MarkdownContentProps = MarkdownProps & {
  links?: MarkdownLink[];
  getLink?: (url: string) => string;
  renderPipelineLinkIcon?: boolean;
};

function buildSanitizeSchema({
  tagsToRemove = [],
  attributesToRemove = [],
}: MarkdownSanitizeOptions = {}) {
  const schema = {
    ...defaultSchema,
    strip: [...(defaultSchema.strip ?? []), ...tagsToRemove],
  };
  if (attributesToRemove.length > 0) {
    schema.attributes = Object.fromEntries(
      Object.entries(defaultSchema.attributes ?? {}).map(([tag, attrs]) => [
        tag,
        (attrs ?? []).filter((attribute) => {
          const name = typeof attribute === 'string' ? attribute : attribute[0];
          return !attributesToRemove.includes(name);
        }),
      ]),
    );
  }
  return schema;
}

function renderPipelineLinkTypeIcon(type: string): ReactNode {
  switch (type) {
    case ItemTypes.pipeline:
      return <ForkOutlined />;
    case ItemTypes.versionedStorage:
      return <InboxOutlined className="cp-versioned-storage" />;
    case ItemTypes.configuration:
      return <SettingOutlined />;
    case ItemTypes.storage:
      return <HddOutlined />;
    case 'tool':
      return <ToolOutlined />;
    default:
      return null;
  }
}

function MarkdownContent({
  className,
  id,
  md,
  style,
  target,
  onClick,
  cloudPipelineLinks,
  sanitize,
  sanitizeOptions,
  links = [],
  getLink = (url) => url,
  renderPipelineLinkIcon = false,
}: MarkdownContentProps) {
  const renderPipelineLinkNode = useCallback(
    ({node}: {node?: Element} & ExtraProps) => {
      const {dataLinkType, dataId, dataName} = node?.properties ?? {};
      const linkType = typeof dataLinkType === 'string' ? dataLinkType : undefined;
      const linkId = dataId === undefined || dataId === null ? undefined : `${dataId}`;
      const linkName = typeof dataName === 'string' ? dataName : '';
      const realLink = links.find((link) => `${link.id}` === linkId && link.type === linkType);
      const icon = renderPipelineLinkIcon && linkType ? renderPipelineLinkTypeIcon(linkType) : null;

      if (realLink) {
        return (
          <a href={getLink(realLink.url)} className="cp-issue-markdown-link">
            {icon} {linkName}
          </a>
        );
      }
      return <span className="cp-issue-markdown-link">{linkName}</span>;
    },
    [getLink, links, renderPipelineLinkIcon],
  );

  const components = useMemo<Components>(() => {
    const anchor: Components['a'] = ({href, children, ...rest}) => (
      <a
        href={href}
        {...rest}
        {...(target ? {target, rel: target === '_blank' ? 'noreferrer' : undefined} : {})}
      >
        {children}
      </a>
    );

    if (!cloudPipelineLinks) {
      return {a: anchor};
    }

    return {
      a: anchor,
      'pipeline-link': renderPipelineLinkNode,
    };
  }, [cloudPipelineLinks, renderPipelineLinkNode, target]);

  const source = cloudPipelineLinks && md ? prepareCloudPipelineLinks(md) : md;
  const remarkPlugins = useMemo<PluggableList>(
    () => [remarkGfm, ...(cloudPipelineLinks ? [remarkPipelineLinks] : [])],
    [cloudPipelineLinks],
  );
  const rehypePlugins = useMemo<PluggableList>(() => {
    const plugins: PluggableList = [rehypeRaw];
    if (sanitize) {
      plugins.push([rehypeSanitize, buildSanitizeSchema(sanitizeOptions)]);
    }
    plugins.push(rehypeHighlight);
    return plugins;
  }, [sanitize, sanitizeOptions]);

  return (
    <div id={id} className={classNames(className, 'markdown')} style={style} onClick={onClick}>
      <ReactMarkdown
        remarkPlugins={remarkPlugins}
        rehypePlugins={rehypePlugins}
        components={components}
        urlTransform={(url) => url}
      >
        {source}
      </ReactMarkdown>
    </div>
  );
}

function MarkdownWithPipelineLinks(props: MarkdownProps) {
  const links = useMarkdownLinks();
  const baseUrl = useStringPreferenceValue('base.pipe.distributions.url') ?? '';
  const getLink = useCallback((url: string) => `${baseUrl}${getCloudPipelineUrl(url)}`, [baseUrl]);
  const {cloudPipelineLinks} = props;
  const renderPipelineLinkIcon =
    !!cloudPipelineLinks &&
    (typeof cloudPipelineLinks !== 'object' ||
      cloudPipelineLinks.icon === undefined ||
      cloudPipelineLinks.icon);

  return (
    <MarkdownContent
      {...props}
      links={links}
      getLink={getLink}
      renderPipelineLinkIcon={renderPipelineLinkIcon}
    />
  );
}

export default function Markdown(props: MarkdownProps) {
  const {md, cloudPipelineLinks} = props;

  if (!md) {
    return null;
  }

  if (cloudPipelineLinks) {
    return <MarkdownWithPipelineLinks {...props} />;
  }

  return <MarkdownContent {...props} />;
}
