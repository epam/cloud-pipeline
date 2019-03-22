# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import csv
import datetime
import getopt
import sys

from api_wrapper import PipelineRunAPI, UsersAPI
from date_utilities import parse_date_parameter, server_date_representation


def main(script_name, argv):

    try:
        opts, args = getopt.getopt(argv, "hs:a:k:o:n:", ["help", "since=", "api=", "key=", "output=", "skip-groups", "skip-ad-groups", "name-attribute="])
        now = datetime.datetime.now()
        since = parse_date_parameter(datetime.datetime(now.year, 1, 1, 0, 0, 0).strftime('%Y-%m-%d %H:%M:%S'))
        output = None
        skip_groups = False
        skip_ad = False
        name_attribute = 'Name'
        for opt, arg in opts:
            if opt in ("-h", "--help"):
                print script_name + ' -s <since date> -a <api path> -k <authentication token> -o <output file> [--skip-groups] [--skip-ad-groups] [-n <name-attribute>]'
                sys.exit()
            if opt in ("-s", "--since"):
                since = parse_date_parameter(arg)
            elif opt in ("-a", "--api"):
                api = arg
            elif opt in ("-k", "--key"):
                key = arg
            elif opt in ("-o", "--output"):
                output = arg
            elif opt == '--skip-groups':
                skip_groups = True
            elif opt == '--skip-ad-groups':
                skip_ad = True
            elif opt in ("-n", "--name"):
                name_attribute = arg
        if not api:
            print 'API path (-a <api path>) is required'
            print script_name + ' -s <since date> -a <api path> -k <authentication token> -o <output file> [--skip-groups] [--skip-ad-groups] [-n <name-attribute>]'
            sys.exit(2)
        if not key:
            print 'Authentication token (-k <authentication token>) is required'
            print script_name + ' -s <since date> -a <api path> -k <authentication token> -o <output file> [--skip-groups] [--skip-ad-groups] [-n <name-attribute>]'
            sys.exit(2)
        if not output:
            print 'Output file (-o <output file>) is required'
            print script_name + ' -s <since date> -a <api path> -k <authentication token> -o <output file> [--skip-groups] [--skip-ad-groups] [-n <name-attribute>]'
            sys.exit(2)

        pipeline_run_api = PipelineRunAPI(api, key)
        page_size = 500
        page = 0
        not_finished = True

        def extract_property(obj, *path):
            if len(list(path)) == 0:
                return ''
            property_name = list(path)[0]
            if property_name in obj:
                if len(list(path)) == 1:
                    return obj[property_name]
                else:
                    return extract_property(obj[property_name], *list(path)[1:])
            return ''
        users = {}
        user_names = {}
        ad_groups = []
        ad_groups_weights = {}
        roles = []
        if not skip_groups or not skip_ad:
            print 'Fetching user list...'
            users_api = UsersAPI(api, key)
            users_result = users_api.get_users()
            for user in users_result:
                users[user['userName'].upper()] = []
                user_name = user['email'] if 'email' in user else user['userName']
                if 'attributes' in user and name_attribute in user['attributes']:
                    user_name = user['attributes'][name_attribute]
                user_names[user['userName'].upper()] = user_name
                if not skip_ad and 'groups' in user:
                    for group in user['groups']:
                        if group not in ad_groups:
                            ad_groups.append(group)
                            ad_groups_weights[group] = 1
                        else:
                            ad_groups_weights[group] += 1
                        users[user['userName'].upper()].append(group.encode('utf8'))
                if not skip_groups and 'roles' in user:
                    for role in user['roles']:
                        if 'predefined' in role and not role['predefined'] and role['name'] not in roles:
                            roles.append(role['name'])
                        role_name = role['name']
                        if role_name.startswith('ROLE_'):
                            role_name = role_name[len('ROLE_'):]
                        users[user['userName'].upper()].append(role_name.encode('utf8'))
            ad_groups.sort(key=lambda g: ad_groups_weights[g], reverse=True)
            print 'Done.'
        print 'Generating csv...'
        columns = [
            'ID',
            'Parent Run',
            'Pipeline',
            'Version',
            'Started',
            'Finished',
            'Status',
            'Owner',
            'Node type',
            'Price type',
            'Disk',
            'IP',
            'Docker image'
        ]
        role_model_columns = []
        if not skip_groups:
            role_model_columns.extend(map(lambda r: r[len('ROLE_'):].encode('utf8') if r.startswith('ROLE_') else r.encode('utf8'), roles))
        if not skip_ad:
            role_model_columns.extend(map(lambda g: g.encode('utf8'), ad_groups))
        columns.extend(role_model_columns)
        with open(output, 'wb') as csv_file:
            csv_writer = csv.writer(csv_file, delimiter=',', quotechar='"', quoting=csv.QUOTE_MINIMAL)
            csv_writer.writerow(columns)
            while not_finished:
                page = page + 1
                result = pipeline_run_api.filter_runs(since, page, page_size)
                for element in result['elements']:
                    def extract_pipeline_name():
                        if 'pipelineName' in element:
                            return element['pipelineName']
                        elif 'dockerImage' in element:
                            return element['dockerImage'].split('/')[-1]
                        else:
                            return ''
                    owner = extract_property(element, 'owner').upper()
                    owner_email = user_names[owner] if owner in user_names else owner
                    data = [
                        extract_property(element, 'podId'),
                        extract_property(element, 'parentRunId'),
                        extract_pipeline_name().encode('utf8'),
                        extract_property(element, 'version').encode('utf8'),
                        server_date_representation(extract_property(element, 'startDate')),
                        server_date_representation(extract_property(element, 'endDate')),
                        extract_property(element, 'status'),
                        owner_email,
                        extract_property(element, 'instance', 'nodeType'),
                        'Spot' if extract_property(element, 'instance', 'spot') else 'On demand',
                        extract_property(element, 'instance', 'nodeDisk'),
                        extract_property(element, 'instance', 'nodeIP'),
                        extract_property(element, 'dockerImage')
                    ]
                    user_name = extract_property(element, 'owner').upper()
                    if user_name in users:
                        user = users[user_name]
                        for role in role_model_columns:
                            if role in user:
                                data.append('X')
                            else:
                                data.append('')
                            pass
                    csv_writer.writerow(data)
                total = result['totalCount']
                not_finished = page * page_size < total
                print '{} of {} items processed'.format(min(total, page * page_size), total)
        print 'Done.'

    except getopt.GetoptError:
        print script_name + ' -s <since date> -a <api path> -k <authentication token> -o <output file> [-n <name-attribute>]'
        sys.exit(2)


if __name__ == "__main__":
    main(sys.argv[0], sys.argv[1:])
