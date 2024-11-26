import { useEffect } from 'react';
import { List } from '@cloud-pipeline/components';
import { useProjectsState } from '../../state/projects/hooks';
import { loadProjects } from '../../state/projects/load-projects';
import type { Project } from '@cloud-pipeline/core';
import './style.css';

export const Home = () => {
  const { projects } = useProjectsState();
  useEffect(() => {
    loadProjects()
      .then(() => {})
      .catch(() => {});
  }, []);
  if (!projects) {
    return null;
  }
  return (
    <div className="flex h-full gap-5 overflow-hidden flex-nowrap justify-around p-2">
      <List
        className="list-container"
        header={<div className="p-2 list-header-container">Projects</div>}
        footer={<div className="p-2">List footer</div>}
        data={projects}
        virtualized
        renderItem={(item: Project) => (
          <div className="p-2" style={{ height: 100 }}>
            {item.name}
          </div>
        )}
        style={{ flex: 1 }}
      />
      <List
        className="list-container"
        header={<div className="p-2">List header</div>}
        footer={<div className="p-2">List footer</div>}
        data={projects}
        virtualized
        renderItem={(item: Project) => (
          <div className="p-2" style={{ height: 100 }}>
            {item.name}
          </div>
        )}
        style={{ flex: 1 }}
      />
      <List
        className="list-container"
        data={projects}
        virtualized
        renderItem={(item: Project) => (
          <div className="p-2" style={{ height: 300 }}>
            {item.name}
          </div>
        )}
        style={{ flex: 1 }}
      />
    </div>
  );
};
