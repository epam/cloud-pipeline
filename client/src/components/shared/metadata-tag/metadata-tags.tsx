import {useMemo} from 'react';
import {CommonProps} from '../../../@types/common.ts';
import {MetadataEntityData} from '../../../@types/metadata.ts';
import classNames from 'classnames';
import {MetadataTag, MetadataTagProps} from './metadata-tag.tsx';
import './metadata-tag.css';
import {SYSTEM_METADATA_TAGS} from './constants.ts';
import {normalizeTag} from './utilities.ts';

function MetadataTags(
  props: CommonProps & {
    metadata?: MetadataEntityData;
    mode?: MetadataTagProps['mode'];
    skipSystem?: boolean;
    skipSecrets?: boolean;
  },
) {
  const {className, style, metadata = {}, mode, skipSystem = false, skipSecrets = false} = props;
  const entries = useMemo(
    () =>
      Object.entries(metadata)
        .map(([key, value]) => normalizeTag(key, value))
        .filter((tag) => !skipSystem || !SYSTEM_METADATA_TAGS.includes(tag.tag))
        .filter((tag) => !skipSecrets || !tag.secret),
    [metadata, skipSystem, skipSecrets],
  );
  return (
    <div
      className={classNames(className, 'inline-flex', 'flex-wrap', 'items-start', 'gap-1')}
      style={style}
    >
      {entries.map((entry) => (
        <MetadataTag
          tag={entry.tag}
          value={entry.value}
          key={entry.tag}
          mode={mode}
          className="shrink"
        />
      ))}
    </div>
  );
}

export {MetadataTags};
