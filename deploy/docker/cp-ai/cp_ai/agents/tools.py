import json

from pydantic import Field
from cp_ai.pipeline.pipelines import get_all_pipelines
from .pipeline.tools import launch_pipeline_by_user_query
from .docker_image.tools import launch_tool_by_user_query
from .utilities import extract_launch_payload
from cp_ai.llm import llm


def get_pipeline_id(pipeline_name: str) -> str:
    pipelines = get_all_pipelines()
    prompt = f"""Given the following pipeline name:
    {pipeline_name}
    Choose the best pipeline id to use from this list: [{pipelines}]. 
    If nothing suitable, return None.
    Only reply with the id."""
    return llm.complete(prompt).text.strip()


def get_command_to_run_pipeline(
        user_query: str,
        **kwargs
):
    """Useful to get a command to launch a **specific pipeline** (specified by name, identifier or by description).
    This function returns launch command or provides missing parameters that user needs to fulfil.
    Required inputs:
    - user_query: str - a user query that defines pipeline, parameters and environment information.
    """
    return launch_pipeline_by_user_query(
        user_query,
        **kwargs
    )


def get_command_to_run_compute_instance(
        user_query: str,
        **kwargs
):
    """Useful to get a command to launch a specific compute instance (not a pipeline),
    specified by "tool", "docker image", "image" or similar words, e.g. "launch XXX instance", "launch XXX".
    This function returns launch command or provides missing parameters that user needs to fulfil.
    Required inputs:
    - user_query: str - a user query that defines tool (or image), parameters and environment information.
    """
    return launch_tool_by_user_query(
        user_query,
        **kwargs
    )


def get_command_to_run_compute_instance1(
        docker_image_name: str = Field(
            description="""
        Defines a docker image name to be used for the user's compute task.
        This parameter is optional, if not specified in the user prompt use default value: 'library/rockylinux:latest'.
        """
        ),
        pipeline_name: str = Field (
            description="""
        Defines a pipeline name to be used for user's compute task.
        This parameter is optional, if not specified in the user prompt leave value empty.
        """
        ),
        compute_instance_size: str = Field(
            description="""
        Defines AWS EC2 instance type to be created for the user's compute task.
        This parameter is optional, if not specified in the user prompt use default value: 'm5.xlarge'.
        """
        ),
        compute_instance_disk_size: str = Field(
            description="""
        Defines size of the disk provisioned for EC2 instance in gigabytes.
        This parameter is optional, if not specified in the user prompt use default value: '50'.
        """
        ),
        task_command: str = Field(
            description="""
        Defines a shell command used to start the user's task within a docker container.
        This parameter is optional, if not specified in the user prompt use default value: 'sleep infinity'.
        """
        ),
        input_paths: str = Field(
            description="""
        An optional list of AWS S3 paths, which are used by the compute instance.
        This parameter shall be formatted as a JSON array of objects. Each object shall have two fields: 'name' and 'path'.
        Example: 
        '[
            {
                "name": "file_input",
                "path": "s3://bucket_name/file_name.txt"
            },
            {
                "name": "directory_input",
                "path": "s3://bucket_name/directory_name"
            }
        ]'
        This parameter is optional, if not specified in the user prompt use default value: '[]'.
        """
        ),
        output_paths: str = Field(
            description="""
        An optional list of AWS S3 paths, which will be used by the compute instance to upload the results of processing the 'input_paths'.
        This parameter shall be formatted as a JSON array of objects. Each object shall have two fields: 'name' and 'path'. 'path' is always a directory.
        Example: 
        '[
            {
                "name": "directory1_output",
                "path": "s3://bucket_name/directory1_name"
            },
            {
                "name": "directory2_output",
                "path": "s3://bucket_name/directory2_name"
            }
        ]'
        This parameter is optional, if not specified in the user prompt use default value: '[]'.
        """
        )
) -> str:
    """Useful to get a command to start compute instance in AWS.
    Returns json to be used to start a compute task"""

    params = {}

    input_paths_cmd = ""
    if input_paths:
        for path_item in json.loads(input_paths):
            name = path_item['name']
            path = path_item['path']
            input_paths_cmd += f" {name} 'input?{path}' "
            params[name] = {'type': 'input'}

    output_paths_cmd = ""
    if output_paths:
        for path_item in json.loads(output_paths):
            name = path_item['name']
            path = path_item['path']
            output_paths_cmd += f" {name} 'output?{path}' "
            params[name] = {'type': 'output'}

    start_command = {
        "dockerImage": docker_image_name,
        "instanceType": compute_instance_size,
        "disk": compute_instance_disk_size,
        "cmd": task_command,
        "is_spot": False,
        "parameters": params
    }

    pipeline_id = get_pipeline_id(pipeline_name)
    if pipeline_id != "None":
        start_command["pipelineId"] = pipeline_id
    else:
        start_command["pipelineName"] = pipeline_name

    json_str = json.dumps(start_command)

    result = f"""Result: <<<LAUNCH:{json_str}>>>. Include this result into response to user."""
    print(result)
    return result

def stop_compute_instance(
        instance_run_id: int = Field(
            description="""
        Run ID of the compute instance.
        This is mandatory field. If it's not specified in the user prompt - reject to stop an instance.
        """
        )
) -> str:
    """Useful to stop an existing compute instance in AWS by it's Run ID."""
    pipe_run_cmd = f"""pipe stop \
                       -y \
                       {instance_run_id}"""
    print(pipe_run_cmd)
    return pipe_run_cmd


def get_compute_instance_state(
        instance_run_id: int = Field(
            description="""
        Run ID of the compute instance to report status.
        If specified as '-1' - all available instances statuses are returned.
        """
        )
) -> str:
    """Useful to get information about compute instances. Either a specific one or all available instances."""
    pipe_run_cmd = "pipe view-runs --parameters-details --tasks-details "
    if instance_run_id != -1:
        pipe_run_cmd += str(instance_run_id)
    print(pipe_run_cmd)
    return pipe_run_cmd