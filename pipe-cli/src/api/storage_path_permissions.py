from .base import API

class StoragePathPermissions(API):

    def __init__(self):
        super(StoragePathPermissions, self).__init__()

    @classmethod
    def get_user_permissions(cls, storage_id):
        api = cls.instance()
        response_data = api.call('/datastorage/%d/paths/permissions' % storage_id, None)
        return response_data.get('payload', [])
