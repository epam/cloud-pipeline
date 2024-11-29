import type { CommonProps } from '@cloud-pipeline/components';
import type { TagProps } from '@epam/uui';
import { Tag } from '@epam/uui';
import { useMemo } from 'react';
import classNames from 'classnames';
import './style.css';

export type TagValue =
  | string
  | number
  | boolean
  | Record<any, any>
  | Array<unknown>;

export type NgsTagProps<Value extends TagValue> = CommonProps &
  Pick<TagProps, 'color' | 'size'> & {
    tag: string;
    value: Value;
    /**
     * if `true`, display "tag:value"
     */
    showTagName?: boolean;
  };

function NgsGeneralTag(props: NgsTagProps<string | number | boolean>) {
  const {
    className,
    tag,
    value,
    showTagName = false,
    color,
    size = '18',
  } = props;
  return (
    <Tag
      cx={classNames('ngs-tag', className)}
      color={color}
      size={size}
      caption={<span className="ngs-tag-content">{showTagName ? `${tag}:${String(value)}` : String(value)}</span>}
    />
  );
}

function NgsObjectTag(props: NgsTagProps<Record<any, any> | Array<unknown>>) {
  const {
    className,
    tag,
    value,
    showTagName = false,
    color,
    size = '18',
  } = props;
  const objType = Array.isArray(value)
    ? `${value.length} item${value.length === 1 ? '' : 's'}`
    : 'object';
  // todo: display as a link; an object / array content should be displayed on hover (as popup)
  return (
    <Tag
      cx={classNames('ngs-tag', className)}
      color={color}
      size={size}
      caption={<span className="ngs-tag-content">{showTagName ? `${tag}:${String(objType)}` : String(objType)}</span>}
    />
  );
}

export function NgsTag<Value extends TagValue>(props: NgsTagProps<Value>) {
  const { value, ...rest } = props;
  const formattedValue = useMemo(() => {
    if (typeof value === 'string') {
      try {
        const obj = JSON.parse(value);
        return obj;
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
