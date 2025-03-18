import { PageSpinner } from '../../../../../../../shared/ui';
import { RunTaskDetailsContentType } from '@cloud-pipeline/core';
import { prepareMetrics } from '../../../helpers';
import { lazy, Suspense } from 'react';
import { dracula } from 'react-syntax-highlighter/dist/esm/styles/prism';
const SyntaxHighlighter = lazy(() => import('react-syntax-highlighter').then((module) => ({ default: module.Prism })));

type Props = {
  isLoading: boolean;
  error: string | null;
  type: RunTaskDetailsContentType;
  content?: string;
};

export const TaskDetailsModalContent = ({ content, error, isLoading, type }: Props) => {
  if (isLoading) {
    return <PageSpinner />;
  }

  if (error) {
    return <p>{error}</p>;
  }

  if (!content) {
    return <p>No info</p>;
  }

  if (type === RunTaskDetailsContentType.Trace) {
    if (!content) {
      return <p>No metrics found</p>;
    }

    return prepareMetrics(content).map(([key, value]) => {
      if (value) {
        return (
          <div key={key} className="flex items-center gap-2 border-b py-1">
            <p className="font-bold w-2/6">{key}:</p>
            <p className="w-4/6">{value}</p>
          </div>
        );
      }

      return (
        <p key={key} className="border-b font-bold py-1 w-full">
          {key}
        </p>
      );
    });
  }

  return (
    <Suspense fallback={<PageSpinner />}>
      <SyntaxHighlighter language="bash" style={dracula}>
        {content}
      </SyntaxHighlighter>
    </Suspense>
  );
};
