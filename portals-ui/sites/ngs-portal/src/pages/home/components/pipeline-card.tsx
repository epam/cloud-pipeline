import { Tag, Badge, FlexRow, RichTextView } from '@epam/uui';
import ContentPersonFillIcon from '@epam/assets/icons/content-person-fill.svg?react';
import cn from 'classnames';
import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';

type Props = {
  id: number;
  name: ReactNode;
  owner: string;
  hasDivider?: boolean;
  description?: string;
  tags?: string[];
};

export const PipelineCard = ({
  id,
  name,
  owner,
  description,
  tags,
  hasDivider = false,
}: Props) => {
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
          to={`/pipeline/${id}`}>
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

      {description && <RichTextView>{description}</RichTextView>}
    </div>
  );
};
