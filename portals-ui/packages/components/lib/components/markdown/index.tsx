import type { ReactNode } from 'react';
import React, { useMemo } from 'react';
import { Converter } from 'showdown';
import classNames from 'classnames';
import './styles.css';
import type {
  MarkdownProps,
  MarkdownTagRenderer,
  MarkdownTagRendererPropsMapper,
} from './types.ts';
import parse from 'html-react-parser';
import type { CommonProps } from '../common.types.ts';

const converter: Converter = new Converter({
  omitExtraWLInCodeBlocks: true,
  noHeaderId: false,
  parseImgDimensions: true,
  simplifiedAutoLink: true,
  literalMidWordUnderscores: true,
  strikethrough: true,
  tables: true,
  tablesHeaderId: false,
  ghCodeBlocks: true,
  tasklists: true,
  smoothLivePreview: true,
  prefixHeaderId: false,
  disableForced4SpacesIndentedSublists: true,
  ghCompatibleHeaderId: true,
  smartIndentationFix: false,
  openLinksInNewWindow: true,
  simpleLineBreaks: true,
});

function correctMarkdown(markdown: string): string {
  return markdown.replace(/^\s+(#+\s.*)$/gm, '$1');
}

const renderers: MarkdownTagRenderer[] = [];

export function registerMarkdownTagRenderer<P extends CommonProps>(
  renderer: React.FunctionComponent<P>,
  tag: string,
  propsMapper?: MarkdownTagRendererPropsMapper<P>,
) {
  renderers.push({
    tag: tag.toLowerCase(),
    renderer: renderer as React.FunctionComponent<CommonProps>,
    propsMapper,
  });
}

function findMarkdownRenderer(
  tag: string,
): MarkdownTagRenderer<CommonProps> | undefined {
  return renderers.find((renderer) => renderer.tag === tag.toLowerCase());
}

function buildTree(
  parsed: string | React.JSX.Element | React.JSX.Element[],
): ReactNode {
  if (typeof parsed === 'string') {
    return parsed;
  }
  if (typeof parsed === 'object' && Array.isArray(parsed)) {
    return parsed.map(buildTree);
  }
  const { props: unmappedProps = {}, key } = parsed;
  let { type } = parsed;
  const { children: unmappedChildren } = unmappedProps;
  const children = unmappedChildren
    ? buildTree(unmappedChildren)
    : unmappedChildren;
  let props = {
    ...unmappedProps,
    children,
    key,
  };
  if (typeof type === 'string') {
    const renderer = findMarkdownRenderer(type);
    if (renderer) {
      type = renderer.renderer;
      props = renderer.propsMapper ? renderer.propsMapper(props) : props;
    }
  }
  return React.createElement(type, props);
}

export default function Markdown(props: MarkdownProps) {
  const { className, style, children } = props;
  const text = typeof children === 'string' ? children : '';
  const corrected = useMemo(() => correctMarkdown(text), [text]);
  const tree = buildTree(parse(converter.makeHtml(corrected)));
  return (
    <div className={classNames(className, 'markdown')} style={style}>
      {tree}
    </div>
  );
}
