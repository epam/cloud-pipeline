from boto3 import Session
from botocore.config import Config
from botocore.credentials import RefreshableCredentials, Credentials
from botocore.session import get_session
from .cpapi import OmicsStoreType


class AWSOmicsFile:

    def __init__(self):
        self.name = None
        self.id = None
        self.type = None
        self.status = None
        self.description = None
        self.size = None
        self.modified = None
        self.raw = None
        self.files = []

    @classmethod
    def from_omics_ref_metadata_response(cls, response):
        file = cls._with_common_metadata_fields(response)
        file.size = cls._get_ref_size(file)
        return file

    @classmethod
    def from_omics_seq_metadata_response(cls, response):
        file = cls._with_common_metadata_fields(response)
        file.size = cls._get_seq_size(response["sequenceInformation"])
        return file

    @classmethod
    def _with_common_metadata_fields(cls, response):
        file = AWSOmicsFile()
        file.name = response["name"]
        file.id = response["id"]
        file.status = response["status"]
        file.description = response["description"]
        file.modified = response.get("updateTime", response["creationTime"])
        file.raw = response
        file.files = response["files"]
        return file

    @classmethod
    def _get_ref_size(cls, file):
        size = 0
        if 'index' in file.files:
            size += file.files["index"].get("contentLength", 0)
        if 'source' in file.files:
            size += file.files["source"].get("contentLength", 0)
        return size

    @classmethod
    def _get_seq_size(cls, sequence_info):
        if 'totalBaseCount' in sequence_info:
            return sequence_info["totalBaseCount"]
        return 0


class AWSOmicsOperation:

    def __init__(self, api):
        self.api = api

    def get_file_metadata(self, storage, id):
        omics = self.get_omics(storage, storage.region_name, read=True)
        match storage.type:
            case OmicsStoreType.OMICS_REF:
                return self._get_reference_metadata(omics, storage.cloud_store_id, id)
            case OmicsStoreType.OMICS_SEQ:
                return self._get_readset_metadata(omics, storage.cloud_store_id, id)

    def list_files(self, storage, token, page_size):
        omics = self.get_omics(storage, storage.region_name, list=True)
        match storage.type:
            case OmicsStoreType.OMICS_REF:
                return self._list_references(omics, storage.cloud_store_id, token, page_size)
            case OmicsStoreType.OMICS_SEQ:
                return self._list_readsets(omics, storage.cloud_store_id, token, page_size)

    def _get_reference_metadata(self, omics, reference_store_id, reference_id):
        response = omics.get_reference_metadata(id=reference_id, referenceStoreId=reference_store_id)
        return AWSOmicsFile.from_omics_ref_metadata_response(response)

    def _list_references(self, omics, reference_store_id, token, page_size):
        pass

    def _get_readset_metadata(self, omics, sequence_store_id, readset_id):
        response = omics.get_reference_metadata(id=readset_id, referenceStoreId=sequence_store_id)
        return AWSOmicsFile.from_omics_seq_metadata_response(response)

    def _list_readsets(self, omics, sequence_store_id, token, page_size):
        pass

    def get_omics(self, storage, region, list=False, read=False, write=False):
        return self.__assumed_session(storage, list, read, write).client('omics', config=Config(), region_name=region)

    def __assumed_session(self, storage, list, read, write):
        def __refresh():
            credentials = self.api.get_temporary_credentials(storage, list, read, write)
            return dict(
                access_key=credentials.access_key_id,
                secret_key=credentials.secret_key,
                token=credentials.session_token,
                expiry_time=credentials.expiration)

        fresh_metadata = __refresh()
        if 'token' not in fresh_metadata or not fresh_metadata['token']:
            session_credentials = Credentials(fresh_metadata['access_key'], fresh_metadata['secret_key'])
        else:
            session_credentials = RefreshableCredentials.create_from_metadata(
                metadata=fresh_metadata,
                refresh_using=__refresh,
                method='sts-assume-role')

        s = get_session()
        s._credentials = session_credentials
        return Session(botocore_session=s)
