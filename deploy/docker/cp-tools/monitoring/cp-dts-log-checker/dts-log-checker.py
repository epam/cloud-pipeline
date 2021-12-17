import glob
import os
from datetime import datetime
import re
import codecs
import copy
from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger, TaskLogger, RunLogger

DTS_LAST_RECORD_FILE = 'last_record.txt'
DTS_EMAIL_TEMPLATE = 'template.html'
DTS_LOGS_FILES = os.getenv('CP_DTS_LOGS_FILES', 'dts*.*')
DATE_TIME_FORMAT = '%Y-%m-%d %H:%M:%S.%f'

dts_user = os.getenv('CP_DTS_LOG_NOTIFICATION_USER')
if not dts_user:
    raise RuntimeError('CP_DTS_LOG_NOTIFICATION_USER is not defined!')
dts_users_copy = os.getenv('CP_DTS_LOG_NOTIFICATION_USERS_COPY', None)
dts_users_copy_list = [cc_user.strip() for cc_user in dts_users_copy.split(
    ",")] if dts_users_copy else []
dts_notify_subject = os.getenv(
    'CP_DTS_LOG_NOTIFICATION_SUBJECT', 'DTS logs files has errors')
dts_path = os.getenv('CP_DTS_LOG_CHECKER_SYSTEM_DIR')
if not dts_path or not os.path.isdir(dts_path):
    raise RuntimeError(
        "CP_DTS_LOG_CHECKER_SYSTEM_DIR is not defined or doesn't exist on the system!")
dts_file = os.path.join(dts_path, DTS_LAST_RECORD_FILE)
dts_logs_path = os.getenv('CP_DTS_LOGS_DIR')
if not dts_logs_path or not os.path.isdir(dts_logs_path):
    raise RuntimeError('CP_DTS_LOGS_DIR is not defined!')
dts_logs_files = glob.glob(os.path.join(dts_logs_path, DTS_LOGS_FILES))
dts_log_url = os.getenv('CP_DTS_LOG_URL_TEMPLATE')
dts_token = os.getenv('API_TOKEN')
dts_newest_log_file = max(glob.iglob(os.path.join(
    dts_logs_path, DTS_LOGS_FILES)), key=os.path.getmtime)
email_template_file = os.path.join(dts_path, DTS_EMAIL_TEMPLATE)
pattern = os.getenv('CP_DTS_LOG_MESSAGE_PATTERN', r'/*ERROR./*')
run_id = os.getenv('RUN_ID', default='0')
pipeline_name = os.getenv('PIPELINE_NAME', default='pipeline')
runs_root = os.getenv('CP_RUNS_ROOT_DIR', default='/runs')
run_dir = os.getenv('RUN_DIR', default=os.path.join(
    runs_root, pipeline_name + '-' + run_id))
log_dir = os.getenv('LOG_DIR', default=os.path.join(run_dir, 'logs'))
pipeline_api = os.getenv('API')
api = PipelineAPI(api_url=pipeline_api, log_dir=log_dir)
logger = RunLogger(api=api, run_id=run_id)
logger = TaskLogger(task='DTSLogCheck', inner=logger)
logger = LocalLogger(inner=logger)


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
        <b>DTS log files with errors:</b><br>
        {}<br>
        Best regards, 
        EPM-AWS-Dev Platform<br>
    </body>
    </html>
        """

    return body


def generate_table(files_with_errors):

    def get_table_header(dts_log_url):
        if dts_log_url:
            return """
                <tr>
                    <td><b>Files</b></td>
                    <td><b>Access link</b></td>
                </tr>"""
        else:
            return """
                <tr>
                    <td><b>Files</b></td>
                </tr>"""

    def get_table_row(dts_log_url, file):
        if dts_log_url:
            file_link = dts_log_url + \
                os.path.basename(file) + '&contentDisposition=ATTACHMENT'
            return '<td>{}</td><td><a href="{}">Link</a></td>'.format(file, file_link)
        else:
            return '<td>{}</td>'.format(file)

    table = "<table>" + get_table_header(dts_log_url)
    for file in files_with_errors:
        table += "<tr>" + get_table_row(dts_log_url, file) + "</tr>"
    table += "</table>"
    return table


def retrieve_timestamp(str):
    match_str = re.search(
        '(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2}).(\d{3})', str)
    if match_str is not None and match_str.group():
        return datetime.strptime(match_str.group(), DATE_TIME_FORMAT)
    else:
        logger.warn("Can't find an applicable date in string: {}".format(str))


def retrieve_last_results_info():
    if os.path.exists(dts_file) and os.path.getsize(dts_file) > 0:
        with open(dts_file) as f:
            file_row = f.read()
            dates = file_row.split(',')
            script_runtime = retrieve_timestamp(dates[0])
            last_row_number = dates[1]
        return script_runtime, last_row_number
    else:
        logger.info('There is no information about previous results')
        return None, None


def retrieve_changed_files(script_runtime):
    changed_files = []
    for file in dts_logs_files:
        mtime = os.path.getmtime(file)
        last_modification_date = datetime.fromtimestamp(mtime)
        if not script_runtime or last_modification_date > script_runtime:
            changed_files.append(file)
    return changed_files


def file_contains_error(filename, line_to_start):
    with open(filename, 'r') as f:
        count = 0
        for line in f:
            if count < int(line_to_start):
                count += 1
                continue
            if re.search(pattern, line):
                return True


def save_last_record_file(dts_file, dts_newest_log_file, logger):
    with open(dts_newest_log_file, 'r') as f:
        latest_row_number = len(f.readlines())
        with open(dts_file, 'w+') as f:
            f.write(str(datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f'))
                    [:-3] + (',') + str(latest_row_number))
            logger.success("DTS file has been updated")


script_runtime, last_row_number = retrieve_last_results_info()
changed_files = retrieve_changed_files(script_runtime)
files_with_errors = set()

if not changed_files:
    logger.info('No new files to check')

else:
    sorted_changed_files = sorted(
        changed_files, key=os.path.getmtime, reverse=False)
    logger.info('There are new files, a check is needed')
    files_size = len(sorted_changed_files)
    for index in range(0, files_size):
        row_number = 0
        if index == 0 and last_row_number:
            row_number = int(last_row_number)
        if file_contains_error(sorted_changed_files[index], row_number):
            files_with_errors.add(sorted_changed_files[index])

table = generate_table(files_with_errors)
body = get_email_body(email_template_file)

if len(files_with_errors) != 0:
    logger.info("There are: {} logs files with errors".format(
        len(files_with_errors)))
    api.create_notification(dts_notify_subject, body.format(
        table), dts_user, dts_users_copy_list)
    recipients = copy.deepcopy(dts_users_copy_list)
    recipients.append(dts_user)
    logger.info("Message was sent to {}".format(str(recipients)))
else:
    logger.info("DTS logs have no errors, nothing to report")

save_last_record_file(dts_file, dts_newest_log_file, logger)
