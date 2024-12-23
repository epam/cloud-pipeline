import { useCallback, useMemo } from 'react';
import cn from 'classnames';
import { Typography } from 'antd';
import { Link } from 'react-router-dom';
import type { Pipeline, Project } from '@cloud-pipeline/core';
import { RunStatuses } from '@cloud-pipeline/core';
import { displayDate } from '@cloud-pipeline/core';
import { StatusIcon, Tag, type CommonProps } from '@cloud-pipeline/components';
import HighlightedText from '../../shared/highlight-text';
import { NgsUserCard } from './ngs-user-card';
import { NgsTag } from '../ngs-tag';
import { CalendarDaysIcon } from '@heroicons/react/24/outline';
import { UserIcon } from '@heroicons/react/24/solid';
import { extractTags } from '../../shared/tags';
import './style.css';
import { generateProjectRoutePath } from '../../shared/constants/routes.ts';

type Props = CommonProps & {
  project: Project;
  description?: string;
  tags?: string[];
  highlightedText?: string;
  mode?: 'standard' | 'extended';
  lastRun?: Pipeline;
  showDescription?: boolean;
};

function getRandomInt(min: number, max: number): number {
  return Math.floor(Math.random() * (max - min + 1)) + min;
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
  const tags = useMemo(() => extractTags(data), [data]);
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
      tags.length > 0 && (
        <div className="flex flex-wrap gap-0">
          {tags.map((tag) => (
            <NgsTag
              key={tag.key}
              tag={tag.key}
              value={tag.value}
              className="shrink-0 mb-0.5"
            />
          ))}
        </div>
      ),
    [tags],
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
        <Link
          className="font-semibold truncate"
          to={generateProjectRoutePath(id)}>
          <HighlightedText search={highlightedText}>{name}</HighlightedText>
        </Link>
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
        {showDescription && <Typography>{description}</Typography>}
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
