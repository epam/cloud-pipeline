import cloudPipelineApi from '../cloud-pipeline-api.ts';

export type UserNotificationsPage = {
  elements?: unknown[];
  totalCount: number;
};

export type UserNotificationsQuery = {
  pageNum?: number;
  pageSize?: number;
  isRead?: boolean;
};

export async function loadUserNotifications(
  query: UserNotificationsQuery = {},
): Promise<UserNotificationsPage> {
  return cloudPipelineApi.jsonGet<UserNotificationsPage>({
    uri: 'user-notification/message/my',
    query: {
      pageNum: 0,
      pageSize: 20,
      isRead: false,
      ...query,
    },
  });
}
