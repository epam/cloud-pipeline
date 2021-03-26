# Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import traceback

import flask
from flask import Flask, jsonify, request

from gitreader.src.git_manager import GitManager
from gitreader.src.model.git_search_filter import GitSearchFilter

app = Flask(__name__)
# Force FLASK to accept both "http://url and http://url/"
app.url_map.strict_slashes = False


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


@app.route('/git/<path:repo>/ls_tree')
def git_list_tree(repo):
    manager = app.config['gitmanager']
    try:
        path, page, page_size, ref = parse_url_params()
        list_tree = manager.ls_tree(repo, path, ref, page, page_size)
        return jsonify(success(list_tree.to_json()))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/git/<path:repo>/logs_tree',  methods=["GET", "POST"])
def git_logs_tree(repo):
    manager = app.config['gitmanager']
    path, page, page_size, ref = parse_url_params()
    try:
        if request.method == 'POST':
            data = load_data_from_request(request)
            logs_tree = manager.logs_paths(repo, ref, data)
            return jsonify(success(logs_tree.to_json()))
        else:
            logs_tree = manager.logs_tree(repo, path, ref, page, page_size)
            return jsonify(success(logs_tree.to_json()))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/git/<path:repo>/commits', methods=["POST"])
def git_list_commits(repo):
    manager = app.config['gitmanager']
    try:
        data = load_data_from_request(request)
        filters = GitSearchFilter.from_json(data["filter"]) if "filter" in data else GitSearchFilter()
        _, page, page_size, _ = parse_url_params()
        commits = manager.list_commits(repo, filters, page, page_size)
        return jsonify(success(commits.to_json()))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/git/<path:repo>/diff', methods=["POST"])
def git_diff_report(repo):
    manager = app.config['gitmanager']
    try:
        data = load_data_from_request(request)
        filters = GitSearchFilter.from_json(data["filter"]) if "filter" in data else GitSearchFilter()
        include_diff = False
        if request.args.get('include_diff'):
            include_diff = str_to_bool(request.args.get('include_diff'))
        unified_lines = 3
        if request.args.get('unified_lines'):
            unified_lines = int(request.args.get('unified_lines'))
        diff_report = manager.diff_report(repo, filters, include_diff, unified_lines)
        return jsonify(success(diff_report.to_json()))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


def load_data_from_request(req):
    if req.data:
        return flask.json.loads(req.data)
    else:
        return {}


def str_to_bool(input_value):
    return input_value and input_value.lower() in ("true", "t")


def parse_url_params():
    path = "."
    if request.args.get('path'):
        path = request.args.get('path')
    page = 0
    if request.args.get('page'):
        page = int(request.args.get('page'))
    page_size = 20
    if request.args.get('page_size'):
        page_size = int(request.args.get('page_size'))
    ref = "HEAD"
    if request.args.get('ref'):
        ref = request.args.get('ref')
    return path, page, page_size, ref


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default="5000")
    parser.add_argument("--git_root", default="/var/opt/gitlab/git-data/repositories")

    args = parser.parse_args()
    app.config['gitmanager'] = GitManager(args.git_root)
    app.run(host=args.host, port=args.port)


if __name__ == '__main__':
    main()
