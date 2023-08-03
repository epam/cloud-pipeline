import math
import re
from datetime import datetime

from pipeline.hpc.cmd import ExecutionError
from pipeline.hpc.logger import Logger
from pipeline.hpc.resource import IntegralDemand, ResourceSupply
from pipeline.hpc.engine.gridengine import GridEngine, GridEngineJobState, GridEngineJob, \
    GridEngineType, _perform_command, GridEngineDemandSelector, GridEngineJobValidator, AllocationRuleParsingError


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

    def get_jobs(self):
        try:
            output = self.cmd_executor.execute(SlurmGridEngine._GET_JOBS)
        except ExecutionError:
            Logger.warn('Slurm jobs listing has failed.')
            return []
        return self._parse_jobs(output)

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

    def get_engine_type(self):
        return GridEngineType.SLURM

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
        jobs = []
        jobs_des_lines = [line for line in scontrol_jobs_output.splitlines() if "JobId=" in line]
        for job_desc in jobs_des_lines:
            job_dict = self._parse_dict(job_desc)
            resources = self._parse_dict(job_dict.get("TRES"), line_sep=",")
            general_resources = self._parse_dict(job_dict.get("GRES"), line_sep=",")
            num_node = int(re.match("(\\d+)-?.*", job_dict.get("NumNodes", "1")).group(1))
            # Splitting one job on 'num_node' ephemeral jobs. The idea is to instruct autoscaler that we need to spread
            # this job to `num_node` nodes and provide portion of resources
            # TODO maybe there is another way to achieve that?
            for node_idx in range(num_node):
                jobs.append(
                    GridEngineJob(
                        id=job_dict.get("JobId") + "_" + str(node_idx),
                        root_id=job_dict.get("JobId"),
                        name=job_dict.get("JobName"),
                        user=self._parse_user(job_dict.get("UserId")),
                        state=GridEngineJobState.from_letter_code(job_dict.get("JobState")),
                        datetime=self._parse_date(
                            job_dict.get("StartTime") if job_dict.get("StartTime") != "Unknown" else job_dict.get("SubmitTime")),
                        hosts=self._parse_nodelist(job_dict.get("NodeList")),
                        cpu=int(job_dict.get("NumCPUs", "1")) // num_node,
                        gpu=0 if "gpu" not in general_resources else int(general_resources.get("gpu")) // num_node,
                        mem=self._parse_mem(self._find_memory_value(job_dict, resources))
                    )
                )
        return jobs

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

    def _find_memory_value(self, job_dict, resource_dict):
        if "MinMemoryNode" in job_dict:
            return job_dict.get("MinMemoryNode")
        elif "mem" in resource_dict:
            return resource_dict.get("mem")
        else:
            return "0M"

    def _parse_mem(self, mem_request):
        if not mem_request:
            return 0
        modifiers = {
            'k': 1000, 'm': 1000 ** 2, 'g': 1000 ** 3,
            'K': 1024, 'M': 1024 ** 2, 'G': 1024 ** 3
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
        matched = re.match("(\\w+)\\(\\d+\\)", user_id)
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
