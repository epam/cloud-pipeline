# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import sys
import traceback
from multiprocessing.pool import ThreadPool

import flask
from flask import Flask, jsonify, send_from_directory, stream_with_context, Response
from flask_httpauth import HTTPBasicAuth
from flasgger import Swagger

from fsbrowser.src.fs_browser_manager import FsBrowserManager
from fsbrowser.src.git.git_manager import GitManager
from fsbrowser.src.logger import BrowserLogger

if getattr(sys, 'frozen', False):
    static_folder = os.path.join(sys._MEIPASS, 'fsbrowser', 'static')
    templates_folder = os.path.join(sys._MEIPASS, 'fsbrowser', 'template')
    app = Flask(__name__, static_folder=static_folder, template_folder=templates_folder)
else:
    app = Flask(__name__)


sys.path.append(os.getcwd())
swagger = Swagger(app)


# Force FLASK to accept both "http://url and http://url/"
app.url_map.strict_slashes = False

auth = HTTPBasicAuth()


def get_file_stream(path_to_file):
    with open(path_to_file, "rb") as f:
        yield f.read()


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
    """
    Lists all data from `working_directory`.
    ---
    definitions:
      Items:
        type: array
        items:
          $ref: '#/definitions/Item'
      Item:
        type: object
        properties:
          name:
            type: string
          path:
            type: string
          type:
            type: string
          size:
            type: integer
    responses:
      200:
        schema:
          $ref: '#/definitions/Items'
    """
    return view("")


@app.route('/view/<path:path>')
@auth.login_required
def view_path(path):
    """
    Lists files via specified path.
    `path` - the path to file/folder relative to `working_directory`.
    The `working_directory` is the path to directory that will be shared for user. This is the required parameter
    that should be specified when application is started.
    ---
    parameters:
      - name: path
        in: path
        type: string
        required: true
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
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
    """
    Transfers file/folder via specified path on common object storage.
    If the user initiate to download the folder the `.tar.gz`  file will be provided for user. This action is
    long running so launches asynchronously.  The result of this request is the `task_id` that should be used for
    task status monitoring. When the download operation is completed a new folder with unique name `task_id`
    will be created on common object storage. The result of the whole download operation is the sign url to the file
    in `task_id` bucket's folder (e.g. sing url will be generated for object that located with
    s3://CP_CAP_EXPOSE_FS_STORAGE/3fcecb8986a34c54939dbaf8c4a2238b/data.tar.gz).
    ---
    parameters:
      - name: path
        in: path
        type: string
        required: true
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
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
    """
    Returns presigned url.
    Returns url to upload file to storage by path cp://CP_CAP_EXPOSE_FS_STORAGE/<task_id>/<path>
    where `task_id` - random generated string, `path` - file location on compute node.
    ---
    parameters:
      - name: path
        in: path
        type: string
        required: true
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
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
    """
    Transfers uploaded file from bucket to compute node.
    If the current task is still in progress but was canceled the created file will be removed.
    ---
    parameters:
      - name: task_id
        in: path
        type: string
        required: true
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
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
    """
    Returns specified task status.
    Allowed states:  'pending', 'success', 'running', 'failure' or 'canceled'. If the task is finished with
    status 'failure' an error message will be returned. If the task is completed successfully the task result
    will be returned.
    ---
    parameters:
      - name: task_id
        in: path
        type: string
        required: true
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    manager = app.config['fsbrowser']
    try:
        return jsonify(success(manager.get_task_status(task_id)))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/cancel/<task_id>')
@auth.login_required
def cancel_task(task_id):
    """
    Cancels task computation. Cleanups data if needed.
    ---
    parameters:
      - name: task_id
        in: path
        type: string
        required: true
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    manager = app.config['fsbrowser']
    try:
        return jsonify(success(manager.cancel(task_id)))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/delete/<path:path>')
