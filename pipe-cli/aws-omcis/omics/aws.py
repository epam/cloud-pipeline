from boto3 import Session
from botocore.config import Config
from botocore.credentials import RefreshableCredentials, Credentials
from botocore.session import get_session
from .cloud_pipeline_api import OmicsStoreType


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
    def from_aws_omics_ref_response(cls, response):
        file = cls._with_common_metadata_fields(response)
        file.type = "REFERENCE"
        file.size = cls._get_ref_size(file)
        return file

    @classmethod
    def from_aws_omics_seq_response(cls, response):
        file = cls._with_common_metadata_fields(response)
        file.type = response.get("fileType", None)
        file.size = cls._get_seq_size(response.get("sequenceInformation", {}))
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
        file.files = response.get("files", [])
        return file

    @classmethod
    def _get_ref_size(cls, file):
        size = 0
        if 'index' in file.files:
            size += file.files.get("index", {}).get("contentLength", 0)
        if 'source' in file.files:
            size += file.files("source", {}).get("contentLength", 0)
        return size

    @classmethod
    def _get_seq_size(cls, sequence_info):
        if 'totalBaseCount' in sequence_info:
            return sequence_info["totalBaseCount"]
        return 0


class AWSOmicsOperation:

    def __init__(self, api):
        self.api = api

    def get_file_metadata(self, storage, file_id):
        omics = self.get_omics(storage, storage.region_name, read=True)
        match storage.type:
            case OmicsStoreType.OMICS_REF:
                response = omics.get_reference_metadata(id=file_id, referenceStoreId=storage.cloud_store_id)
                return AWSOmicsFile.from_aws_omics_ref_response(response)
            case OmicsStoreType.OMICS_SEQ:
                response = omics.get_read_set_metadata(id=file_id, sequenceStoreId=storage.cloud_store_id)
                return AWSOmicsFile.from_aws_omics_seq_response(response)

    def list_files(self, storage, token, page_size, show_all):

        # Helper method to get one page with AWS SDK
        def _get_page(store_id, aws_omics_method, object_mapping, token, page_size):
            req_kwars = {'sequenceStoreId': store_id}
            if token is not None:
                req_kwars["nextToken"] = token
            if page_size is not None and page_size > 0:
                req_kwars["maxResults"] = page_size
            try:
                response = aws_omics_method(**req_kwars)
                return response.get("nextToken", None), [object_mapping(e) for e in response["readSets"]]
            except Exception as e:
                raise RuntimeError("Something went wrong during the request to AWS.", e)

        # Based on storage type choose appropriate SKD method and object mapping
        omics = self.get_omics(storage, storage.region_name, list=True)
        match storage.type:
            case OmicsStoreType.OMICS_REF:
                aws_omics_method = omics.list_references
                object_mapping = AWSOmicsFile.from_aws_omics_ref_response
            case OmicsStoreType.OMICS_SEQ:
                aws_omics_method = omics.list_read_sets
                object_mapping = AWSOmicsFile.from_aws_omics_seq_response
            case _:
                raise RuntimeError("Unexpected storage type: " + storage.type)

        # Perform listing
        result = []
        if show_all:
            next_token, page = _get_page(storage.cloud_store_id, aws_omics_method, object_mapping, token, page_size)
            result.extend(page)
            while next_token is not None:
                next_token, page = _get_page(storage.cloud_store_id, aws_omics_method, object_mapping, next_token, page_size)
                result.extend(page)
        else:
            _, result = _get_page(storage.cloud_store_id, aws_omics_method, object_mapping, token, page_size)
        return result

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
