import type { TagProps } from '@cloud-pipeline/components';
import { Tag } from '@cloud-pipeline/components';
import { useMemo } from 'react';
import classNames from 'classnames';
import './style.css';

export type TagValue =
  | string
  | number
  | boolean
  | Record<string, unknown>
  | Array<unknown>;

export type NgsTagProps<Value extends TagValue> = TagProps & {
  tag: string;
  value: Value;
  /**
   * if `true`, display "tag:value"
   */
  showTagName?: boolean;
};

function NgsGeneralTag(props: NgsTagProps<string | number | boolean>) {
  const { className, tag, value, showTagName = false, color } = props;
  return (
    <Tag className={classNames('ngs-tag', className)} color={color}>
      <span className="ngs-tag-content">
        {showTagName ? `${tag}:${String(value)}` : String(value)}
      </span>
    </Tag>
  );
}

function NgsObjectTag(
  props: NgsTagProps<Record<string, unknown> | Array<unknown>>,
) {
  const { className, tag, value, showTagName = false, color } = props;
  const objType = Array.isArray(value)
    ? `${value.length} item${value.length === 1 ? '' : 's'}`
    : 'object';
  // todo: display as a link; an object / array content should be displayed on hover (as popup)
  return (
    <Tag className={classNames('ngs-tag', className)} color={color}>
      <span className="ngs-tag-content">
        {showTagName ? `${tag}:${String(objType)}` : String(objType)}
      </span>
    </Tag>
  );
}

export function NgsTag<Value extends TagValue>(props: NgsTagProps<Value>) {
  const { value, ...rest } = props;
  const formattedValue = useMemo(() => {
    if (typeof value === 'string') {
      try {
        return JSON.parse(value) as Value;
      } catch {
        return value;
      }
    }
    return value;
  }, [value]);
  if (typeof formattedValue === 'object') {
    return <NgsObjectTag {...rest} value={formattedValue} />;
  }
  return <NgsGeneralTag {...rest} value={formattedValue} />;
}
