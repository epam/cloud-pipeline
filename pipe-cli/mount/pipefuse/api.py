import json

import requests


class TemporaryCredentials:

    def __init__(self):
        self.access_key_id = None
        self.secret_key = None
        self.session_token = None
        self.expiration = None
        self.region = None

    @classmethod
    def load(cls, json):
        instance = cls()
        instance.access_key_id = json['keyID'] if 'keyID' in json else None
        instance.secret_key = json['accessKey']
        instance.session_token = json['token']
        instance.expiration = json['expiration']
        instance.region = json['region'] if 'region' in json else None
        return instance


class DataStorage:
    def __init__(self):
        self.id = None

    @classmethod
    def load(cls, json):
        instance = DataStorage()
        instance.id = json['id']
        return instance


class CloudPipelineClient:

    def __init__(self, api, token):
        self._api = api.strip('/')
        self._token = token
        self.__headers__ = {'Content-Type': 'application/json',
                            'Authorization': 'Bearer {}'.format(self._token)}

    def get_storage(self, name):
        response_data = self._get('datastorage/find?id={}'.format(name))
        if 'payload' in response_data:
            return DataStorage.load(response_data['payload'])
        return None

    def get_temporary_credentials(self, bucket_id, read=True, write=True, versioning=False):
        operation = {
            'id': bucket_id,
            'read': read,
            'write': write
        }
        if versioning:
            operation['readVersion'] = read
            operation['writeVersion'] = write
        credentials = self._get_temporary_credentials([operation])
        return credentials

    def _get_temporary_credentials(self, data):
        response_data = self._post('datastorage/tempCredentials/', data=json.dumps(data))
        if 'payload' in response_data:
            return TemporaryCredentials.load(response_data['payload'])
        elif 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError('Failed to load credentials from server.')

    def _get(self, method, *args, **kwargs):
        return self._call(method, http_method='get', *args, **kwargs)

    def _post(self, method, *args, **kwargs):
        return self._call(method, http_method='post', *args, **kwargs)

    def _call(self, method, http_method, data=None, error_message=None):
        url = '{}/{}'.format(self._api, method)
        if http_method == 'get':
            response = requests.get(url, headers=self.__headers__, verify=False)
        else:
            response = requests.post(url, data=data, headers=self.__headers__, verify=False)
        response_data = json.loads(response.text)
        message_text = error_message if error_message else 'Failed to fetch data from server'
        if 'status' not in response_data:
            raise RuntimeError('{}. Server responded with status: {}.'
                               .format(message_text, str(response_data.status_code)))
        if response_data['status'] != 'OK':
            raise RuntimeError('{}. Server responded with message: {}'.format(message_text, response_data['message']))
        else:
            return response_data
