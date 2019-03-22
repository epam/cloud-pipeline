class WrapperType(object):
    LOCAL = 'LOCAL'
    S3 = 'S3'
    AZURE = 'AZ'
    FTP = 'FTP'
    HTTP = 'HTTP'

    __cloud_types = [S3, AZURE]
    __dynamic_cloud_scheme = 'cp'
    __s3_cloud_scheme = 's3'
    __azure_cloud_scheme = 'az'
    __cloud_schemes = [__dynamic_cloud_scheme, __s3_cloud_scheme, __azure_cloud_scheme]
    __cloud_schemes_map = {S3: __s3_cloud_scheme, AZURE: __azure_cloud_scheme}

    @classmethod
    def cloud_types(cls):
        return WrapperType.__cloud_types

    @classmethod
    def cloud_schemes(cls):
        return WrapperType.__cloud_schemes

    @classmethod
    def is_dynamic_cloud_scheme(cls, scheme):
        return scheme == WrapperType.__dynamic_cloud_scheme

    @classmethod
    def cloud_scheme(cls, type):
        if type in WrapperType.__cloud_schemes_map:
            return WrapperType.__cloud_schemes_map[type]
        else:
            raise RuntimeError('Storage provider %s is not in the list of supported cloud providers %s'
                               % (type, WrapperType.cloud_types()))
