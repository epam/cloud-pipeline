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

import argparse
import os
import traceback

import flask
from flask import Flask, jsonify, send_from_directory
from flask_httpauth import HTTPBasicAuth

from fsbrowser.src.fs_browser_manager import FsBrowserManager
from fsbrowser.src.logger import BrowserLogger

app = Flask(__name__)
# Force FLASK to accept both "http://url and http://url/"
app.url_map.strict_slashes = False

auth = HTTPBasicAuth()


def success(payload):
    return {
        "payload": payload,
        "status": "OK"
    }


def error(message):
    return {
        "message": message,
        "status": "ERROR"
    }


@auth.verify_password
def verify_password(username, password):
    return password == os.getenv('SSH_PASS')


@app.route('/')
@auth.login_required
def root():
    return app.send_static_file('index.html')


@app.route('/<path:path>')
@auth.login_required
def static_files(path):
    return app.send_static_file(path)


@app.route('/view')
@app.route('/view/')
@auth.login_required
def view_root():
    return view("")


@app.route('/view/<path:path>')
@auth.login_required
def view_path(path):
    return view(path)


def view(path):
    manager = app.config['fsbrowser']
    try:
        items = manager.list(path)
        return jsonify(success(items))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/download/<path:path>')
@auth.login_required
def download(path):
    manager = app.config['fsbrowser']
    try:
        task_id = manager.run_download(path)
        return jsonify(success({"task": task_id}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/uploadUrl/<path:path>')
@auth.login_required
def get_upload_url(path):
    manager = app.config['fsbrowser']
    try:
        task_id, upload_url = manager.init_upload(path)
        return jsonify(success({"task": task_id, "url": upload_url}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/upload/<task_id>')
@auth.login_required
def upload(task_id):
    manager = app.config['fsbrowser']
    try:
        task_id = manager.run_upload(task_id)
        return jsonify(success({"task": task_id}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/status/<task_id>')
@auth.login_required
def get_task_status(task_id):
    manager = app.config['fsbrowser']
    try:
        return jsonify(success(manager.get_task_status(task_id)))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/cancel/<task_id>')
@auth.login_required
def cancel_task(task_id):
    manager = app.config['fsbrowser']
    try:
        return jsonify(success(manager.cancel(task_id)))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/delete/<path:path>')
@auth.login_required
def delete(path):
    manager = app.config['fsbrowser']
    try:
        return jsonify(success({"path": manager.delete(path)}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/clone', methods=['POST'])
@auth.login_required
def clone_versioned_storage(vs_id):
    revision = flask.request.args.get("revision", None)
    manager = app.config['fsbrowser']
    try:
        task_id = manager.git_clone(vs_id, revision)
        return jsonify(success({"task": task_id}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/detached')
@auth.login_required
def is_versioned_storage_detached(vs_id):
    manager = app.config['fsbrowser']
    try:
        is_head_detached = manager.is_head_detached(vs_id)
        return jsonify(success({"detached": is_head_detached}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/list')
@auth.login_required
def list_versioned_storages():
    manager = app.config['fsbrowser']
    try:
        items = manager.list_version_storages()
        return jsonify(success(items))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/fetch', methods=['POST'])
@auth.login_required
def fetch_versioned_storage(vs_id):
    manager = app.config['fsbrowser']
    try:
        task_id = manager.git_pull(vs_id)
        return jsonify(success({"task": task_id}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/diff')
@auth.login_required
def diff_versioned_storage(vs_id):
    manager = app.config['fsbrowser']
    try:
        items = manager.git_status(vs_id)
        return jsonify(success(items))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/diff/files')
@auth.login_required
def diff_versioned_storage_file(vs_id):
    file_path = flask.request.args.get("path")
    manager = app.config['fsbrowser']
    try:
        items = manager.git_diff(vs_id, file_path)
        return jsonify(success(items))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/commit', methods=['POST'])
@auth.login_required
def commit_versioned_storage(vs_id):
    message = flask.request.args.get("message")
    files_to_add = flask.request.args.get("files", None)
    manager = app.config['fsbrowser']
    try:
        task_id = manager.git_push(vs_id, message, files_to_add)
        return jsonify(success({"task": task_id}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/files', methods=['POST'])
@auth.login_required
def save_versioned_storage_file(vs_id):
    path = flask.request.args.get('path')
    manager = app.config['fsbrowser']
    try:
        content = flask.request.stream.read()
        task_id = manager.save_file(vs_id, path, content)
        return jsonify(success({"task": task_id}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


def str_to_bool(input_value):
    return input_value.lower() in ("true", "t")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default="5000")
    parser.add_argument("--working_directory", required=True)
    parser.add_argument("--vs_working_directory", required=True)
    parser.add_argument("--transfer_storage", required=True)
    parser.add_argument("--process_count", default=2)
    parser.add_argument("--run_id", required=False)
    parser.add_argument("--log_dir", required=False)
    parser.add_argument("--exclude", default="/bin,/var,/root,/sbin,/home,/sys,/usr,/boot,/dev,/lib,/proc")
    parser.add_argument("--follow_symlinks", default="True", type=str_to_bool)
    parser.add_argument("--tmp_directory", default="/tmp")

    # TODO: should we move pool and add another manager?
    args = parser.parse_args()
    app.config['fsbrowser'] = FsBrowserManager(args.working_directory, args.process_count,
                                               BrowserLogger(args.run_id, args.log_dir), args.transfer_storage,
                                               args.follow_symlinks, args.tmp_directory, args.exclude,
                                               args.vs_working_directory)

    app.run(host=args.host, port=args.port)


if __name__ == '__main__':
    main()
