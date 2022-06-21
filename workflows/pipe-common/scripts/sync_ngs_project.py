import argparse
import datetime
import glob
import os

from pipeline import Logger, PipelineAPI


DEFAULT_FOLDERS = ['Config', 'Data', 'Images', 'InterOp', 'Logs', 'RTALogs', 'Recipe', 'Thumbnail_Images']


class Settings(object):

    def __init__(self, api, project_id, cloud_path, config_path):
        self.complete_token_file_name = api.get_preference('ngs.preprocessing.completion.mark.file.default.name')['value']
        self.sample_sheet_glob = api.get_preference('ngs.preprocessing.samplesheet.pattern')['value']
        self.machine_run_class = api.get_preference('ngs.preprocessing.machine.run.metadata.class.name')['value']
        self.machine_run_class_id = int(api.get_preference('ngs.preprocessing.machine.run.metadata.class.id')['value'])
        self.config_path = config_path
        self.project_id = int(project_id)
        self.cloud_path = cloud_path


class NGSSync(object):

    def __init__(self, api, settings):
        self.api = api
        self.settings = settings

    def sync_ngs_project(self, folder):
        machine_run_folders = [directory for directory in os.listdir(folder) if os.path.isdir(folder + directory)]
        for machine_run in machine_run_folders:
            self.process_machine_run(folder, machine_run)

    def process_machine_run(self, folder, machine_run):
        try:
            run_folder = os.path.join(folder, machine_run)
            Logger.info('\nSynchronizing folder %s' % run_folder)
            completion_mark = os.path.join(run_folder, self.settings.complete_token_file_name)
            if not os.path.isfile(completion_mark):
                Logger.info('Completion token is not present for machine run %s. Skipping processing' % machine_run)
                return
            Logger.info('Completion token is present for machine run %s' % machine_run)
            sample_sheets = [os.path.basename(s) for s in glob.glob(os.path.join(run_folder, self.settings.sample_sheet_glob))]
            trigger_run = len(sample_sheets) == 0
            if len(sample_sheets) == 0:
                Logger.info('Sample sheet is not present for machine run %s. It will be generated.' % machine_run)
                sample_sheets = [self.generate_sample_sheet(machine_run)]
            else:
                Logger.info('%d sample sheets are present for machine run %s' % (len(sample_sheets), machine_run))
            self.register_metadata(machine_run, run_folder, sample_sheets, trigger_run)
        except Exception as e:
            Logger.warn('An error occurred during machine run processing %s: %s' % (machine_run, str(e.message)))

    def generate_sample_sheet(self, machine_run):
        # run Rscript
        # check results
        return None

    def register_metadata(self, machine_run, run_folder, sample_sheets, trigger_run):
        results_present = self.check_results(run_folder)
        for sample_sheet in sample_sheets:
            id = machine_run + ':' + sample_sheet if sample_sheet else machine_run
            result = self.api.find_metadata_entity(self.settings.project_id, id, self.settings.machine_run_class)
            if result:
                Logger.info('Machine run %s is already registered' % machine_run)
                return
            data = {
                'MachineRun': {'type': 'string', 'value': machine_run},
                'BCLs': {'type': 'Path', 'value': os.path.join(self.settings.cloud_path, machine_run)},
                'Results': {'type': 'Path', 'value': os.path.join(self.settings.cloud_path, machine_run, 'results')},
                'Processed': {'type': 'string', 'value': 'Yes' if results_present else 'No'},
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
            Logger.info('Created metadata for machine run %s with sample sheet %s' % (machine_run, sample_sheet))
            if trigger_run:
                self.run_analysis(metadata_entity)

    def check_results(self, run_folder):
        for dir in os.listdir(run_folder):
            # Consider all non-default folders to be results
            if os.path.isdir(os.path.join(run_folder, dir)) and dir not in DEFAULT_FOLDERS:
                return True
        return False

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
    api = PipelineAPI(api_url=os.environ['API'], log_dir='sync_ngs')
    settings = Settings(api, project_id, cloud_path, config_path)
    NGSSync(api, settings).sync_ngs_project(folder)


if __name__ == '__main__':
    main()

