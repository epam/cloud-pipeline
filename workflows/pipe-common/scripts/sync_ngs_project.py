import datetime
import glob
import json
import os

from pipeline import Logger, PipelineAPI, common

DEFAULT_FOLDERS = ['Config', 'Data', 'Images', 'InterOp', 'Logs', 'RTALogs', 'Recipe', 'Thumbnail_Images']
REQUIRED_FIELDS = ['BCLs', 'Results', 'SampleSheet',   'ExperimentType', 'ExperimentName', 'PairedEnd', 'ConfigFile']


class Event(object):
    def __init__(self, machine_run, type, status, message=None):
        self.machine_run = machine_run
        self.type = type
        self.status = status
        self.message = message


class Settings(object):

    def __init__(self, api, project_id, cloud_path, config_path, r_script, db_path_prefix, notify_users,
                 configuration_id, configuration_entry_name):
        self.complete_token_file_name = api.get_preference('ngs.preprocessing.completion.mark.file.default.name')['value']
        self.sample_sheet_glob = api.get_preference('ngs.preprocessing.samplesheet.pattern')['value']
        self.machine_run_class = api.get_preference('ngs.preprocessing.machine.run.metadata.class.name')['value']
        self.machine_run_class_id = int(api.get_preference('ngs.preprocessing.machine.run.metadata.class.id')['value'])
        self.demultiplex_file = api.get_preference('ngs.preprocessing.demultiplex.config.pattern')['value']
        self.config_path = config_path
        self.project_id = int(project_id)
        self.cloud_path = cloud_path
        self.r_script = r_script
        self.db_path_prefix = db_path_prefix
        self.notify_users = notify_users.split(',')
        self.configuration_id = configuration_id
        self.configuration_entry_name = configuration_entry_name


