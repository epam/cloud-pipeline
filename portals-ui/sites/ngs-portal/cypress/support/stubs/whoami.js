const API_BASE = Cypress.env('API_BASE');

export const stubWhoAmI = () => {
  cy.intercept('GET', `${API_BASE}/whoami`, {
    statusCode: 200,
    body: {
      payload: {
        id: 1,
        userName: 'PIPE_ADMIN',
        roles: [
          {
            id: 1,
            name: 'ROLE_ADMIN',
            predefined: true,
            userDefault: false,
            defaultProfileId: 1,
            createdDate: '2025-04-07 11:55:28.760',
            mask: 15,
            owner: 'Unauthorized',
            locked: false,
            aclClass: 'ROLE',
          },
          {
            id: 13,
            name: 'ROLE_METADATA_USERS',
            predefined: false,
            userDefault: false,
            createdDate: '2025-04-07 11:55:28.760',
            mask: 15,
            owner: 'Unauthorized',
            locked: false,
            aclClass: 'ROLE',
          },
        ],
        groups: ['ROLE_ADMIN'],
        admin: true,
        blocked: false,
        registrationDate: '2021-06-04 20:08:40.897',
        firstLoginDate: '2021-06-04 21:48:12.519',
        lastLoginDate: '2025-04-07 11:55:10.839',
        attributes: {
          Name: 'pipe_admin',
          Email: 'pipe_admin@example.com',
          LastName: 'pipe_admin',
          FirstName: 'pipe_admin',
        },
        createdDate: '2025-04-07 11:55:28.760',
        mask: 15,
        owner: 'Unauthorized',
        locked: false,
        aclClass: 'PIPELINE',
        email: 'pipe_admin@example.com',
      },
      status: 'OK',
    },
  }).as('getWhoAmI');
};
