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

import click
import logging
import os
import subprocess
import time
import sys

PIPE_RUN_TEMPLATE = "pipe run --yes --quiet --docker-image {docker_image} --cmd-template \"{cmd}\""
PIPE_VIEW_TEMPLATE = "pipe view-runs {run_id}"
TIMEOUT_POLL = 60
ATTEMPTS = 5
ATTEMPTS_TIMEOUT = 20
PARAMETER_DELIMITERS = ['=', ' ']

@click.command(context_settings=dict(
    ignore_unknown_options=True,
    allow_extra_args=True,
))
@click.argument('script', nargs=-1, required=True, type=str)
@click.option('-q', '--queue-name',
              help='Queue name for jobs submitting',
              default=None,
              required=False)
@click.option('-N', '--run-name',
              default=None,
              required=False)
@click.option('-o', '--output',
              default=None,
              required=False)
@click.option('-e', '--err',
              default=None,
              required=False)
@click.option('-V', '--env-vars',
              is_flag=True,
              default=None,
              required=False)
@click.option('-j', '--err-redirect',
              default=None,
              required=False)
@click.option('-sync', '--sync',
              default='no',
              required=False)
@click.option('-shell', '--shell',
              default=None,
              required=False)
@click.option('-b', '--binary',
              default=None,
              required=False)
@click.option('-cwd', '--current-dir',
              default=None,
              required=False,
              is_flag=True)
@click.option('-pe', '--parallel',
              default=None,
              required=False)
@click.option('-v', '--env',
              default=None,
              required=False,
              multiple=True)
@click.pass_context
def qsub(ctx, script, queue_name, run_name, output, err, env_vars, err_redirect, sync, shell, binary, current_dir, parallel, env):
    logging.basicConfig(filename='qsub.log', level=logging.INFO,
                        format='%(levelname)s %(asctime)s %(module)s:%(message)s')
    if not script:
        click.echo("Job command is not specified", err=True)
        exit(1)
    command = " ".join(script)
    logging.info("Command: {}".format(command))
    docker_image, instance_disk, instance_type, ncluster, ncores = get_parameters(env, parallel)
    run_command = build_pipe_run(command, docker_image, instance_type, instance_disk, ncluster, ncores)
    logging.info("Launching job with command: {}".format(run_command))
    exit_code, stdout, stderr = execute_command(run_command)
    if exit_code != 0:
        click.echo("Failed to start job: {}, {}".format(str(stderr), str(stdout)), err=True)
        exit(1)
    run_id = stdout[0].strip()
    started_msg = "Started job with ID: {}".format(run_id)
    logging.info(started_msg)
    click.echo(started_msg)
    if sync == 'y' or sync == 'yes':
        wait_run_finish(run_id)

@click.command()
@click.argument('script', nargs=-1, required=True, type=str)
@click.option('-q', '--queue-name',
              help='Queue name for jobs submitting',
              default=None,
              required=False)
def qstat(script, queue_name):
    click.echo(str(sys.argv[1:]))
    click.echo(script)
    click.echo(queue_name)

@click.command()
@click.argument('script', nargs=-1, required=True, type=str)
@click.option('-q', '--queue-name',
              help='Queue name for jobs submitting',
              default=None,
              required=False)
def qacct(script, queue_name):
    click.echo(str(sys.argv[1:]))
    click.echo(script)
    click.echo(queue_name)

def get_parallel_env(pe):
    param = parse_parameter_string(pe)
    ncluster = None
    ncores = None
    if param and param[0].lower() == 'ncluster':
        ncluster = param[1]
    elif param and param[0].lower() == 'ncores':
        ncores = param[1]

    return ncluster, ncores

def get_parameters(env, pe):
    ncluster, ncores = get_parallel_env(pe)

    docker_image = get_parameter_value('docker_image', env)
    instance_type = get_parameter_value('instance_type', env)
    instance_disk = get_parameter_value('instance_disk', env)
    if not docker_image:
        click.echo("Docker image is not specified", err=True)
        exit(1)
    if not instance_type and not ncores and not ncluster:
        click.echo("Instance type is not specified", err=True)
        exit(1)
    if not instance_disk:
        click.echo("Instance disk is not specified", err=True)
        exit(1)
    return docker_image, instance_disk, instance_type, ncluster, ncores


def build_pipe_run(command, docker_image, instance_type, instance_disk, instance_count, cluster_cores):
    pipe_run = PIPE_RUN_TEMPLATE.format(cmd=command, docker_image=docker_image)
    if instance_type:
        pipe_run += " --instance-type {instance_type}".format(instance_type=instance_type)
    if instance_disk:
        pipe_run += " --instance-disk {instance_disk}".format(instance_disk=instance_disk)
    if instance_count:
        pipe_run += " --instance-count {instance_count}".format(instance_count=instance_count)
    if cluster_cores:
        pipe_run += " --cores {cluster_cores}".format(cluster_cores=cluster_cores)
    return pipe_run


def read_output(output):
    result = []
    line = output.readline()
    while line:
        line = line.strip()
        if line:
            result.append(line)
        line = output.readline()
    return result


def execute_command(cmd):
    process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
    stdout = read_output(process.stdout)
    stderr = read_output(process.stderr)
    exit_code = process.wait()
    return exit_code, stdout, stderr


def parse_status(stdout):
    for line in stdout:
        if line.startswith("Status"):
            chunks = line.split()
            if len(chunks) != 2:
                raise RuntimeError("Unexpected line format {}".format(line))
            return chunks[1].strip()


def get_status(run_id):
    command = PIPE_VIEW_TEMPLATE.format(run_id=run_id)
    count = 0
    while count < ATTEMPTS:
        exit_code, stdout, stderr = execute_command(command)
        if exit_code != 0:
            logging.error("Error getting job {} status: {}".format(run_id, str(stderr)))
            count += 1
            time.sleep(ATTEMPTS_TIMEOUT)
        else:
            return parse_status(stdout)


def wait_run_finish(run_id):
    while True:
        status = get_status(run_id)
        if status != 'RUNNING' and status != 'SCHEDULED':
            logging.info("Job {} finished with status {}".format(run_id, status))
            if status != 'SUCCESS':
                click.echo("Job {} did not finish successfully: {}".format(run_id, status), err=True)
                exit(1)
            else:
                return
            time.sleep(TIMEOUT_POLL)

def parse_parameter_string(param):
    if not param:
        return None

    for delimiter in PARAMETER_DELIMITERS:
        chunks = param.split(delimiter)
        if len(chunks) != 2:
            continue
        else:
            return ( chunks[0], chunks[1] )

    return None

def get_parameter_value(param_name, env):
    param_value = None
    for var in env:
        param = parse_parameter_string(var)
        if not param:
            continue
        if param[0].lower() == param_name:
            param_value = param[1]
    if not param_value:
        param_value = os.environ.get(param_name)
    return param_value
