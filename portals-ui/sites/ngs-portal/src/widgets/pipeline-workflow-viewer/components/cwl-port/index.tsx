import type { CWLPort } from '../../types';

type Props = {
  port: CWLPort;
  disabled?: boolean;
};

export function CWLPort({ port }: Props) {
  return (
    <div className="inline-flex items-center">
      <span>{port.id}</span>
      {port?.type?.type ? <span className="ml-1 text-xs tet-faded">({port.type.type})</span> : null}
    </div>
  );
}
