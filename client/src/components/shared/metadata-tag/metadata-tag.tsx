import {useMemo} from 'react';
import {CommonProps} from '../../../@types/common.ts';
import {MetadataAttribute} from '../../../@types/metadata.ts';
import classNames from 'classnames';
import {resolveRenderer, stringifyMetadataValue} from './renderers/index.ts';
import './metadata-tag.css';
import {normalizeTag} from './utilities.ts';

export type MetadataTagProps = CommonProps & {
  tag: string;
  value: MetadataAttribute | MetadataAttribute['value'];
  mode?: 'vertical' | 'horizontal';
  showEmpty?: boolean;
};

function MetadataTag(props: MetadataTagProps) {
  const {className, style, value: rawValue, tag, mode = 'horizontal', showEmpty = false} = props;
  const {value, type, secret} = useMemo(() => normalizeTag(tag, rawValue), [tag, rawValue]);
  const rendererContext = useMemo(
    () => ({
      tag,
      value,
      type,
      secret,
      raw: rawValue,
    }),
    [tag, value, secret, type, rawValue],
  );
  const Renderer = useMemo(() => resolveRenderer(rendererContext), [rendererContext]);
  if (!showEmpty && (value === undefined || value === '')) {
    return null;
  }
  return (
    <div
      className={classNames(className, 'metadata-tag', mode)}
      style={style}
      data-metadata-tag={tag}
      data-metadata-tag-type={type}
      data-metadata-tag-value={stringifyMetadataValue(value)}
    >
      <span key="tag" className="metadata-tag-name">
        {tag}
      </span>
      <span key="value" className="metadata-tag-value">
        <Renderer {...rendererContext} />
      </span>
    </div>
  );
}

export {MetadataTag};