@auth.login_required
def delete(path):
    """
    Removes file/folder from compute node.
    ---
    parameters:
      - name: path
        in: path
        type: string
        required: true
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    manager = app.config['fsbrowser']
    try:
        return jsonify(success({"path": manager.delete(path)}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/clone', methods=['POST'])
@auth.login_required
def clone_versioned_storage(vs_id):
    """
    Clones versioned storage specified by `ID`.
    Optionally supports revision. If revision was specified a `READ ONLY` regime will be enabled. To switch
    `READ ONLY` regime fetch data from server. This operation returns task ID since may take a long time.
    Use `status/<task_id>` method to check result.
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
      - name: revision
        in: query
        type: string
        required: false
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    revision = flask.request.args.get("revision", None)
    manager = app.config['git_manager']
    try:
        task_id = manager.clone(vs_id, revision)
        return jsonify(success({"task": task_id}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/detached')
@auth.login_required
def is_versioned_storage_detached(vs_id):
    """
    Checks if regime `READ ONLY` was specified (in this situation `HEAD` is detached).
    If this check returns `true` the `commit` operation shall not be available.
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
    definitions:
      Detached:
        type: object
        properties:
          detached:
            type: boolean
    responses:
      200:
        schema:
          $ref: '#/definitions/Detached'
    """
    manager = app.config['git_manager']
    try:
        is_head_detached = manager.is_head_detached(vs_id)
        return jsonify(success({"detached": is_head_detached}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/list')
@auth.login_required
def list_versioned_storages():
    """
    Loads all versioned storages specified for current run
    ---
    definitions:
      VSList:
        type: array
        items:
          $ref: '#/definitions/VS'
      VS:
        type: object
        properties:
          name:
            type: string
          path:
            type: string
          revision:
            type: string
          id:
            type: integer
          detached:
            type: boolean
    responses:
      200:
        schema:
          $ref: '#/definitions/VSList'
    """
    manager = app.config['git_manager']
    try:
        items = manager.list()
        return jsonify(success(items))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/fetch', methods=['POST'])
@auth.login_required
def fetch_versioned_storage(vs_id):
    """
    Refreshes repository.
    If head detached reverts all local changes. If head not detached and conflicts were detected an error with
    conflicted files will be returned. This operation returns task ID since may take a long time.
    Use `status/<task_id>` method to check result.
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    manager = app.config['git_manager']
    try:
        task_id = manager.pull(vs_id)
        return jsonify(success({"task": task_id}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/status', methods=['GET'])
@auth.login_required
def status_versioned_storage(vs_id):
    """
    Loads repository current status: changed files, merge state and unsaved changes existence
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
    definitions:
      Diff:
        type: object
        properties:
          status:
            type: string
          path:
            type: string
      RepoStatus:
        type: object
        properties:
          merge_in_progress:
            type: boolean
          unsaved:
            type: boolean
          files:
            type: array
            items:
              $ref: '#/definitions/Diff'
    responses:
      200:
        schema:
          $ref: '#/definitions/RepoStatus'
    """
    manager = app.config['git_manager']
    try:
        items = manager.status(vs_id)
        return jsonify(success(items))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/diff/files')
@auth.login_required
def diff_versioned_storage_file(vs_id):
    """
    Loads diff for specified file.
    If `raw` request parameter specified this method returns `git diff` output. Specify `lines_count` to adjust
    additional lines count into the output (default: 3).
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
      - name: path
        in: query
        type: string
        required: true
      - name: raw
        in: query
        type: boolean
        required: false
        default: false
      - name: lines_count
        in: query
        type: integer
        required: false
        default: 3
    definitions:
      DiffList:
        type: array
        items:
          $ref: '#/definitions/Diff'
      Diff:
        type: object
        properties:
          new_name:
            type: string
          old_name:
            type: string
          binary:
            type: boolean
          new_size:
            type: integer
          old_size:
            type: integer
          raw_output:
            type: string
          insertions:
            type: integer
          deletions:
            type: integer
          lines:
            type: array
            items:
              $ref: '#/definitions/Line'
      Line:
        type: object
        properties:
          content:
            type: string
          content_offset:
            type: integer
          new_lineno:
            type: integer
          num_lines:
            type: integer
          old_lineno:
            type: integer
          origin:
            type: string
    responses:
      200:
        schema:
          $ref: '#/definitions/DiffList'
    """
    file_path = flask.request.args.get("path")
    show_raw = flask.request.args.get("raw")
    show_raw_flag = False if not show_raw else str_to_bool(show_raw)
    lines_count = flask.request.args.get("lines_count", 3)
    manager = app.config['git_manager']
    try:
        items = manager.diff(vs_id, file_path, show_raw_flag, int(lines_count))
        return jsonify(success(items))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/commit', methods=['POST'])
@auth.login_required
def commit_versioned_storage(vs_id):
    """
    Saves local changes to remote: fetches repo, adds changed files, commits changes and pushes to remote.
    This operation returns task ID since may take a long time. Use `status/<task_id>` method to check result.
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
      - name: message
        in: query
        type: string
        required: true
      - name: files
        in: query
        type: array
        items:
            type: string
        required: false
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    message = flask.request.args.get("message")
    files_to_add = flask.request.args.get("files", None)
    manager = app.config['git_manager']
    token = flask.request.headers.get('token', None)
    try:
        task_id = manager.push(vs_id, message, files_to_add, token)
        return jsonify(success({"task": task_id}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/files', methods=['POST'])
@auth.login_required
def save_versioned_storage_file(vs_id):
    """
    Saves file content after conflicts were resolved.
    This operation returns task ID since may take a long time. Use `status/<task_id>` method to check result.
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
      - name: path
        in: query
        type: string
        required: true
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    path = flask.request.args.get('path')
    manager = app.config['git_manager']
    try:
        content = flask.request.stream.read()
        task_id = manager.save_file(vs_id, path, content)
        return jsonify(success({"task": task_id}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/files', methods=['GET'])
@auth.login_required
def get_versioned_storage_file(vs_id):
    """
    Returns local current file content
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
      - name: path
        in: query
        type: string
        required: true
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    path = flask.request.args.get('path')
    manager = app.config['git_manager']
    try:
        path_to_file = manager.get_file_path(vs_id, path)
        return Response(stream_with_context(get_file_stream(path_to_file)))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/revert', methods=['POST'])
@auth.login_required
def revert_versioned_storage(vs_id):
    """
    Reverts all local changes
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    manager = app.config['git_manager']
    try:
        manager.revert(vs_id)
        return jsonify(success({}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/remove', methods=['POST'])
@auth.login_required
def remove_version_storage(vs_id):
    """
    Removes versioned storage from run
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    manager = app.config['git_manager']
    try:
        manager.remove(vs_id)
        return jsonify(success({}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/checkout', methods=['POST'])
@auth.login_required
def checkout_version_storage(vs_id):
    """
    Checkouts to specified revision number.
    After checkout operation a `READ ONLY` regime will be enabled. To switch `READ ONLY` regime fetch data from server.
    Reverts all local changes if any.
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
      - name: revision
        in: query
        type: string
        required: true
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    revision = flask.request.args.get("revision")
    manager = app.config['git_manager']
    try:
        manager.checkout(vs_id, revision)
        return jsonify(success({}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/conflicts', methods=['POST'])
@auth.login_required
def version_storage_file_resolve(vs_id):
    """
    Registers changes after fetch conflicts resolving. If file was not specified and head was detached
    performs `git checkout HEAD` operation.
    If specified file has no conflicts an error will be occurred. Performs `git add` operation for files.
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
      - name: path
        in: query
        type: string
        required: false
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    path = flask.request.args.get('path')
    manager = app.config['git_manager']
    try:
        manager.add(vs_id, path)
        return jsonify(success({}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/diff/conflicts', methods=['GET'])
@auth.login_required
def version_storage_diff_conflicts(vs_id):
    """
    Returns `git diff` for files when merge is in progress. Returns `diff` between `revision` and last common commit
    between local and remote trees. If `revision` not specified the `HEAD` will be used.
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
      - name: path
        in: query
        type: string
        required: true
      - name: revision
        in: query
        type: string
        required: false
      - name: raw
        in: query
        type: boolean
        required: false
        default: false
      - name: lines_count
        in: query
        type: integer
        required: false
        default: 3
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    path = flask.request.args.get('path')
    revision = flask.request.args.get('revision', None)
    show_raw = flask.request.args.get("raw")
    show_raw_flag = False if not show_raw else str_to_bool(show_raw)
    lines_count = flask.request.args.get("lines_count", 3)
    manager = app.config['git_manager']
    try:
        result = manager.merge_conflicts_diff(vs_id, path, revision, show_raw_flag, lines_count=int(lines_count))
        return jsonify(success(result))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/diff/fetch/conflicts', methods=['GET'])
@auth.login_required
def version_storage_diff_fetch_conflicts(vs_id):
    """
    Returns `git diff` for specified `path` for conflicts after `fetch` operation. If `remote` parameter is true
    returns diff between newly loaded changes and commit before stash. If `remote` is false (default) returns
    diff from stash.
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
      - name: path
        in: query
        type: string
        required: true
      - name: raw
        in: query
        type: boolean
        required: false
        default: false
      - name: lines_count
        in: query
        type: integer
        required: false
        default: 3
      - name: remote
        in: query
        type: boolean
        required: false
        default: false
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    path = flask.request.args.get('path')
    show_raw = flask.request.args.get("raw")
    show_raw_flag = False if not show_raw else str_to_bool(show_raw)
    lines_count = flask.request.args.get("lines_count", 3)
    remote = flask.request.args.get("remote")
    remote_flag = False if not remote else str_to_bool(remote)
    manager = app.config['git_manager']
    try:
        result = manager.fetch_conflicts_diff(vs_id, path, show_raw_flag, lines_count=int(lines_count),
                                              remote_flag=remote_flag)
        return jsonify(success(result))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/merge/abort', methods=['POST'])
@auth.login_required
def version_storage_merge_abort(vs_id):
    """
    Aborts merge process.
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    manager = app.config['git_manager']
    try:
        manager.merge_abort(vs_id)
        return jsonify(success({}))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/vs/<vs_id>/checkout/path', methods=['POST'])
@auth.login_required
def version_storage_checkout_path(vs_id):
    """
    Accepts remote or local changes for specified file. (This file shall be in `conflicts` state).
    ---
    parameters:
      - name: vs_id
        in: path
        type: string
        required: true
      - name: path
        in: query
        type: string
        required: true
      - name: remote
        in: query
        type: boolean
        required: false
        default: false
    definitions:
      Object:
        type: object
    responses:
      200:
        schema:
          $ref: '#/definitions/Object'
    """
    path = flask.request.args.get('path')
    manager = app.config['git_manager']
    remote = flask.request.args.get("remote")
    remote_flag = False if not remote else str_to_bool(remote)
    try:
        manager.checkout_path(vs_id, path, remote_flag)
        return jsonify(success({}))
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

    args = parser.parse_args()

    logger = BrowserLogger(args.run_id, args.log_dir)
    pool = ThreadPool(processes=args.process_count)
    tasks = {}

    app.config['fsbrowser'] = FsBrowserManager(args.working_directory, pool, logger, args.transfer_storage,
                                               args.follow_symlinks, args.tmp_directory, args.exclude, tasks)
    app.config['git_manager'] = GitManager(pool, tasks, logger, args.vs_working_directory)

    app.run(host=args.host, port=args.port)


if __name__ == '__main__':
    main()
