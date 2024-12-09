import { Tag, Badge, FlexRow, RichTextView } from '@epam/uui';
import ContentPersonFillIcon from '@epam/assets/icons/content-person-fill.svg?react';
import cn from 'classnames';
import { Link } from 'react-router-dom';
import type { Pipeline } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import HighlightedText from '../../../shared/highlight-text';
import { NgsUserCard } from '../../../widgets/ngs-user-card';
import './style.css';
import { useMemo } from 'react';
import { NgsTag } from '../../../widgets/ngs-tag';

type Props = CommonProps & {
  pipeline: Pipeline;
  highlightedText?: string;
  mode?: 'standard' | 'extended';
};

type MappedTag = {
  key: string;
  type: 'string';
  value: string;
};

function mapTag(key: string, value: unknown): MappedTag | undefined {
  if (typeof value === 'string') {
    return {
      key,
      type: 'string',
      value,
    };
  }
  if (typeof value === 'object') {
    const { type = 'string', value: tagValue } = value as Record<
      string,
      unknown
    >;
    if (type === 'string' && typeof tagValue === 'string') {
      return {
        key,
        type,
        value: tagValue,
      };
    }
  }
  return undefined;
}

const __UNSAFE__will_be_removed_tagsToDisplay: string[] | undefined = undefined;
const __UNSAFE__will_be_removed_tagsToHide: string[] | undefined = undefined;

function filterTag(tag: MappedTag): boolean {
  let allow = true;
  if (
    __UNSAFE__will_be_removed_tagsToDisplay !== undefined &&
    __UNSAFE__will_be_removed_tagsToDisplay.length > 0
  ) {
    allow = __UNSAFE__will_be_removed_tagsToDisplay
      .map((t) => t.toLowerCase())
      .includes(tag.key.toLowerCase());
  }
  if (
    allow &&
    __UNSAFE__will_be_removed_tagsToHide &&
    __UNSAFE__will_be_removed_tagsToHide.length > 0
  ) {
    allow = !__UNSAFE__will_be_removed_tagsToHide
      .map((t) => t.toLowerCase())
      .includes(tag.key.toLowerCase());
  }
  return allow;
}

export const PipelineCard = ({
  pipeline,
  highlightedText,
  className,
  style,
  mode = 'standard',
}: Props) => {
  const { id, name, owner, data = {}, description } = pipeline;
  const tags = useMemo(
    () =>
      Object.entries(data ?? {})
        .map(([key, value]) => mapTag(key, value))
        .filter(Boolean) as MappedTag[],
    [data],
  );

  const filteredTag = useMemo(() => tags.filter(filterTag), [tags]);
  return (
    <div className={cn('ngs-container', className)} style={style}>
      {/* {tags?.length && (
        <div className="flex flex-wrap gap-1">
          {tags.map((tag) => (
            <Tag caption={tag} size="18" />
          ))}
        </div>
      )} */}
      {filteredTag.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {filteredTag.map((tag) => (
            <NgsTag
              key={tag.key}
              tag={tag.key}
              value={tag.value}
              size="18"
              className="shrink-0"
            />
          ))}
        </div>
      )}

      <FlexRow columnGap="12" size="24" alignItems="center">
        <Link
          className="text-[var(--uui-link)] hover:text-[var(--uui-link-hover)] no-underline"
          to={`/pipeline/${id}`}>
          <HighlightedText search={highlightedText}>{name}</HighlightedText>
        </Link>

        <Badge
          icon={ContentPersonFillIcon}
          caption={<NgsUserCard userName={owner} showTooltip={false} />}
          color="neutral"
          size="18"
          cx="shrink-0"
        />
      </FlexRow>

      {description && (
        <RichTextView cx="leading-4 text-sm">{description}</RichTextView>
      )}
    </div>
  );
};
