import { useEffect, useMemo, useState } from 'react';
import type { Project } from '@cloud-pipeline/core';
import { Button, LinkButton } from '@epam/uui';
import { List, ListHeader } from '@cloud-pipeline/components';
import { useProjectsState } from '../../state/projects/hooks';
import { loadProjects } from '../../state/projects/load-projects';
import HighlightedText from '../../shared/highlight-text';
import './style.css';

export const Home = () => {
  const { projects } = useProjectsState();
  const [projectSearch, setProjectSearch] = useState('');
  const [pipelinesSearch, setPipelinesSearch] = useState('');
  useEffect(() => {
    loadProjects()
      .then(() => {})
      .catch(() => {});
  }, []);
  const filteredProjects = useMemo(() => {
    if (!projects) {
      return [];
    }
    return projectSearch
      ? projects.filter((project) =>
          project.name.toLowerCase().includes(projectSearch.toLowerCase()),
        )
      : projects;
  }, [projectSearch, projects]);
  if (!projects) {
    return null;
  }
  return (
    <div className="flex h-full gap-5 overflow-hidden flex-nowrap justify-around p-2">
      <List
        className="list-container"
        header={
          <ListHeader
            className="list-header-container"
            title="Projects"
            controls={
              <Button caption="Add project" size="24" onClick={() => null} />
            }
            search={projectSearch}
            onSearch={setProjectSearch}
          />
        }
        footer={
          <div className="list-footer-container">
            <LinkButton
              caption="View all projects"
              link={{ pathname: '/projects' }}
            />
          </div>
        }
        data={filteredProjects}
        itemKey={(item: Project) => item.id}
        virtualized
        renderItem={(item: Project) => (
          <div className="p-2" style={{ height: 100 }}>
            <HighlightedText search={projectSearch}>
              {item.name}
            </HighlightedText>
          </div>
        )}
        style={{ flex: 1 }}
      />
      <List
        className="list-container"
        header={
          <ListHeader
            className="list-header-container"
            title="Pipelines"
            search={pipelinesSearch}
            onSearch={setPipelinesSearch}
          />
        }
        footer={
          <div className="list-footer-container">
            <LinkButton
              caption="View all pipelines"
              link={{ pathname: '/pipelines' }}
            />
          </div>
        }
        data={projects}
        virtualized
        itemKey={(item: Project) => item.id}
        renderItem={(item: Project) => (
          <div className="p-2" style={{ height: 100 }}>
            {item.name}
          </div>
        )}
        style={{ flex: 1 }}
      />
      <List
        className="list-container"
        header={
          <ListHeader className="list-header-container" title="Run History" />
        }
        footer={
          <div className="list-footer-container">
            <LinkButton caption="View all runs" link={{ pathname: '/runs' }} />
          </div>
        }
        data={projects}
        virtualized
        itemKey={(item: Project) => item.id}
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
