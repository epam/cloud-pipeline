const API_BASE = Cypress.env('API_BASE');

export const stubPipelineFilter = () => {
  cy.intercept('POST', `${API_BASE}/pipeline/filter*`, {
    status: 200,
    body: {
      payload: [
        {
          id: 1,
          name: 'data-transfer-pipeline',
          createdDate: '2021-06-04 21:47:49.783',
          mask: 15,
          owner: 'PIPE_ADMIN',
          locked: false,
          description:
            'DataTransfer is a system pipeline that is used to automated data transfers from the external sites',
          repository: 'https://git.aws.cloud-pipeline.com/root/datatransferpipeline.git',
          repositorySsh: 'git@git.aws.cloud-pipeline.com:root/datatransferpipeline.git',
          parentFolderId: 1,
          parent: {
            id: 1,
            createdDate: '2025-04-17 20:55:21.588',
            mask: 15,
            locked: false,
            aclClass: 'FOLDER',
            hasMetadata: false,
          },
          aclClass: 'PIPELINE',
          repositoryType: 'GITLAB',
          pipelineType: 'PIPELINE',
          hasMetadata: false,
          codePath: 'src/',
          docsPath: 'docs/',
        },
        {
          id: 2,
          name: 'VS07-06',
          createdDate: '2021-06-07 20:33:31.523',
          mask: 15,
          owner: 'USER1',
          locked: false,
          description: 'new VS',
          repository: 'https://git.aws.cloud-pipeline.com/root/vs0706.git',
          repositorySsh: 'git@git.aws.cloud-pipeline.com:root/vs0706.git',
          parentFolderId: 2,
          parent: {
            id: 2,
            createdDate: '2025-04-17 20:55:21.588',
            mask: 15,
            locked: false,
            aclClass: 'FOLDER',
            hasMetadata: false,
          },
          aclClass: 'PIPELINE',
          repositoryType: 'GITLAB',
          pipelineType: 'VERSIONED_STORAGE',
          hasMetadata: false,
          codePath: 'src/',
          docsPath: 'docs/',
        },
      ],
      status: 'OK',
    },
  }).as('getPipelineFilter');
};
