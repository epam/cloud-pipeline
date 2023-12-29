import math
import re
from datetime import datetime

from pipeline.hpc.cmd import ExecutionError
from pipeline.hpc.logger import Logger
from pipeline.hpc.resource import IntegralDemand, ResourceSupply
from pipeline.hpc.engine.gridengine import GridEngine, GridEngineJobState, GridEngineJob, \
    GridEngineType, _perform_command, GridEngineDemandSelector, GridEngineJobValidator, AllocationRuleParsingError, \
    GridEngineLaunchAdapter


class SlurmGridEngine(GridEngine):

    _KILL_JOBS = "scancel %s"
    _FORCE_KILL_JOBS = "scancel -f %s"
    _SHOW_EXECUTION_HOST = "scontrol -o show node %s"
    _SCONTROL_UPDATE_NODE_STATE = "scontrol update State=%s Reason='CP_CAP_GE: Autoscale lifecycle event.' NodeName=%s"
    _SCONTROL_DELETE_NODE = "scontrol delete nodename=%s"
    _SCONTROL_PARSE_HOSTLIST = "scontrol show hostnames %s"

    _NODE_BAD_STATES = ["DOWN", "DRAINING", "DRAIN", "DRAINED", "FAIL", "FAILING",  "INVAL"]

    _SCONTROL_DATETIME_FORMAT = '%Y-%m-%dT%H:%M:%S'
    _GET_JOBS = "scontrol -o show job"

    def __init__(self, cmd_executor):
        self.cmd_executor = cmd_executor
        self.job_state_to_codes = {
            GridEngineJobState.RUNNING: ['RUNNING'],
            GridEngineJobState.PENDING: ['PENDING'],
            GridEngineJobState.SUSPENDED: ['SUSPENDED', 'STOPPED'],
            GridEngineJobState.ERROR: ['DEADLINE', ' FAILED'],
            GridEngineJobState.DELETED: ['DELETED', 'CANCELLED'],
            GridEngineJobState.COMPLETED: ['COMPLETED', 'COMPLETING'],
            GridEngineJobState.UNKNOWN: []
        }

    def get_engine_type(self):
        return GridEngineType.SLURM

    def get_jobs(self):
        try:
            output = self.cmd_executor.execute(SlurmGridEngine._GET_JOBS)
        except ExecutionError:
            Logger.warn('Slurm jobs listing has failed.')
            return []
        return list(self._parse_jobs(output))

    def disable_host(self, host):
        self.cmd_executor.execute(SlurmGridEngine._SCONTROL_UPDATE_NODE_STATE % ("DRAIN", host))

    def enable_host(self, host):
        host_state = self._get_host_state(host)
        if "DRAIN" in host_state:
            self.cmd_executor.execute(SlurmGridEngine._SCONTROL_UPDATE_NODE_STATE % ("UNDRAIN", host))
        else:
            # NO-OP for all node states except DRAIN
            pass

    def get_pe_allocation_rule(self, pe):
        raise AllocationRuleParsingError("Slurm doesn't have PE preference.")

    def delete_host(self, host, skip_on_failure=False):
        _perform_command(
            action=lambda: self.cmd_executor.execute(SlurmGridEngine._SCONTROL_DELETE_NODE % host),
            msg='Remove host from GE.',
            error_msg='Removing host from GE has failed.',
            skip_on_failure=skip_on_failure
        )

    def get_host_supplies(self):
        for line in self.cmd_executor.execute_to_lines(SlurmGridEngine._SHOW_EXECUTION_HOST % ''):
            if "NodeName" in line:
                node_desc = self._parse_dict(line)
                yield ResourceSupply(cpu=int(node_desc.get("CPUTot", "0"))) \
                    - ResourceSupply(cpu=int(node_desc.get("CPUAlloc", "0")))

    def get_host_supply(self, host):
        for line in self.cmd_executor.execute_to_lines(SlurmGridEngine._SHOW_EXECUTION_HOST % host):
            if "NodeName" in line:
                node_desc = self._parse_dict(line)
                return ResourceSupply(cpu=int(node_desc.get("CPUTot", "0"))) \
                    - ResourceSupply(cpu=int(node_desc.get("CPUAlloc", "0")))
        return ResourceSupply()

    def is_valid(self, host):
        node_state = self._get_host_state(host)
        for bad_state in SlurmGridEngine._NODE_BAD_STATES:
            if bad_state in node_state:
                Logger.warn('Execution host %s GE state is %s which makes host invalid.' % (host, bad_state))
                return False
        return True

    def kill_jobs(self, jobs, force=False):
        job_ids = sorted(set(str(job.root_id) for job in jobs))
        self.cmd_executor.execute((SlurmGridEngine._FORCE_KILL_JOBS if force else SlurmGridEngine._KILL_JOBS) % ' '.join(job_ids))

    def _get_host_state(self, host):
        try:
            for line in self.cmd_executor.execute_to_lines(SlurmGridEngine._SHOW_EXECUTION_HOST % host):
                if "NodeName" in line:
                    return self._parse_dict(line).get("State", "UNKNOWN")
        except ExecutionError as e:
            Logger.warn("Problems with getting host '%s' info: %s" % (host, e))
            return "UNKNOWN"

    def _parse_jobs(self, scontrol_jobs_output):
        for job_desc in scontrol_jobs_output.splitlines():
            if 'JobId=' not in job_desc:
                continue

            job_dict = self._parse_dict(job_desc)

            root_job_id = job_dict.get('JobId')
            job_name = job_dict.get('JobName')
            job_user = self._parse_user(job_dict.get('UserId'))
            job_hosts = self._parse_nodelist(job_dict.get('NodeList'))

            #       -> NumTasks=1
            # ?     -> NumTasks=N/A
            # -n 20 -> NumTasks=20
            # -N 20 -> NumTasks=20 NumNodes=20
            num_tasks_str = job_dict.get('NumTasks', '1')
            num_tasks = int(num_tasks_str) if num_tasks_str.isdigit() else 1

            job_state = GridEngineJobState.from_letter_code(job_dict.get('JobState'), self.job_state_to_codes)
            if job_state == GridEngineJobState.PENDING:
                # In certain cases pending job's start date can be estimated start date.
                # It confuses autoscaler and therefore should be ignored.
                job_dict['StartTime'] = 'Unknown'
            job_datetime = self._parse_date(job_dict.get('StartTime') if job_dict.get('StartTime') != 'Unknown'
                                            else job_dict.get('SubmitTime'))

            # -c 20 -> MinCPUsNode=20
            cpu_per_node = int(job_dict.get('MinCPUsNode', '1'))
            job_cpu = cpu_per_node

            # --gpus  20         -> TresPerJob=gres:gpu:20
            # --gpus-per-job  20 -> TresPerJob=gres:gpu:20
            # --gpus-per-task 20 -> TresPerTask=gres:gpu:20
            # --gpus-per-node 20 -> TresPerNode=gres:gpu:20
            tres_per_job = self._parse_tres(job_dict.get('TresPerJob', 'gres:gpu:0'))
            tres_per_node = self._parse_tres(job_dict.get('TresPerNode', 'gres:gpu:0'))
            tres_per_task = self._parse_tres(job_dict.get('TresPerTask', 'gres:gpu:0'))
            gpu_per_job = int(tres_per_job.get('gpu', '0'))
            gpu_per_node = int(tres_per_node.get('gpu', '0'))
            gpu_per_task = int(tres_per_task.get('gpu', '0'))
            job_gpu = max(int(gpu_per_job / num_tasks), gpu_per_node, gpu_per_task)

            # --mem 200M         -> MinMemoryNode=200M
            # --mem-per-cpu 200M -> MinMemoryCPU=200M
            # --mem-per-gpu 200M -> MemPerTres=gres:gpu:200
            mem_per_tres = self._parse_tres(job_dict.get('MemPerTres', 'gres:gpu:0'))
            mem_per_node = self._parse_mem(job_dict.get('MinMemoryNode', '0M'))
            mem_per_cpu = self._parse_mem(job_dict.get('MinMemoryCPU', '0M'))
            mem_per_gpu = self._parse_mem(mem_per_tres.get('gpu', '0') + 'M')
            job_mem = max(mem_per_node, mem_per_cpu * job_cpu, mem_per_gpu * job_gpu)

            for task_idx in range(num_tasks):
                job_id = root_job_id + '_' + str(task_idx)
                yield GridEngineJob(
                    id=job_id,
                    root_id=root_job_id,
                    name=job_name,
                    user=job_user,
                    state=job_state,
                    datetime=job_datetime,
                    hosts=job_hosts,
                    cpu=job_cpu,
                    gpu=job_gpu,
                    mem=job_mem
                )

    def _parse_tres(self, tres_str):
        if not tres_str:
            return {}

        tres_dict = {}
        for tres_str in tres_str.split(','):
            tres_items = tres_str.split(':')
            if len(tres_items) == 3:
                tres_type, tres_group, tres_value = tres_items
            elif len(tres_items) == 4:
                tres_type, tres_group, tres_name, tres_value = tres_items
            else:
                continue
            tres_dict[tres_group] = tres_value
        return  tres_dict

    def _parse_date(self, date):
        return datetime.strptime(date, SlurmGridEngine._SCONTROL_DATETIME_FORMAT)

    def _parse_dict(self, text, line_sep=" ", value_sep="="):
        if not text:
            return {}
        return {
            key_value[0]: key_value[1] if len(key_value) == 2 else "" for key_value in
            [
                entry.split(value_sep, 1) for entry in text.split(line_sep)
            ]
        }

    def _parse_mem(self, mem_request):
        if not mem_request:
            return 0
        modifiers = {
            'k': 1000, 'm': 1000 ** 2, 'g': 1000 ** 3, 't': 1000 ** 4,
            'K': 1024, 'M': 1024 ** 2, 'G': 1024 ** 3, 'T': 1024 ** 4,
        }
        if mem_request[-1] in modifiers:
            number = int(mem_request[:-1])
            modifier = modifiers[mem_request[-1]]
        else:
            number = int(mem_request)
            modifier = 1
        size_in_bytes = number * modifier
        size_in_gibibytes = int(math.ceil(size_in_bytes / modifiers['G']))
        return size_in_gibibytes

    def _parse_nodelist(self, nodelist):
        if nodelist == "(null)":
            return []
        return self.cmd_executor.execute_to_lines(SlurmGridEngine._SCONTROL_PARSE_HOSTLIST % nodelist)

    def _parse_user(self, user_id):
        matched = re.match("(.+)\\(\\d+\\)", user_id)
        if matched:
            return matched.group(1)
        else:
            return user_id


