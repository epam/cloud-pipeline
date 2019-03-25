import os

from google.cloud.storage import Client
from google.oauth2.credentials import Credentials

from src.utilities.storage.common import AbstractRestoreManager, AbstractListingManager, StorageOperations, \
    AbstractDeleteManager, AbstractTransferManager


class GsManager:

    def __init__(self, client):
        self.client = client


class GsListingManager(GsManager, AbstractListingManager):

    def __init__(self, client, bucket):
        super(GsListingManager, self).__init__(client)
        self.bucket = bucket

    def list_items(self, relative_path=None, recursive=False, page_size=StorageOperations.DEFAULT_PAGE_SIZE,
                   show_all=False):
        # TODO 25.03.19: Method is not implemented yet.
        raise RuntimeError('Method is not implemented yet.')


class GsDeleteManager(GsManager, AbstractDeleteManager):

    def __init__(self, client, bucket):
        super(GsDeleteManager, self).__init__(client)
        self.bucket = bucket

    def delete_items(self, relative_path, recursive=False, exclude=[], include=[], version=None, hard_delete=False):
        # TODO 25.03.19: Method is not implemented yet.
        raise RuntimeError('Method is not implemented yet.')


class GsRestoreManager(GsManager, AbstractRestoreManager):

    def __init__(self, client, bucket):
        super(GsRestoreManager, self).__init__(client)
        self.bucket = bucket

    def restore_version(self, version):
        # TODO 25.03.19: Method is not implemented yet.
        raise RuntimeError('Method is not implemented yet.')


class TransferBetweenGsBucketsManager(GsManager, AbstractTransferManager):

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), skip_existing=False):
        # TODO 25.03.19: Method is not implemented yet.
        raise RuntimeError('Method is not implemented yet.')


class GsDownloadManager(GsManager, AbstractTransferManager):

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), skip_existing=False):
        # TODO 25.03.19: Method is not implemented yet.
        raise RuntimeError('Method is not implemented yet.')


class GsUploadManager(GsManager, AbstractTransferManager):

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), skip_existing=False):
        # TODO 25.03.19: Method is not implemented yet.
        raise RuntimeError('Method is not implemented yet.')


class TransferFromHttpOrFtpToGsManager(GsManager, AbstractTransferManager):

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), skip_existing=False):
        # TODO 25.03.19: Method is not implemented yet.
        raise RuntimeError('Method is not implemented yet.')


class RefreshingClient(Client):

    def __init__(self, bucket):
        # TODO 25.03.19: Determine what it is in the context of Google Cloud Platform
        project = bucket.path
        # TODO 25.03.19: Replace with refresh mechanism build over CloudPipeline API.
        credentials = Credentials(
            token=None,
            token_uri=os.getenv('GS_OAUTH_URI', 'https://accounts.google.com/o/oauth2/token'),
            refresh_token=os.environ['GS_OAUTH_REFRESH_TOKEN'],
            client_id=os.environ['GS_CLIENT_ID'],
            client_secret=os.environ['GS_CLIENT_SECRET']
        )
        super(RefreshingClient, self).__init__(project, credentials)


class GsBucketOperations:

    @classmethod
    def init_wrapper(cls, wrapper, versioning=False):
        # TODO 25.03.19: Method is not implemented yet.
        raise RuntimeError('Method is not implemented yet.')

    @classmethod
    def get_transfer_between_buckets_manager(cls, source_wrapper, destination_wrapper, command):
        return TransferBetweenGsBucketsManager(GsBucketOperations.get_client(destination_wrapper.bucket))

    @classmethod
    def get_download_manager(cls, source_wrapper, destination_wrapper, command):
        return GsDownloadManager(GsBucketOperations.get_client(source_wrapper.bucket))

    @classmethod
    def get_upload_manager(cls, source_wrapper, destination_wrapper, command):
        return GsUploadManager(GsBucketOperations.get_client(destination_wrapper.bucket))

    @classmethod
    def get_transfer_from_http_or_ftp_manager(cls, source_wrapper, destination_wrapper, command):
        return TransferFromHttpOrFtpToGsManager(GsBucketOperations.get_client(destination_wrapper.bucket))

    @classmethod
    def get_client(cls, bucket):
        return RefreshingClient(bucket)
