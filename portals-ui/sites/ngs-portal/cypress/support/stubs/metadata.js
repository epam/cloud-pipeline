const API_BASE = Cypress.env('API_BASE');

export const stubMetadataLoad = () => {
  cy.intercept('POST', `${API_BASE}/metadata/load`, {
    statusCode: 200,
    body: {
      payload: [
        {
          entity: {
            entityId: 1,
            entityClass: 'PIPELINE_USER',
          },
          data: {
            'ui.notifications.mute': {
              type: 'string',
              value: '{"muted":true,"displayAfter":"2024-07-25 14:19:56"}',
            },
            fs_notifications: {
              type: 'string',
              value: '1sdf',
            },
            'ui.ssh.theme': {
              type: 'string',
              value: 'light',
            },
            confirmed_notifications: {
              type: 'json',
              value:
                '[{"notificationId":360,"title":"Unblind request","body":"**Author**: USER3  \\n**Request**: unblind the file \'Revision-storage/Folder1/SimpleFile.txt\'  \\n**Reason**: this file is very important for my work, unblind please!","user":"PIPE_ADMIN","date":"2021-10-12 08:19:01.760"},{"notificationId":360,"title":"Unblind request","body":"**Author**: USER3  \\n**Request**: unblind the file \'Revision-storage/Folder1/SimpleFile.txt\'  \\n**Reason**: this file is very important for my work, unblind please!","user":"PIPE_ADMIN","date":"2021-10-12 08:32:10.142"},{"notificationId":202,"title":"11111","user":"PIPE_ADMIN","date":"2021-11-07 06:42:09.620"},{"notificationId":202,"title":"11111","user":"PIPE_ADMIN","date":"2021-11-07 06:43:50.917"},{"notificationId":202,"title":"11111","user":"PIPE_ADMIN","date":"2021-11-07 06:48:17.559"},{"notificationId":360,"title":"Unblind request","body":"**Author**: USER3  \\n**Request**: unblind the file \'Revision-storage/Folder1/SimpleFile.txt\'  \\n**Reason**: this file is very important for my work, unblind please!","user":"PIPE_ADMIN","date":"2021-11-19 17:52:22.868"},{"notificationId":360,"title":"Unblind request","body":"**Author**: USER3  \\n**Request**: unblind the file \'Revision-storage/Folder1/SimpleFile.txt\'  \\n**Reason**: this file is very important for my work, unblind please!","user":"PIPE_ADMIN","date":"2021-11-19 17:53:40.910"},{"notificationId":245,"title":"Important message","body":"That\'s **it**!","user":"PIPE_ADMIN","date":"2021-11-25 14:02:57.383"},{"notificationId":245,"title":"Important message","body":"That\'s **it**!","user":"PIPE_ADMIN","date":"2021-11-25 14:02:59.870"}]',
            },
            CP_CAP_RUN_CAPABILITIES: {
              type: 'string',
            },
          },
        },
      ],
      status: 'OK',
    },
  }).as('getMetadata');
};
