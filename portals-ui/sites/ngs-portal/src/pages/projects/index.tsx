import { useEffect } from 'react';
import { Spinner } from '@epam/uui';
import { loadProjects } from '../../state/projects/load-projects';
import { useProjectsState } from '../../state/projects/hooks';

export default function Projects() {
  useEffect(() => {
    loadProjects()
      .then(() => {})
      .catch(() => {});
  }, []);
  const { projects, error, pending } = useProjectsState();
  if (error) {
    return <div>{error}</div>;
  }
  if (pending) {
    return <Spinner />;
  }
  if (!projects) {
    return <div>No data</div>;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
      {projects.map((project) => (
        <span key={project.id}>{project.name}</span>
      ))}
    </div>
  );
}
