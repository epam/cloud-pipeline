import {
  stubWhoAmI,
  stubFolderProjects,
  stubMetadataLoad,
  stubUsersInfo,
  stubPipelineFilter,
  stubAvailableDatastores,
} from '../support/stubs';

describe('Projects page', () => {
  beforeEach(() => {
    stubWhoAmI();
    stubMetadataLoad();
    stubUsersInfo();
    stubFolderProjects();
    stubAvailableDatastores();
    stubPipelineFilter();

    cy.visit('/#/projects');
  });

  it('navigates to project details page', () => {
    cy.get('[data-cy="projects-list-title"]');

    cy.get('[data-cy="project-details-link"]').first().click();

    cy.get('[data-cy="project-details-title"]');
  });
});
