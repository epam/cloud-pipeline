import datetime
import glob
import json
import os

from pipeline import Logger, PipelineAPI, common

DEFAULT_FOLDERS = ['Config', 'Data', 'Images', 'InterOp', 'Logs', 'RTALogs', 'Recipe', 'Thumbnail_Images']


class Settings(object):

    def __init__(self, api, project_id, cloud_path, config_path, r_script, db_path_prefix):
        self.complete_token_file_name = api.get_preference('ngs.preprocessing.completion.mark.file.default.name')['value']
        self.sample_sheet_glob = api.get_preference('ngs.preprocessing.samplesheet.pattern')['value']
        self.machine_run_class = api.get_preference('ngs.preprocessing.machine.run.metadata.class.name')['value']
        self.machine_run_class_id = int(api.get_preference('ngs.preprocessing.machine.run.metadata.class.id')['value'])
        self.config_path = config_path
        self.project_id = int(project_id)
        self.cloud_path = cloud_path
        self.r_script = r_script
        self.db_path_prefix = db_path_prefix


class MachineRun(object):

    def __init__(self, run_folder, machine_run, settings, api):
        self.run_folder = run_folder
        self.machine_run = machine_run
        self.settings = settings
        self.api = api

    def sync(self):
        try:
            Logger.info('\nSynchronizing machine run %s.' % self.machine_run, task_name=self.machine_run)
            completion_mark = os.path.join(self.run_folder, self.settings.complete_token_file_name)
            if not os.path.isfile(completion_mark):
                Logger.info('Completion token is not present for machine run %s. Skipping processing.'
                            % self.machine_run, task_name=self.machine_run)
                return
            Logger.info('Completion token is present for machine run %s.' % self.machine_run,
                        task_name=self.machine_run)
            sample_sheets = self.find_sample_sheet(self.run_folder)
            trigger_run = len(sample_sheets) == 0
            if len(sample_sheets) == 0:
                Logger.info('Sample sheet is not present for machine run %s. It will be generated.' %
                            self.machine_run, task_name=self.machine_run)
                sample_sheets = self.generate_sample_sheet(self.machine_run, self.run_folder)
            else:
                Logger.info('%d sample sheets are present for machine run %s.' % (len(sample_sheets),
                                                                                  self.machine_run),
                            task_name=self.machine_run)
            self.register_metadata(self.machine_run, self.run_folder, sample_sheets, trigger_run)
            Logger.success('Finished % synchronization' % self.machine_run, task_name=self.machine_run)
        except Exception as e:
            Logger.fail('An error occurred during machine run processing %s: %s.' % (self.machine_run, str(e.message)),
                        task_name=self.machine_run)

    def find_sample_sheet(self, run_folder):
        return [os.path.basename(s) for s in glob.glob(os.path.join(run_folder, self.settings.sample_sheet_glob))]

    def generate_sample_sheet(self, machine_run, run_folder):
        # run Rscript
        # check results
        command = 'cd %s && Rscript %s %s' % (run_folder, self.settings.r_script,
                                              self.settings.db_path_prefix + machine_run)
        Logger.info('Executing command %s.' % command, task_name=self.machine_run)
        exit_code, out, err = common.execute_cmd_command_and_get_stdout_stderr(command,
                                                                               silent=True)
        if exit_code != 0:
            Logger.fail('Failed to generate sample sheet for %s.' % machine_run, task_name=self.machine_run)
            if err:
                Logger.warn(str(err), task_name=self.machine_run)
            if out:
                Logger.warn(str(out), task_name=self.machine_run)
            return []
        generated = self.find_sample_sheet(run_folder)
        if not generated:
            Logger.fail('Failed to generate sample sheet for %s.' % machine_run, task_name=self.machine_run)
            if err:
                Logger.warn(str(err), task_name=self.machine_run)
            if out:
                Logger.warn(str(out), task_name=self.machine_run)
            return []
        return generated

    def register_metadata(self, machine_run, run_folder, sample_sheets, trigger_run):
        results = self.check_results(run_folder)
        for sample_sheet in sample_sheets:
            id = machine_run + ':' + sample_sheet if sample_sheet else machine_run
            metadata = self.api.find_metadata_entity(self.settings.project_id, id, self.settings.machine_run_class)
            if metadata:
                Logger.info('Machine run %s is already registered.' % machine_run, task_name=self.machine_run)
                return
            data = {
                'MachineRun': {'type': 'string', 'value': machine_run},
                'BCLs': {'type': 'Path', 'value': os.path.join(self.settings.cloud_path, machine_run)},
                'Results':  self.build_results(machine_run, results),
                'Processed': {'type': 'string', 'value': 'Yes' if results else 'No'},
                'SampleSheet': {'type': 'Path', 'value':
                    os.path.join(self.settings.cloud_path, machine_run, sample_sheet) if sample_sheet else None},
                'ExperimentType': {'type': 'string', 'value': 'single'},
                'PairedEnd': {'type': 'string', 'value': 'true'},
                'ConfigFile': {'type': 'Path', 'value': self.settings.config_path},
                'SequenceDate': {'type': 'Date', 'value': self.parse_seq_date(machine_run)}
            }
            entity = {
                'parentId': self.settings.project_id,
                'classId': self.settings.machine_run_class_id,
                'className': self.settings.machine_run_class,
                'externalId': id,
                'entityName': id,
                'data': data
            }
            metadata_entity = self.api.save_metadata_entity(entity)
            Logger.info('Created metadata for machine run %s with sample sheet %s.' % (machine_run, sample_sheet),
                        task_name=self.machine_run)
            if trigger_run:
                self.run_analysis(metadata_entity)

    def check_results(self, run_folder):
        res = []
        for dir in os.listdir(run_folder):
            # Consider all non-default folders to be results
            if os.path.isdir(os.path.join(run_folder, dir)) and dir not in DEFAULT_FOLDERS:
                res.append(dir)
        return res

    def parse_seq_date(self, machine_run):
        if '_' in machine_run:
            try:
                text = machine_run.split('_')[0]
                return datetime.datetime.strptime(text, '%y%m%d').strftime('%Y-%m-%d %H:%M:%S.%f')
            except Exception as e:
                return None
        return None

    def run_analysis(self, metadata_entities):
        pass

    def build_results(self, machine_run, results):
        # {'type': 'Path', 'value': self.build_results(machine_run, results)}
        if not results:
            return {'type': 'Path', 'value': os.path.join(self.settings.cloud_path, 'results')}
        value = json.dumps([os.path.join(self.settings.cloud_path,  res) for res in results])
        return { 'type': 'Array[Path]', 'value': value}



