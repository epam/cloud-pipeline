import os

import boto3
from azure.storage.blob import BlockBlobService
from google.cloud.storage import Client
from tzlocal import get_localzone

from buckets.utils.file_utils import file_last_modified_time

_object_infos = {
    'S3': lambda *args: S3ObjectInfo(False).build(*args),
    'AZ': lambda *args: AzureObjectInfo(False).build(*args),
    'GS': lambda *args: GsObjectInfo(False).build(*args)
}


class ObjectInfo(object):

    def __init__(self, is_local):
        self.is_local = is_local
        self.exists = None
        self.size = None
        self.last_modified = None
        self.path = None

    def build(self, *args):
        if self.is_local:
            return LocalFileInfo(self.is_local).build(args[0])
        else:
            provider = os.environ['CP_PROVIDER']
            if provider in _object_infos:
                return _object_infos[provider](*args)
            else:
                raise RuntimeError('Provider must be one of %s' % _object_infos.keys())


class S3ObjectInfo(ObjectInfo):

    def __init__(self, is_local):
        super(S3ObjectInfo, self).__init__(is_local)

    def build(self, bucket_name, key):
        self.path = "cp://" + os.path.join(bucket_name, key)
        s3 = boto3.resource('s3')
        bucket = s3.Bucket(bucket_name)
        bucket_entry = list(bucket.objects.filter(Prefix=key))
        if len(bucket_entry) > 0 and bucket_entry[0].key == key:
            self.exists = True
        else:
            self.exists = False
            return self
        obj = s3.Object(bucket_name, key)
        self.size = obj.content_length
        self.last_modified = obj.last_modified.astimezone(get_localzone()).replace(tzinfo=None)
        return self


class AzureObjectInfo(ObjectInfo):

    def __init__(self, is_local):
        super(AzureObjectInfo, self).__init__(is_local)

    def build(self, bucket_name, key):
        self.path = "cp://" + os.path.join(bucket_name, key)
        service = BlockBlobService(account_name=os.environ['AZURE_STORAGE_ACCOUNT'],
                                   account_key=os.environ['AZURE_ACCOUNT_KEY'])
        bucket_entry = list(service.list_blobs(bucket_name, key))
        if len(bucket_entry) > 0 and bucket_entry[0].name == key:
            self.exists = True
        else:
            self.exists = False
            return self
        self.size = bucket_entry[0].properties.content_length
        self.last_modified = bucket_entry[0].properties.last_modified.astimezone(
            get_localzone()).replace(tzinfo=None)
        return self


class GsObjectInfo(ObjectInfo):

    def __init__(self, is_local):
        super(GsObjectInfo, self).__init__(is_local)

    def build(self, bucket_name, key):
        self.path = "cp://" + os.path.join(bucket_name, key)
        client = Client()
        bucket_entry = list(client.bucket(bucket_name).list_blobs(prefix=key))
        if len(bucket_entry) > 0 and bucket_entry[0].name == key:
            self.exists = True
        else:
            self.exists = False
            return self
        self.size = bucket_entry[0].size
        self.last_modified = bucket_entry[0].updated.astimezone(get_localzone()).replace(
            tzinfo=None)
        return self


class LocalFileInfo(ObjectInfo):

    def __init__(self, is_local):
        super(LocalFileInfo, self).__init__(is_local)

    def build(self, path):
        self.path = os.path.abspath(path)
        self.exists = os.path.exists(self.path)
        if not self.exists:
            return self
        self.size = os.path.getsize(self.path)
        self.last_modified = file_last_modified_time(self.path)
        return self
