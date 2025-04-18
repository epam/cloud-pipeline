const API_BASE = Cypress.env('API_BASE');

export const stubUsersInfo = () => {
  cy.intercept('GET', `${API_BASE}/users/info`, {
    statusCode: 200,
    body: {
      payload: [
        {
          id: 1,
          name: 'PIPE_ADMIN',
          attributes: {
            Name: 'pipe_admin',
            Email: 'pipe_admin@example.com',
            LastName: 'pipe_admin',
            FirstName: 'pipe_admin',
          },
          roles: [
            {
              id: 1,
              name: 'ROLE_ADMIN',
              predefined: true,
              userDefault: false,
              defaultProfileId: 1,
              createdDate: '2025-04-07 12:23:43.893',
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
              createdDate: '2025-04-07 12:23:43.894',
              mask: 15,
              owner: 'Unauthorized',
              locked: false,
              aclClass: 'ROLE',
            },
          ],
          groups: ['ROLE_ADMIN'],
        },
        {
          id: 1,
          name: 'MR_RODICHENKO',
          attributes: {
            Name: 'mr_rodichenko',
            Email: 'mr_rodichenko@example.com',
            LastName: 'mr_rodichenko',
            FirstName: 'mr_rodichenko',
          },
          roles: [
            {
              id: 1,
              name: 'ROLE_ADMIN',
              predefined: true,
              userDefault: false,
              defaultProfileId: 1,
              createdDate: '2025-04-07 12:23:43.893',
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
              createdDate: '2025-04-07 12:23:43.894',
              mask: 15,
              owner: 'Unauthorized',
              locked: false,
              aclClass: 'ROLE',
            },
          ],
          groups: ['ROLE_ADMIN'],
        },
      ],
      status: 'OK',
    },
  }).as('getUsersInfo');
};
