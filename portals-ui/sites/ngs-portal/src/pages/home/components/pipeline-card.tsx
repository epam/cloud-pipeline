import { Badge, Button, RichTextView } from '@epam/uui';
import ContentPersonFillIcon from '@epam/assets/icons/content-person-fill.svg?react';
import cn from 'classnames';
import { Link } from 'react-router-dom';
import { noop, type Pipeline } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import HighlightedText from '../../../shared/highlight-text';
import { NgsUserCard } from '../../../widgets/ngs-user-card';
import './style.css';
import { useMemo } from 'react';
import { NgsTag } from '../../../widgets/ngs-tag';
import { useUuiContext } from '@epam/uui-core';
import { PipelineToProjectModal } from '../../../widgets/modals';

type Props = CommonProps & {
  pipeline: Pipeline;
  highlightedText?: string;
  mode?: 'standard' | 'extended';
  showDescription?: boolean;
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
  showDescription = false,
}: Props) => {
  const { uuiModals } = useUuiContext();
  const { id, name, owner, data = {}, description } = pipeline;
  const tags = useMemo(
    () =>
      Object.entries(data ?? {})
        .map(([key, value]) => mapTag(key, value))
        .filter(Boolean) as MappedTag[],
    [data],
  );

  const openAddPipelineModal = () => {
    uuiModals
      .show((props) => (
        <PipelineToProjectModal pipeline={pipeline} {...props} />
      ))
      .then(noop)
      .catch(noop);
  };

  const filteredTag = useMemo(() => tags.filter(filterTag), [tags]);
  return (
    <div
      className={cn(
        mode === 'standard' ? 'ngs-container' : 'ngs-container-extended',
        className,
      )}
      style={style}>
      <div className="flex flex-col gap-1">
        <Link
          className="text-[var(--uui-link)] hover:text-[var(--uui-link-hover)] no-underline"
          to={`/pipeline/${id}`}>
          <HighlightedText search={highlightedText}>{name}</HighlightedText>
        </Link>
        {showDescription && !!description && (
          <RichTextView cx="leading-4 text-sm">{description}</RichTextView>
        )}
        <Badge
          icon={ContentPersonFillIcon}
          caption={<NgsUserCard userName={owner} showTooltip={false} />}
          color="neutral"
          size="18"
          cx="w-fit"
        />
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
      </div>
      {mode === 'extended' && (
        <div className="ml-auto flex items-center">
          <Button
            caption="Add to project"
            fill="none"
            color="secondary"
            size="24"
            onClick={openAddPipelineModal}
          />
        </div>
      )}
    </div>
  );
};
