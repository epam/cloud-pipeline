import { Badge, FlexRow, RichTextView } from '@epam/uui';
import ContentPersonFillIcon from '@epam/assets/icons/content-person-fill.svg?react';
import cn from 'classnames';
import { Link } from 'react-router-dom';
import type { Project } from '@cloud-pipeline/core';
import {
  executeAllowed,
  readAllowed,
  writeAllowed,
} from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import HighlightedText from '../../../shared/highlight-text';
import { NgsUserCard } from '../../../widgets/ngs-user-card';
import { useMemo } from 'react';
import { NgsTag } from '../../../widgets/ngs-tag';
import './style.css';

type Props = CommonProps & {
  project: Project;
  description?: string;
  tags?: string[];
  highlightedText?: string;
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
    const { type = 'string', value: tagValue } = value as Record<any, any>;
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

export const ProjectCard = ({
  project,
  description,
  highlightedText,
  className,
  style,
}: Props) => {
  const { id, name, owner, mask, data } = project;

  const tags = useMemo(
    () =>
      Object.entries(data ?? {})
        .map(([key, value]) => mapTag(key, value))
        .filter(Boolean) as MappedTag[],
    [data],
  );

  const filteredTag = useMemo(() => tags.filter(filterTag), [tags]);

  const read = readAllowed(mask);
  const write = writeAllowed(mask);
  const execute = executeAllowed(mask);

  const hasSomeRights = read || write || execute;

  return (
    <div className={cn('ngs-container', className)} style={style}>
      {filteredTag.length > 0 && (
        <FlexRow columnGap="6" size="24">
          {filteredTag.map((tag) => (
            <NgsTag
              key={tag.key}
              tag={tag.key}
              value={tag.value}
              size="18"
              className="shrink-0"
            />
          ))}
        </FlexRow>
      )}

      <FlexRow columnGap="12" size="24" alignItems="center">
        <Link
          className="text-[var(--uui-link)] hover:text-[var(--uui-link-hover)] no-underline"
          to={`/project/${id}`}>
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

      {hasSomeRights && (
        <FlexRow columnGap="6" size="24">
          {read && (
            <Badge size="18" fill="outline" caption="Read" color="info" />
          )}
          {write && (
            <Badge size="18" fill="outline" caption="Write" color="warning" />
          )}
          {execute && (
            <Badge size="18" fill="outline" caption="Execute" color="success" />
          )}
        </FlexRow>
      )}

      {description && <RichTextView>{description}</RichTextView>}
    </div>
  );
};
