import { stubWhoAmI, stubFolderProjects, stubMetadataLoad, stubRunFilter, stubUsersInfo } from '../support/stubs';

describe('Runs page', () => {
  beforeEach(() => {
    stubWhoAmI();
    stubMetadataLoad();
    stubUsersInfo();
    stubRunFilter();
    stubFolderProjects();

    cy.visit('/#/runs');
  });

  it('loads correctly and shows a title', () => {
    cy.contains('Runs history');
  });
});
