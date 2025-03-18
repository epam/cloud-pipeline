import { fetchRunEngineTaskDetails } from '@cloud-pipeline/api';
import type { RunTaskDetailsContentType } from '@cloud-pipeline/core';
import { message } from 'antd';
import { useState, useEffect } from 'react';
import { useParams } from 'react-router';

type Props = {
  hash?: string;
  contentType: RunTaskDetailsContentType;
};

export const useTaskDetails = ({ contentType, hash }: Props) => {
  const { runId } = useParams();
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [content, setContent] = useState<string>();
  const [messageApi, contextHolder] = message.useMessage();

  useEffect(() => {
    const validatedRunId = Number(runId);

    if (hash && validatedRunId) {
      setIsLoading(true);

      void fetchRunEngineTaskDetails({
        hash,
        runId: validatedRunId,
        contentType,
      })
        .then(({ data }) => {
          setContent(data.content);
        })
        .catch((err) => {
          if (err instanceof Error) {
            setError(err.message);
          } else {
            setError('Error fetching task details');
          }
        })
        .finally(() => {
          setIsLoading(false);
        });
    }
  }, [contentType, hash, messageApi, runId]);

  return {
    content,
    isDetailsLoading: isLoading,
    detailsError: error,
    taskDetailsContextHolder: contextHolder,
  };
};
