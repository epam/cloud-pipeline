import cn from 'classnames';
import { Button, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { PipelineType, type Pipeline } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import { Tag } from '@cloud-pipeline/components';
import HighlightedText from '../../shared/highlight-text';
import { NgsUserCard } from './ngs-user-card';
import { NgsTag } from '../ngs-tag';
import { PipelineToProjectButton } from '../modals';
import './style.css';
import {
  generateLaunchRoutePath,
  generatePipelineRoutePath,
} from '../../shared/constants/routes.ts';
import { usePipelineTags } from '../../shared/tags/use-pipeline-tags.ts';

type Props = CommonProps & {
  pipeline: Pipeline;
  highlightedText?: string;
  mode?: 'standard' | 'extended';
  showDescription?: boolean;
};

export const PipelineCard = ({
  pipeline,
  highlightedText,
  className,
  style,
  mode = 'standard',
  showDescription = false,
}: Props) => {
  const { id, name, owner, data = {}, description, pipelineType } = pipeline;
  const tags = usePipelineTags(data);
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
          to={generatePipelineRoutePath(id)}>
          <HighlightedText search={highlightedText}>{name}</HighlightedText>
        </Link>
        {showDescription && !!description && (
          <Typography className="leading-4 text-sm">{description}</Typography>
        )}
        <div>
          <Tag>
            <NgsUserCard userName={owner} showTooltip={false} showIcon />
          </Tag>
        </div>
        {tags.length > 0 && (
          <div className="flex flex-wrap">
            {tags.map((tag) => (
              <NgsTag
                key={tag.key}
                tag={tag.key}
                value={tag.value}
                className="shrink-0 mb-0.5"
              />
            ))}
          </div>
        )}
      </div>
      {mode === 'extended' && (
        <div className="ml-auto flex gap-1 items-center">
          <PipelineToProjectButton pipeline={pipeline} />
          {pipelineType === PipelineType.pipeline ? (
            <Button style={{ padding: 0 }} type="primary" size="small">
              <Link
                className="flex w-full h-full px-2"
                to={generateLaunchRoutePath(id)}>
                Run
              </Link>
            </Button>
          ) : null}
        </div>
      )}
    </div>
  );
};
