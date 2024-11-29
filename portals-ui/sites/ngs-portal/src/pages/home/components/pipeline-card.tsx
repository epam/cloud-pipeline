import { Tag, Badge, FlexRow, RichTextView } from '@epam/uui';
import ContentPersonFillIcon from '@epam/assets/icons/content-person-fill.svg?react';
import cn from 'classnames';
import { Link } from 'react-router-dom';
import type { Pipeline } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import HighlightedText from '../../../shared/highlight-text';
import { NgsUserCard } from '../../../widgets/ngs-user-card';
import './style.css';

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
      className={cn('ngs-container', className)}
      style={style}>
      {tags?.length && (
        <FlexRow columnGap="6" size="24">
          {tags.map((tag) => (
            <Tag caption={tag} size="18" />
          ))}
        </FlexRow>
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
