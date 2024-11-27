import { Tag, Badge, FlexRow, RichTextView } from '@epam/uui';
import ContentPersonFillIcon from '@epam/assets/icons/content-person-fill.svg?react';
import cn from 'classnames';
import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';

type Props = {
  id: number;
  name: ReactNode;
  owner: string;
  accessRights: {
    read: boolean;
    write: boolean;
    execute: boolean;
  };
  hasDivider?: boolean;
  description?: string;
  tags?: string[];
};

export const ProjectCard = ({
  id,
  name,
  owner,
  description,
  accessRights,
  tags,
  hasDivider = false,
}: Props) => {
  const hasSomeRights =
    accessRights && Object.values(accessRights).some((right) => Boolean(right));

  return (
    <div
      className={cn('px-4 py-4 bg-white w-full space-y-2', {
        'border-t-2 border-[var(--uui-neutral-30)]': hasDivider,
      })}>
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
          {name}
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
