import { fetchPipelineFiles } from '@cloud-pipeline/api';
import type { PipelineFile } from '@cloud-pipeline/core';
import { useEffect, useState } from 'react';

export const usePipelineFiles = (pipelineId: number, version: string) => {
  const [pipelineFiles, setPipelineFiles] = useState<PipelineFile[] | null>(
    null,
  );
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    const fetchFiles = async () => {
      setIsLoading(true);
      setError(null);

      try {
        const files = await fetchPipelineFiles(pipelineId, version);
        setPipelineFiles(files);
      } catch (err) {
        setError(err as Error);
      } finally {
        setIsLoading(false);
      }
    };

    void fetchFiles();
  }, [pipelineId, version]);

  return { pipelineFiles, isLoading, error };
};
