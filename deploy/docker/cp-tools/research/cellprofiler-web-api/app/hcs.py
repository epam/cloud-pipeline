# Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import json
import sys
import os
import traceback
import argparse
import flask
from flask import Flask, jsonify,  request

from src.hcs_manager import HCSManager
from src.hcs_clip import create_clip, create_image


if getattr(sys, 'frozen', False):
    static_folder = os.path.join(sys._MEIPASS, 'hcs', 'static')
    templates_folder = os.path.join(sys._MEIPASS, 'hcs', 'template')
    app = Flask(__name__, static_folder=static_folder, template_folder=templates_folder)
else:
    app = Flask(__name__)


sys.path.append(os.getcwd())

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


@app.route('/hcs/pipelines', methods=['POST'])
def create_pipeline():
    manager = app.config['hcs']
    try:
        measurement_uuid = flask.request.args.get("measurementUUID")
        if not measurement_uuid:
            raise RuntimeError("Parameter 'measurementUUID' must be specified.")
        # To address older hcs files, which contained "s3://<bucket>/..." as a measurement id
        measurement_uuid = measurement_uuid.split('/')[-1]
        pipeline_id = manager.create_pipeline(measurement_uuid)
        return jsonify(success({"pipelineId": pipeline_id}))
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/pipelines', methods=['GET'])
def get_pipeline():
    manager = app.config['hcs']
    try:
        pipeline_id = flask.request.args.get("pipelineId")
        if not pipeline_id:
            raise RuntimeError("Parameter 'pipelineId' must be specified.")
        pipeline_id = manager.get_pipeline(pipeline_id)
        return jsonify(success({"pipelineId": pipeline_id}))
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/pipelines/files', methods=['POST'])
def add_files():
    manager = app.config['hcs']
    try:
        pipeline_id = flask.request.args.get("pipelineId")
        if not pipeline_id:
            raise RuntimeError("Parameter 'pipelineId' must be specified.")
        files_data = json.loads(request.data)
        manager.add_files(pipeline_id, files_data)
        return jsonify({"status": "OK"})
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/modules', methods=['POST'])
def create_module():
    manager = app.config['hcs']
    try:
        module_data = json.loads(request.data)
        pipeline_id = flask.request.args.get("pipelineId")
        if not pipeline_id:
            raise RuntimeError("Parameter 'pipelineId' must be specified.")
        manager.create_module(pipeline_id, module_data)
        return jsonify({"status": "OK"})
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/modules/move', methods=['POST'])
def move_module():
    manager = app.config['hcs']
    try:
        pipeline_id = flask.request.args.get("pipelineId")
        if not pipeline_id:
            raise RuntimeError("Parameter 'pipelineId' must be specified.")
        module_id = flask.request.args.get("moduleId")
        if not module_id:
            raise RuntimeError("Parameter 'moduleId' must be specified.")
        direction = flask.request.args.get("direction")
        if not direction:
            raise RuntimeError("Parameter 'direction' must be specified.")
        manager.move_module(pipeline_id, module_id, direction)
        return jsonify({"status": "OK"})
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/modules', methods=['PUT'])
def update_module():
    manager = app.config['hcs']
    try:
        module_data = json.loads(request.data)
        pipeline_id = flask.request.args.get("pipelineId")
        if not pipeline_id:
            raise RuntimeError("Parameter 'pipelineId' must be specified.")
        module_id = flask.request.args.get("moduleId")
        if not module_id:
            raise RuntimeError("Parameter 'moduleId' must be specified.")
        manager.update_module(pipeline_id, module_id, module_data)
        return jsonify({"status": "OK"})
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/modules', methods=['DELETE'])
def delete_module():
    manager = app.config['hcs']
    try:
        pipeline_id = flask.request.args.get("pipelineId")
        if not pipeline_id:
            raise RuntimeError("Parameter 'pipelineId' must be specified.")
        module_id = flask.request.args.get("moduleId")
        if not pipeline_id:
            raise RuntimeError("Parameter 'moduleId' must be specified.")
        manager.delete_module(pipeline_id, module_id)
        return jsonify({"status": "OK"})
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/run/pipelines', methods=['POST'])
def run_pipeline():
    manager = app.config['hcs']
    try:
        pipeline_id = flask.request.args.get("pipelineId")
        if not pipeline_id:
            raise RuntimeError("Parameter 'pipelineId' must be specified.")
        manager.launch_pipeline(pipeline_id)
        return jsonify({"status": "OK"})
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/run/modules', methods=['POST'])
def run_module():
    manager = app.config['hcs']
    try:
        pipeline_id = flask.request.args.get("pipelineId")
        if not pipeline_id:
            raise RuntimeError("Parameter 'pipelineId' must be specified.")
        module_id = flask.request.args.get("moduleId")
        if not pipeline_id:
            raise RuntimeError("Parameter 'moduleId' must be specified.")
        manager.run_module(pipeline_id, module_id)
        return jsonify({"status": "OK"})
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/run/statuses', methods=['GET'])
def get_status():
    manager = app.config['hcs']
    try:
        pipeline_id = flask.request.args.get("pipelineId")
        if not pipeline_id:
            raise RuntimeError("Parameter 'pipelineId' must be specified.")
        module_id = flask.request.args.get("moduleId")
        response = manager.get_status(pipeline_id, module_id)
        return jsonify(success(response))
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/clip', methods=['GET'])
def get_movie():
    try:
        params = flask.request.args
        clip_full_path, total_time = create_clip(params)
        return jsonify(success({"path": clip_full_path, "time": total_time}))
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/images', methods=['GET'])
def get_image():
    try:
        params = flask.request.args
        image_full_path = create_image(params)
        return jsonify(success({"path": image_full_path}))
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default="5000")

    args = parser.parse_args()

    pipelines = {}

    app.config['hcs'] = HCSManager(pipelines)

    app.run(host=args.host, port=args.port)


if __name__ == '__main__':
    main()
