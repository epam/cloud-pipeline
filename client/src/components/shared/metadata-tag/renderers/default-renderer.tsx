import {Tooltip} from 'antd';
import type {MetadataValueRendererProps} from './types.ts';
import {stringifyMetadataValue} from './utilities.ts';

const MAX_PREVIEW_LENGTH = 100;

function DefaultRenderer(props: MetadataValueRendererProps) {
  const {className, style, value} = props;
  const text = stringifyMetadataValue(value) ?? '';
  if (!text) {
    return null;
  }
  const truncated =
    text.length > MAX_PREVIEW_LENGTH ? `${text.substring(0, MAX_PREVIEW_LENGTH)}...` : text;
  const content = (
    <span className={className} style={style}>
      {truncated}
    </span>
  );
  if (text.length <= MAX_PREVIEW_LENGTH) {
    return content;
  }
  return (
    <Tooltip title={<span style={{wordBreak: 'break-word', whiteSpace: 'pre-wrap'}}>{text}</span>}>
      {content}
    </Tooltip>
  );
}

export {DefaultRenderer};
