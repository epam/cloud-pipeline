import { Tag, Badge, FlexRow, RichTextView } from '@epam/uui';
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

type Props = CommonProps & {
  project: Project;
  description?: string;
  tags?: string[];
  highlightedText?: string;
};

export const ProjectCard = ({
  project,
  description,
  tags,
  highlightedText,
  className,
  style,
}: Props) => {
  const { id, name, owner, mask } = project;

  const read = readAllowed(mask);
  const write = writeAllowed(mask);
  const execute = executeAllowed(mask);

  const hasSomeRights = read || write || execute;

  return (
    <div
      className={cn('px-2 py-1 bg-white w-full space-y-1', className)}
      style={style}>
      {tags?.length && (
        <FlexRow columnGap="6" size="24">
          {tags.map((tag) => (
            <Tag caption={tag} size="24" />
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
