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
from flask import Flask, jsonify, send_from_directory, request

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


@app.route('/git/<repo>/ls_tree')
def git_list_tree(repo):
    manager = app.config['gitmanager']
    try:
        path = "."
        if request.args.get('path'):
            path = request.args.get('path')
        page_size = 20
        if request.args.get('page_size'):
            page_size = int(request.args.get('page_size'))
        page = 0
        if request.args.get('page'):
            page = int(request.args.get('page'))
        ref = "HEAD"
        if request.args.get('ref'):
            ref = request.args.get('ref')
        list_tree = manager.ls_tree(repo, path, ref, page, page_size)
        return jsonify(success(list_tree.to_json()))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/git/<repo>/logs_tree')
def git_logs_tree(repo):
    manager = app.config['gitmanager']
    try:
        path = "."
        if request.args.get('path'):
            path = request.args.get('path')
        page_size = 20
        if request.args.get('page_size'):
            page_size = int(request.args.get('page_size'))
        page = 0
        if request.args.get('page'):
            page = int(request.args.get('page'))
        ref = "HEAD"
        if request.args.get('ref'):
            ref = request.args.get('ref')
        logs_tree = manager.logs_tree(repo, path, ref, page, page_size)
        return jsonify(success(logs_tree.to_json()))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/git/<repo>/commits', methods=["POST"])
def git_list_commits(repo):
    manager = app.config['gitmanager']
    try:
        data = load_data_from_request(request)
        filters = GitSearchFilter.from_json(data["filter"]) if "filter" in data else GitSearchFilter()
        page_size = 20
        if request.args.get('page_size'):
            page_size = int(request.args.get('page_size'))
        page = 0
        if request.args.get('page'):
            page = int(request.args.get('page'))
        commits = manager.list_commits(repo, filters, page, page_size)
        return jsonify(success(commits.to_json()))
    except Exception as e:
        manager.logger.log(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/git/<repo>/diff', methods=["POST"])
def git_diff_report(repo):
    manager = app.config['gitmanager']
    try:
        data = load_data_from_request(request)
        filters = GitSearchFilter.from_json(data["filter"]) if "filter" in data else GitSearchFilter()
        include_diff = False
        if request.args.get('include_diff'):
            include_diff = bool(request.args.get('include_diff'))
        diff_report = manager.diff_report(repo, filters, include_diff)
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
    return input_value.lower() in ("true", "t")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default="5000")
    parser.add_argument("--git_root", default="/var/opt/gitlab/git-data/repositories")

    # TODO: should we move pool and add another manager?
    args = parser.parse_args()
    app.config['gitmanager'] = GitManager(args.git_root)
    app.run(host=args.host, port=args.port)


if __name__ == '__main__':
    main()
