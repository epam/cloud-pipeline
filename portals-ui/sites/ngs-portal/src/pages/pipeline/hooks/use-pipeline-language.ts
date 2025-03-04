import { useLoadableState } from '../../../shared/hooks';
import { fetchPipelineLanguage } from '@cloud-pipeline/api';

export const usePipelineLanguage = (id: number, version: string) => {
  const { state: language, pending, error } = useLoadableState(fetchPipelineLanguage, id, version);
  return { language, pending, error };
};
