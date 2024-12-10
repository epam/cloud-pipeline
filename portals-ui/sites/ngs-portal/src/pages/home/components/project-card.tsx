import { useCallback, useMemo } from 'react';
import { FlexRow, RichTextView } from '@epam/uui';
import cn from 'classnames';
import { Link } from 'react-router-dom';
import type { Pipeline, Project } from '@cloud-pipeline/core';
import { RunStatuses } from '@cloud-pipeline/core';
import { displayDate } from '@cloud-pipeline/core';
import { StatusIcon, Tag, type CommonProps } from '@cloud-pipeline/components';
import HighlightedText from '../../../shared/highlight-text';
import { NgsUserCard } from '../../../widgets/ngs-user-card';
import { NgsTag } from '../../../widgets/ngs-tag';
import { UserIcon } from '@heroicons/react/24/solid';
import { CalendarDaysIcon } from '@heroicons/react/24/outline';
import './style.css';

type Props = CommonProps & {
  project: Project;
  description?: string;
  tags?: string[];
  highlightedText?: string;
  mode?: 'standard' | 'extended';
  lastRun?: Pipeline;
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

function getRandomInt(min: number, max: number): number {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

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
  mode = 'standard',
  lastRun,
  showDescription: showDescriptionProp = false,
}: Props) => {
  const { id, name, data, owner } = project;
  const randomRunningCount = getRandomInt(0, 4);
  const tags = useMemo(
    () =>
      Object.entries(data ?? {})
        .map(([key, value]) => mapTag(key, value))
        .filter(Boolean) as MappedTag[],
    [data],
  );

  const filteredTag = useMemo(() => tags.filter(filterTag), [tags]);

  const { showExtraInfo, showDescription, showStatusInfo } = useMemo(
    () => ({
      showExtraInfo: mode === 'extended',
      showDescription: showDescriptionProp && !!description,
      showStatusInfo: mode === 'extended',
    }),
    [description, mode, showDescriptionProp],
  );

  const renderTags = useCallback(
    () =>
      filteredTag.length > 0 && (
        <div className="flex flex-wrap gap-0">
          {filteredTag.map((tag) => (
            <NgsTag
              key={tag.key}
              tag={tag.key}
              value={tag.value}
              className="shrink-0 mb-0.5"
            />
          ))}
        </div>
      ),
    [filteredTag],
  );

  const renderExtraInfo = useCallback(
    () => (
      <div className="text text-xs flex flex-nowrap gap-2">
        <div className="flex flex-nowrap items-center gap-1">
          <CalendarDaysIcon className="h-3 w-3" />
          {displayDate(project.createdDate)}
        </div>
        <div className="flex flex-nowrap items-center gap-1">
          <UserIcon className="h-3 w-3" />
          {Math.floor(Math.random() * 10 + 1)} users
        </div>
      </div>
    ),
    [project.createdDate],
  );

  return (
    <div
      className={cn(
        mode === 'standard' ? 'ngs-container' : 'ngs-container-extended',
        className,
      )}
      style={style}>
      <div className="flex flex-col gap-1">
        <FlexRow columnGap="12" size="24" alignItems="center">
          <Link
            className="text-[var(--uui-link)] hover:text-[var(--uui-link-hover)] no-underline"
            to={`/project/${id}`}>
            <HighlightedText search={highlightedText}>{name}</HighlightedText>
          </Link>
        </FlexRow>
        <div className="flex flex-nowrap">
          <Tag className="shrink-0">
            <NgsUserCard
              userName={owner}
              showTooltip={false}
              showIcon
              className="h-4"
            />
          </Tag>
          {showExtraInfo && renderExtraInfo()}
        </div>
        {showDescription && <RichTextView>{description}</RichTextView>}
        {renderTags()}
      </div>
      {showStatusInfo && (
        <div className="text text-sm ml-auto mt-0">
          <div className="flex flex-nowrap items-baseline gap-2">
            <StatusIcon
              status={
                randomRunningCount > 0
                  ? RunStatuses.success
                  : RunStatuses.paused
              }
              className="shrink-0"
            />
            {randomRunningCount} running
          </div>
          {lastRun ? (
            <div className="flex flex-nowrap gap-2 w-80 items-baseline">
              <StatusIcon status={RunStatuses.resuming} className="shrink-0" />
              <span className="break-all">
                Last finished: {lastRun.name} (latest)
              </span>
            </div>
          ) : null}
        </div>
      )}
    </div>
  );
};
