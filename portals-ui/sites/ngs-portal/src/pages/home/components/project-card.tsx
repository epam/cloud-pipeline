import { Tag, Badge, FlexRow, RichTextView } from '@epam/uui';
import ContentPersonFillIcon from '@epam/assets/icons/content-person-fill.svg?react';
import cn from 'classnames';
import { Link } from 'react-router-dom';
import type { Project } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import HighlightedText from '../../../shared/highlight-text';

type Props = CommonProps & {
  project: Project;
  accessRights: {
    read: boolean;
    write: boolean;
    execute: boolean;
  };
  description?: string;
  tags?: string[];
  highlightedText?: string;
};

export const ProjectCard = ({
  project,
  description,
  accessRights,
  tags,
  highlightedText,
  className,
  style,
}: Props) => {
  const { id, name, owner } = project;

  const hasSomeRights =
    accessRights && Object.values(accessRights).some((right) => Boolean(right));

  return (
    <div
      className={cn(
        'px-4 py-2 bg-white w-full space-y-2 border-[var(--uui-neutral-30)]',
        className,
      )}
      style={style}>
      {tags?.length && (
        <FlexRow columnGap="6" size="24">
          {tags.map((tag) => (
            <Tag caption={tag} size="24" />
          ))}
        </FlexRow>
      )}

      <FlexRow columnGap="12" size="24">
        <Link
          className="text-lg text-[var(--uui-link)] hover:text-[var(--uui-link-hover)] no-underline"
          to={`/project/${id}`}>
          <HighlightedText search={highlightedText}>{name}</HighlightedText>
        </Link>
        <Badge
          icon={ContentPersonFillIcon}
          caption={owner}
          color="neutral"
          size="18"
          cx="shrink-0"
        />
      </FlexRow>

      {hasSomeRights && (
        <FlexRow columnGap="6" size="30">
          {accessRights.read && (
            <Badge size="24" fill="outline" caption="Read" color="info" />
          )}
          {accessRights.write && (
            <Badge size="24" fill="outline" caption="Write" color="warning" />
          )}
          {accessRights.execute && (
            <Badge size="24" fill="outline" caption="Execute" color="success" />
          )}
        </FlexRow>
      )}

      {description && <RichTextView>{description}</RichTextView>}
    </div>
  );
};