class NGSSync(object):

    def __init__(self, api, settings):
        self.api = api
        self.settings = settings

    def sync_ngs_project(self, folder):
        machine_run_folders = [directory for directory in os.listdir(folder) if os.path.isdir(folder + directory)]
        for machine_run in machine_run_folders:
            MachineRun(os.path.join(folder, machine_run), machine_run, self.settings, self.api).sync()


def get_required_env_var(name):
    val = os.getenv(name)
    if val is None:
        raise RuntimeError('Required environment variable "%s" is not set.' % name)
    return val


def main():
    folder = get_required_env_var('NGS_SYNC_FOLDER')
    project_id = get_required_env_var('NGS_SYNC_PROJECT_ID')
    cloud_path = get_required_env_var('NGS_SYNC_CLOUD_PATH')
    config_path = get_required_env_var('NGS_SYNC_CONFIG_PATH')
    r_script = get_required_env_var('NGS_SYNC_R_SCRIPT')
    db_path_prefix = os.getenv('NGS_SYNC_DB_PATH_PREFIX', '')
    api = PipelineAPI(api_url=os.environ['API'], log_dir='sync_ngs')
    settings = Settings(api, project_id, cloud_path, config_path, r_script, db_path_prefix)
    NGSSync(api, settings).sync_ngs_project(folder)


if __name__ == '__main__':
    main()

