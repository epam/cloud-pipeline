# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# touch all root tree entities
# touch docker registry
# TODO: touch runs starting from date

from pipeline import PipelineAPI
import os
import json

ROLE = 'SEARCH_UPDATE'


def touch_item(api, id, acl_class):
    print('Processing %s [%s]' % (str(id), acl_class))
    permissions = {'id': id,
                   'aclClass': acl_class,
                   'mask': 0,
                   'principal': False,
                   'userName': ROLE}
    try:
        api.execute_request(str(api.api_url) + '/grant', method='post', data=json.dumps(permissions))
        api.execute_request(str(api.api_url) + '/grant?id={id}&aclClass={aclClass}&user={userName}&isPrincipal=false'
                            .format(**permissions), method='delete')
    except BaseException as e:
        print(str(e.message))


def run():
    api = PipelineAPI(os.environ['API'], 'logs')
    result = api.execute_request(str(api.api_url) + '/folder/loadTree', method='get')
    children = ['pipelines', 'childFolders', 'storages', 'configurations']
    for child_type in children:
        if child_type in result:
            for item in result[child_type]:
                touch_item(api, item['id'], item['aclClass'])
    registries = api.docker_registry_load_all()
    for registry in registries:
        touch_item(api, registry['id'], registry['aclClass'])


if __name__ == '__main__':
    run()
