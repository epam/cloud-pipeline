#!/bin/bash

# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import jwt
import flask
from flask import Flask, jsonify
import os

app = Flask(__name__)

def success(payload):
    return jsonify({
        "payload": payload,
        "status": "OK"
    })


def error(message):
    return jsonify({
        "message": message,
        "status": "ERROR"
    })

def get_path(username, path):
    return os.path.join(os.getenv("CP_DAV_SERVE_DIR", "/dav-serve"), username, path)

def get_and_normalize_pub_key():
    read_pub_key = open(
                  os.path.join(os.getenv("CP_API_SRV_CERT_DIR", "/opt/api/pki"), "jwt.key.public")
    ).read().strip()
    if "BEGIN PUBLIC KEY" not in read_pub_key:
        return "-----BEGIN PUBLIC KEY-----\n" \
               + read_pub_key \
               + "\n-----END PUBLIC KEY-----"
    else:
        return read_pub_key

def verify_token():
    token = flask.request.cookies.get('bearer', None)
    if not token:
        auth_header = flask.request.headers.get('Authorization', None)

    if not token:
        raise RuntimeError('No cookie or authorization header is set')
    
    token = token.replace("Bearer ", "")
    payload = jwt.decode(
                token,
                get_and_normalize_pub_key(),
                algorithms=["RS512"]
            )
    
    sub = payload['sub'].upper() if 'sub' in payload else None
    if not sub:
        raise RuntimeError('No user name is set in the token')

    user_id = int(payload['user_id']) if 'user_id' in payload else None
    if not user_id:
        raise RuntimeError('No user id is set in the token')
    
    uid_seed = int(os.getenv('CP_CAP_UID_SEED', 70000))

    return user_id, sub, uid_seed + user_id

@app.route('/chown/', methods=['POST'])
def chown():
    try:
        user_id, sub, uid = verify_token()
        flask_json = flask.request.json

        if not flask_json:
            return error('Request is empty')

        if not 'path' in flask_json:
            return error('Path is not provided')
        path = flask_json['path']

        paths_list = []
        if isinstance(path, list):
            paths_list = path
        else:
            paths_list.append(path)
        
        if len(paths_list) == 0:
            return error('Paths length is 0')

        errors_list = []
        for path_item in paths_list:
            full_path = get_path(sub, path_item)
            if not os.path.exists(full_path):
                errors_list.append('Path {} does not exist'.format(full_path))
                continue

            gid = int(os.getenv('CP_DAV_MOUNT_DEFAULT_OWNER_GROUP', uid))
            try:
                os.chown(full_path, uid, gid)
            except Exception as file_e:
                errors_list.append('Path {} chown errored: {}'.format(full_path, file_e.__str__()))

        if len(errors_list) == 0:
            return success("{}:{} is set for:\n{}".format(uid, gid, '\n'.join(paths_list)))
        else:
            return error('\n'.join(errors_list))

    except Exception as e:
        return error(e.__str__())


if __name__ == '__main__':
    dav_extra_port = int(os.getenv("CP_DAV_EXTRA_INTERNAL_PORT", 31086))
    app.run(host='0.0.0.0', port=dav_extra_port)