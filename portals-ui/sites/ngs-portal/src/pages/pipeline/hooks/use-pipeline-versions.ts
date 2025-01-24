import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PipelineSearchParams } from '../constants';
import type { PipelineVersion } from '@cloud-pipeline/core';

export const usePipelineVersions = (versions: PipelineVersion[]) => {
  const [searchParams, setSearchParams] = useSearchParams();

  const version =
    searchParams.get(PipelineSearchParams.Version) ?? versions[0]?.name;

  useEffect(() => {
    if (!versions.some(({ name }) => name === version)) {
      // If the version is invalid, reset to the default version
      const newSearchParams = new URLSearchParams(searchParams);
      newSearchParams.delete(PipelineSearchParams.Version);
      setSearchParams(newSearchParams);
    }
  }, [searchParams, setSearchParams, version, versions]);

  const onChangeVersion = (index: number) => {
    const selectedVersion = versions[index]?.name;

    if (selectedVersion) {
      const newSearchParams = new URLSearchParams(searchParams);
      newSearchParams.set(PipelineSearchParams.Version, selectedVersion);
      setSearchParams(newSearchParams);
    }
  };

  return { version, onChangeVersion };
};
