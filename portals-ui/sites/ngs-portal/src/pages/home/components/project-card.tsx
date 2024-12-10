import { useMemo } from 'react';
import { Badge, FlexRow, RichTextView } from '@epam/uui';
import ContentPersonFillIcon from '@epam/assets/icons/content-person-fill.svg?react';
import ActionCalendarFillIcon from '@epam/assets/icons/action-calendar-fill.svg?react';
import cn from 'classnames';
import { Link } from 'react-router-dom';
import type { Pipeline, Project } from '@cloud-pipeline/core';
import { RunStatuses } from '@cloud-pipeline/core';
import {
  displayDate,
  executeAllowed,
  readAllowed,
  writeAllowed,
} from '@cloud-pipeline/core';
import { StatusIcon, type CommonProps } from '@cloud-pipeline/components';
import HighlightedText from '../../../shared/highlight-text';
import { NgsUserCard } from '../../../widgets/ngs-user-card';
import { NgsTag } from '../../../widgets/ngs-tag';
import './style.css';

type Props = CommonProps & {
  project: Project;
  description?: string;
  tags?: string[];
  highlightedText?: string;
  mode?: 'standard' | 'extended';
  lastRun?: Pipeline;
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
}: Props) => {
  const { id, name, owner, mask, data } = project;
  const randomRunningCount = getRandomInt(0, 4);
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

  const { showPermissionTags, showExtraInfo, showDescription, showStatusInfo } =
    useMemo(
      () => ({
        showPermissionTags: mode === 'standard' && hasSomeRights,
        showExtraInfo: mode === 'extended',
        showDescription: !!description,
        showStatusInfo: mode === 'extended',
      }),
      [description, hasSomeRights, mode],
    );

  return (
    <div
      className={cn(
        mode === 'standard' ? 'ngs-container' : 'ngs-container-extended',
        className,
      )}
      style={style}>
      <div className="flex flex-col">
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
          {showExtraInfo && (
            <div className="text text-xs flex flex-nowrap gap-3">
              <div className="flex flex-nowrap items-center gap-1">
                <ActionCalendarFillIcon className="h-3 w-3 fill-current" />
                {displayDate(project.createdDate)}
              </div>
              <div className="flex flex-nowrap items-center gap-1">
                <ContentPersonFillIcon className="h-3 w-3 fill-current" />
                {Math.floor(Math.random() * 10 + 1)} users
              </div>
            </div>
          )}
        </FlexRow>
        {showPermissionTags && (
          <FlexRow columnGap="6" size="24">
            {read && (
              <Badge size="18" fill="outline" caption="Read" color="info" />
            )}
            {write && (
              <Badge size="18" fill="outline" caption="Write" color="warning" />
            )}
            {execute && (
              <Badge
                size="18"
                fill="outline"
                caption="Execute"
                color="success"
              />
            )}
          </FlexRow>
        )}
        {showDescription && <RichTextView>{description}</RichTextView>}
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