class SlurmDemandSelector(GridEngineDemandSelector):

    def __init__(self, grid_engine):
        self.grid_engine = grid_engine

    def select(self, jobs):
        _provisioned_root_jobs = set()
        # Check if root_job was already provisioned with resources on a prev yield and if so - return empty demand.
        #
        # We are doing so because all jobs with the same rood_id is a "secondary" jobs, that were created by splitting
        # resources of main real job on number of jobs = root_job["NumNodes"] (see SlurmGridEngine._parse_jobs),
        # so requesting for all "secondary" jobs the same amount of resources will lead to requesting a big node
        # but will not allow to utilize it fully, because actually we need several small nodes.
        #
        # For more details see Slurm sbatch docs (-N option particular),
        # GridEngineAutoscaler.scale() method and how resources demand are calculated
        for job in jobs:
            if job.root_id not in _provisioned_root_jobs:
                _provisioned_root_jobs.add(job.root_id)
                yield IntegralDemand(cpu=job.cpu, gpu=job.gpu, mem=job.mem, owner=job.user)
            else:
                yield IntegralDemand()


class SlurmJobValidator(GridEngineJobValidator):

    def __init__(self, grid_engine, instance_max_supply, cluster_max_supply):
        self.grid_engine = grid_engine
        self.instance_max_supply = instance_max_supply
        self.cluster_max_supply = cluster_max_supply

    def validate(self, jobs):
        valid_jobs, invalid_jobs = [], []
        for job in jobs:
            job_demand = IntegralDemand(cpu=job.cpu, gpu=job.gpu, mem=job.mem)
            if job_demand > self.instance_max_supply:
                Logger.warn('Invalid job #{job_id} {job_name} by {job_user} requires resources '
                            'which cannot be satisfied by the biggest instance in cluster: '
                            '{job_cpu}/{available_cpu} cpu, '
                            '{job_gpu}/{available_gpu} gpu, '
                            '{job_mem}/{available_mem} mem.'
                            .format(job_id=job.id, job_name=job.name, job_user=job.user,
                                    job_cpu=job.cpu, available_cpu=self.instance_max_supply.cpu,
                                    job_gpu=job.gpu, available_gpu=self.instance_max_supply.gpu,
                                    job_mem=job.mem, available_mem=self.instance_max_supply.mem),
                            crucial=True)
                invalid_jobs.append(job)
                continue
            valid_jobs.append(job)
        return valid_jobs, invalid_jobs


class SlurmLaunchAdapter(GridEngineLaunchAdapter):

    def __init__(self):
        pass

    def get_worker_init_task_name(self):
        return 'SLURMWorkerSetup'

    def get_worker_launch_params(self):
        return {
            'CP_CAP_SLURM': 'false'
        }