class MachineRun(object):

    def __init__(self, run_folder, machine_run, settings, api, notifications):
        self.run_folder = run_folder
        self.machine_run = machine_run
        self.settings = settings
        self.api = api
        self.notifications = notifications

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
            self.notifications.append(Event(self.machine_run, 'Metadata Created', 'Failure', message=str(e.message)))

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
            return self.handle_sample_sheet_error(machine_run, err, out)
        generated = self.find_sample_sheet(run_folder)
        if not generated:
            return self.handle_sample_sheet_error(machine_run, err, out)
        self.notifications.append(Event(self.machine_run, 'SampleSheet generated', 'Success'))
        return generated

    def handle_sample_sheet_error(self, machine_run, err, out):
        Logger.fail('Failed to generate sample sheet for %s.' % machine_run, task_name=self.machine_run)
        output = ''
        if err:
            output += str(err)
        if out:
            output = output + '\n' + str(out)
            Logger.warn(str(output), task_name=self.machine_run)
        self.notifications.append(Event(self.machine_run, 'SampleSheet generated', 'Failure', message=output))
        return [None]

    def register_metadata(self, machine_run, run_folder, sample_sheets, trigger_run):
        results = self.check_results(run_folder)
        for sample_sheet in sample_sheets:
            id = machine_run + ':' + sample_sheet if sample_sheet else machine_run
            metadata = self.api.find_metadata_entity(self.settings.project_id, id, self.settings.machine_run_class)
            if metadata:
                Logger.info('Machine run %s is already registered.' % machine_run, task_name=self.machine_run)
                return
            config, experiment_name, experiment_type = self.get_configuration(machine_run, run_folder)
            data = {
                'MachineRun': {'type': 'string', 'value': machine_run},
                'BCLs': {'type': 'Path', 'value': os.path.join(self.settings.cloud_path, machine_run)},
                'Results':  self.build_results(machine_run, results, experiment_name),
                'Processed': {'type': 'string', 'value': 'Yes' if results else 'No'},
                'SampleSheet': {'type': 'Path', 'value':
                    os.path.join(self.settings.cloud_path, machine_run, sample_sheet) if sample_sheet else None},
                'ExperimentType': {'type': 'string', 'value': experiment_type},
                'ExperimentName': {'type': 'string', 'value': experiment_name},
                'PairedEnd': {'type': 'string', 'value': 'true'},
                'ConfigFile': {'type': 'Path', 'value': config},
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
            self.notifications.append(Event(self.machine_run, 'Metadata created', 'Success'))
            if trigger_run:
                self.run_analysis(metadata_entity)

    def get_configuration(self, machine_run, run_folder):
        demultiplex_file, demultiplex_config = self.find_demultiplex_config(run_folder)
        if demultiplex_config:
            if len(demultiplex_config) == 1:
                for key, val in demultiplex_config.items():
                    experiment_type = 'single'
                    experiment_name = val[0]
                    config = val[1]
            else:
                experiment_type = 'multiple'
                experiment_name = None
                config = os.path.join(self.settings.cloud_path, machine_run, demultiplex_file)
        else:
            experiment_type = None
            experiment_name = self.machine_run
            config = None
        return config, experiment_name, experiment_type

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

    def run_analysis(self, metadata_entity):
        Logger.info('Launching analysis for machine run %s.' % self.machine_run, task_name=self.machine_run)
        for field in REQUIRED_FIELDS:
            if field not in metadata_entity['data']:
                msg = 'Required metadata field %s in missing for machine run %s. Analysis won\'t be launched,' % \
                      (field, self.machine_run)
                raise RuntimeError(msg)
        try:
            configuration = self.api.load_configuration(int(self.settings.configuration_id))
            target_entry = None
            for entry in configuration['entries']:
                if entry['configName'] == self.settings.configuration_entry_name:
                    target_entry = entry
            if not target_entry:
                raise RuntimeError('Analysis configuration is misconfigured. Failed to find configuration entry %s.' % self.settings.configuration_entry_name)
            configuration['entries'] = [target_entry]
            data = {
                "entitiesIds": [int(metadata_entity['id'])],
                "entries": [target_entry],
                "folderId": self.settings.project_id,
                "id": int(configuration['id']),
                "metadataClass": self.settings.machine_run_class
            }
            result = self.api.run_configuration(data)
            msg = 'Successfully launched analysis for machine run %s. Run id: %d.' % (self.machine_run, result[0]['id'])
            Logger.info(msg, task_name=self.machine_run)
            self.notifications.append(Event(self.machine_run, 'Analysis launch', 'Success', message=msg))
        except Exception as e:
            Logger.warn('Failed to launch analysis for machine run %s. Error: %s' % (self.machine_run, str(e.message)),
                                                                                     task_name=self.machine_run)
            self.notifications.append(Event(self.machine_run, 'Analysis launch', 'Failure', message=str(e.message)))

    def build_results(self, machine_run, results, experiment_name):
        if not results:
            return {'type': 'Path', 'value': os.path.join(self.settings.cloud_path, machine_run, experiment_name)}
        value = json.dumps([os.path.join(self.settings.cloud_path, machine_run, res) for res in results])
        return {'type': 'Array[Path]', 'value': value}

    def find_demultiplex_config(self, run_folder):
        result = {}
        files = [os.path.basename(s) for s in glob.glob(os.path.join(run_folder, self.settings.demultiplex_file))]
        if not files:
            return None, result
        if len(files) > 1:
            Logger.warn('Multiple demultiplex files found for machine run %s: %s. First one will be processed.' %
                        (self.machine_run, ','.join(files)), task_name=self.machine_run)
        demultiplex_config = files[0]
        Logger.info('Reading demultiplex configuration file form %s.' % os.path.join(run_folder, demultiplex_config),
                    task_name=self.machine_run)
        with open(os.path.join(run_folder, demultiplex_config), 'r') as config:
            for line in config.readlines():
                if not line or '\t' not in line:
                    continue
                parts = line.split('\t')
                name = parts[0]
                path = parts[1]
                result[demultiplex_config] = [name, path]
        return demultiplex_config, result


class NGSSync(object):

    def __init__(self, api, settings):
        self.api = api
        self.settings = settings

    def sync_ngs_project(self, folder):
        machine_run_folders = [directory for directory in os.listdir(folder) if os.path.isdir(folder + directory)]
        notifications = []
        for machine_run in machine_run_folders:
            MachineRun(os.path.join(folder, machine_run), machine_run, self.settings, self.api, notifications).sync()
        if notifications and self.settings.notify_users:
            Logger.info('Sending notification to %s.' % ','.join(self.settings.notify_users))
            self.api.create_notification('[Genie test]: NGS metadata synchronization',
                                         self.build_notification_text(notifications),
                                         self.settings.notify_users[0],
                                         copy_users=self.settings.notify_users[1:] if len(
                                             self.settings.notify_users) > 0 else None,
                                         )

    def build_notification_text(self, notifications):
        text = '''Dear user,
        Please find list of the latest metadata synchronization events for NGS data:
        MachineRun\tEvent\tStatus\tMessage\n
        '''
        for event in notifications:
            text += '%s\t%s\t%s\t%s\n' % (event.machine_run, event.type, event.status, event.message if event.message else '')
        text += 'Best regards, \nGenie Support Team'
        return text


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
    notify_users = os.getenv('NGS_SYNC_NOTIFY_USERS', '')
    configuration_id = os.getenv('NGS_SYNC_CONFIG_ID')
    configuration_entry_name = os.getenv('NGS_SYNC_CONFIG_ENTRY_NAME')
    api = PipelineAPI(api_url=os.environ['API'], log_dir='sync_ngs')
    settings = Settings(api, project_id, cloud_path, config_path, r_script, db_path_prefix, notify_users,
                        configuration_id, configuration_entry_name)
    NGSSync(api, settings).sync_ngs_project(folder)


if __name__ == '__main__':
    main()

