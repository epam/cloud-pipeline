import { Tag, Badge, FlexRow, RichTextView } from '@epam/uui';
import ContentPersonFillIcon from '@epam/assets/icons/content-person-fill.svg?react';
import cn from 'classnames';
import { Link } from 'react-router-dom';
import type { Pipeline } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import HighlightedText from '../../../shared/highlight-text';

type Props = CommonProps & {
  pipeline: Pipeline;
  highlightedText?: string;
  tags?: string[];
};

export const PipelineCard = ({
  pipeline,
  tags,
  highlightedText,
  className,
  style,
}: Props) => {
  const { id, name, owner, description } = pipeline;

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
          to={`/pipeline/${id}`}>
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

      {description && <RichTextView>{description}</RichTextView>}
    </div>
  );
};
