from functools import total_ordering
import json
import subprocess
import os
import sys
from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger, TaskLogger, RunLogger
import codecs
import copy

DTS_STATUS_FILE = "data.json"
DTS_EMAIL_TEMPLATE = "template.html"

dts_user = os.environ.get('CP_DTS_STATUS_NOTIFICATION_USER')
if not dts_user:
    raise RuntimeError("CP_DTS_STATUS_NOTIFICATION_USER is not defined!")
dts_users_copy = os.getenv('CP_DTS_STATUS_NOTIFICATION_USERS_COPY', None)
dts_users_copy_list = [cc_user.strip() for cc_user in dts_users_copy.split(",")] if dts_users_copy else []
dts_notify_subject = os.environ.get('CP_DTS_STATUS_NOTIFICATION_SUBJECT', 'DTS statuses were changed')

dts_path = os.getenv('CP_DTS_STATUS_CHECKER_SYSTEM_DIR')
if not dts_path or not os.path.isdir(dts_path):
    raise RuntimeError("CP_DTS_STATUS_CHECKER_SYSTEM_DIR is not defined!")

dts_file = os.path.join(dts_path, DTS_STATUS_FILE)
dts_token = os.getenv('API_TOKEN')
email_template_file = os.path.join(dts_path, DTS_EMAIL_TEMPLATE)


def map_pipe_dts_output_to_dts_status(raw_dts_status):
    return {
        "id": raw_dts_status["id"],
        "name": raw_dts_status["name"],
        "status": raw_dts_status["status"],
        "heartbeat": raw_dts_status["heartbeat"]
    }


def fetch_current_dts_statuses():
    get_new_dts_status_command = "pipe dts list -jo"
    process = subprocess.Popen(
        get_new_dts_status_command.split(), stdout=subprocess.PIPE)
    output, error = process.communicate()
    if process.returncode != 0:
        logger.error(error)
        sys.exit('Error with getting data')
    new_dts_status = json.loads(output)
    return {dts_status["id"]: dts_status for dts_status in map(map_pipe_dts_output_to_dts_status, new_dts_status)}


def get_email_body(email_template_file):
    body = {}
    if os.path.exists(email_template_file):
        with codecs.open(email_template_file,  "r", "utf-8") as html_file:
            body = html_file.read()
    else:
        body = """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
    <html xmlns="http://www.w3.org/1999/xhtml">
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
        <style>
            table,
            td {{
                border: 1px solid black;
                border-collapse: collapse;
                padding: 5px;
            }}
        </style>
    </head>
    <body>
        <p>Dear user,<br>
        *** This is a system generated email, do not reply to this email ***<br>
        <b>New DTS status was detected:</b><br>
        {}<br>
        Best regards, 
        EPM-AWS-Dev Platform<br>
    </body>
    </html>
        """

    return body


def generate_table(dts_to_inform):
    table = """<table>
    <tr>
		<td><b>ID</b></td>
		<td><b>Name</b></td>
		<td><b>Status</b></td>
		<td><b>Last heartbeat</b></td>
	</tr>"""
    for status in dts_to_inform:
        table += "<tr>" + "<td>{}</td>".format(status["id"]) + "<td>{}</td>".format(status["name"]) \
                        + "<td>{}</td>".format(status["status"]) + "<td>{}</td>".format(status["heartbeat"]) + "</tr>"

    table += "</table>"
    return table


run_id = os.getenv('RUN_ID', default='0')
pipeline_name = os.getenv('PIPELINE_NAME', default='pipeline')
runs_root = os.getenv('CP_RUNS_ROOT_DIR', default='/runs')
run_dir = os.getenv('RUN_DIR', default=os.path.join(runs_root, pipeline_name + '-' + run_id))
log_dir = os.getenv('LOG_DIR', default=os.path.join(run_dir, 'logs'))
pipeline_api = os.getenv('API')

api = PipelineAPI(api_url=pipeline_api, log_dir=log_dir)

logger = RunLogger(api=api, run_id=run_id)
logger = TaskLogger(task='DTSStatusCheck', inner=logger)
logger = LocalLogger(inner=logger)

new_dts_statuses = fetch_current_dts_statuses()
logger.info("Starting to check DTS")

previous_dts_statuses = {}
if os.path.exists(dts_file):
    with open(dts_file) as json_file:
        previous_dts_statuses = json.load(json_file)

dts_to_inform = []

for dts_id, new_dts_status in new_dts_statuses.iteritems():
    current_status = new_dts_status["status"]
    current_timestamp = new_dts_status["heartbeat"]
    logger.info("DTS with id {} has status {}. Last heartbeat: {}".format(
        dts_id, current_status, current_timestamp))
    prev_dts_status = previous_dts_statuses.get(str(dts_id))
    if not prev_dts_status or prev_dts_status["status"] != current_status:
        logger.info("DTS with id {} has status: {}. Previous status: {}".format(
        dts_id, current_status, prev_dts_status["status"]))
        dts_to_inform.append(new_dts_status)
    else:
        logger.info("DTS with id {} has the same status: {}.".format(
        dts_id, current_status))

table = generate_table(dts_to_inform)

body = get_email_body(email_template_file)

if len(dts_to_inform) != 0:
    logger.info("There are: {} dts to inform".format(len(dts_to_inform)))
    api.create_notification(dts_notify_subject, body.format(
        table), dts_user, dts_users_copy_list)
    recipients = copy.deepcopy(dts_users_copy_list)
    recipients.append(dts_user)
    logger.info("Message was sent to {}".format(str(recipients)))
else:
    logger.info("DTS status has not changed, nothing is reported")

with open(dts_file, 'w+') as f:
    json.dump(new_dts_statuses, f)
    logger.success("DTS file has been updated")
