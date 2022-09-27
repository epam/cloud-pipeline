import json
import sys
import os
import traceback
import argparse
import flask
from flask import Flask, jsonify,  request

from src.hcs_manager import HCSManager


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


def ok():
    return {
        "status": "OK"
    }


def get_required_parameter(parameter_name):
    param = flask.request.args.get(parameter_name)
    if not param:
        raise RuntimeError("Parameter '%s' must be specified." % parameter_name)
    return param


PIPELINE_ID = "pipelineId"
MODULE_ID = "moduleId"


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
        manager.launch_pipeline(get_required_parameter(PIPELINE_ID))
        return jsonify(ok())
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/run/modules', methods=['POST'])
def run_module():
    manager = app.config['hcs']
    try:
        pipeline_id = get_required_parameter(PIPELINE_ID)
        module_id = get_required_parameter(MODULE_ID)
        manager.run_module(pipeline_id, module_id)
        return jsonify(ok())
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/debug/start', methods=['POST'])
def debug_mode_start():
    manager = app.config['hcs']
    try:
        manager.start_debug_mode(get_required_parameter(PIPELINE_ID))
        return jsonify(ok())
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/debug/end', methods=['POST'])
def debug_mode_end():
    manager = app.config['hcs']
    try:
        manager.end_debug_mode(get_required_parameter(PIPELINE_ID))
        return jsonify(ok())
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/debug/flush', methods=['POST'])
def flush_debug_results():
    manager = app.config['hcs']
    try:
        manager.flush_debug_results(get_required_parameter(PIPELINE_ID))
        return jsonify(ok())
    except Exception as e:
        print(traceback.format_exc())
        return jsonify(error(e.__str__()))


@app.route('/hcs/debug/next', methods=['POST'])
def debug_next_image_set():
    manager = app.config['hcs']
    try:
        manager.debug_next_image_set(get_required_parameter(PIPELINE_ID))
        return jsonify(ok())
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
