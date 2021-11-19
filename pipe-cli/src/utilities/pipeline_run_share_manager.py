# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import sys
import click
from prettytable import prettytable

from src.api.pipeline_run import PipelineRun


class PipelineRunShareManager(object):

    def __init__(self):
        pass

    def get(self, run_id):
        run = PipelineRun.get(run_id)
        if not run:
            raise RuntimeError("Failed to load run '%s'" % str(run_id))
        if not run.run_sids or len(run.run_sids) == 0:
            click.echo("Not shared (use 'pipe share add' to configure)")
            return
        self._check_run_is_running(run)
        table = prettytable.PrettyTable()
        table.field_names = ["User/group", "SSH shared"]
        table.align = "l"
        table.header = True
        for sid in run.run_sids:
            table.add_row([sid.name, '+' if sid.access_type == 'SSH' else ''])
        click.echo(table)

    def add(self, run_id, users, groups, ssh):
        run = PipelineRun.get(run_id)
        if not run:
            click.echo("Failed to load run '%s'" % str(run_id), err=True)
            sys.exit(1)
        if not users and not groups or len(users) == 0 and len(groups) == 0:
            click.echo("Users or groups must be specified", err=True)
            sys.exit(1)
        self._check_run_is_running(run)
        if not run.endpoints and not ssh:
            click.echo("Run doesn't have endpoints. Please, specify '-ssh' option to share ssh.", err=True)
            sys.exit(1)

        existing_users, existing_groups = self._get_existing_sids(run, run_id)
        self._add_sids(users, existing_users, run_id, ssh, True)
        self._add_sids(groups, existing_groups, run_id, ssh, False)

        result = PipelineRun.update_run_sids(run_id, existing_users.values() + existing_groups.values())
        if not result:
            click.echo("Failed to share run '%s'" % str(run_id), err=True)
            sys.exit(1)
        click.echo("Done")

    def remove(self, run_id, users, groups, ssh):
        run = PipelineRun.get(run_id)
        if not run:
            click.echo("Failed to load run '%s'" % str(run_id), err=True)
            sys.exit(1)
        self._check_run_is_running(run)

        if not users and not groups or len(users) == 0 and len(groups) == 0:
            sids_to_delete = list()
            click.echo("Run '%s' will be unshared for all users and groups", str(run_id))
        else:
            existing_users, existing_groups = self._get_existing_sids(run, run_id)
            self._delete_sids(users, existing_users, run_id, ssh, True, run)
            self._delete_sids(groups, existing_groups, run_id, ssh, False, run)
            sids_to_delete = self._filter_nulls(existing_users.values()) + self._filter_nulls(existing_groups.values())
        result = PipelineRun.update_run_sids(run_id, sids_to_delete)
        if not result:
            click.echo("Failed to unshare run '%s'" % str(run_id), err=True)
            sys.exit(1)
        click.echo("Done")

    @staticmethod
    def _check_run_is_running(run):
        if run.status != 'RUNNING':
            click.echo("Run is not running", err=True)
            sys.exit(1)

    @staticmethod
    def _to_json(name, is_principal, access_type, run_id):
        return {
            "name": name,
            "runId": run_id,
            "isPrincipal": is_principal,
            "accessType": str(access_type).upper()
        }

    @staticmethod
    def _model_to_json(sid_model, run_id):
        return PipelineRunShareManager._to_json(sid_model.name, sid_model.is_principal, sid_model.access_type, run_id)

    @staticmethod
    def _determine_access_type(ssh):
        return 'SSH' if ssh else 'ENDPOINT'

    def _delete_sids(self, sids, existing_sids, run_id, ssh, is_principal, run):
        if sids:
            for sid in sids:
                existing_sid = existing_sids.get(sid)
                if not existing_sid:
                    click.echo("Run '%s' was not shared for user or group '%s'" % (str(run_id), sid))
                    continue
                if ssh and run.endpoints:
                    existing_sids.update({sid: self._to_json(sid, is_principal, 'ENDPOINT', run_id)})
                else:
                    existing_sids.update({sid: None})
                click.echo("Run '%s' will be unshared for user or group '%s'" % (str(run_id), sid))

    @staticmethod
    def _filter_nulls(sids):
        return [sid for sid in sids if sid is not None]

    def _get_existing_sids(self, run, run_id):
        existing_users = dict()
        existing_groups = dict()
        for sid in run.run_sids:
            if sid.is_principal:
                existing_users.update({sid.name: self._model_to_json(sid, run_id)})
            else:
                existing_groups.update({sid.name: self._model_to_json(sid, run_id)})
        return existing_users, existing_groups

    def _add_sids(self, sids, existing_sids, run_id, ssh, is_principal):
        if sids:
            for sid in sids:
                existing_sids.update({sid: self._to_json(sid, is_principal, self._determine_access_type(ssh), run_id)})
