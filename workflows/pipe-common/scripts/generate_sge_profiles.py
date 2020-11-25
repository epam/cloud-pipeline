import argparse
import logging
import os

from pipeline import suffix_non_unique, build_environment_profiles

_LOGGING_FORMAT = '%(asctime)s:%(levelname)s: %(message)s'


def generate_sge_profiles():
    common_profile, profiles = build_environment_profiles(prefixes=['CP_CAP_SGE', 'CP_CAP_AUTOSCALE'])

    for profile_index, profile in profiles.items():
        if 'CP_CAP_SGE_QUEUE_NAME' not in profile:
            if 'CP_CAP_AUTOSCALE_INSTANCE_TYPE' in profile:
                profile['CP_CAP_SGE_QUEUE_NAME'] = '{}.q'.format(profile['CP_CAP_AUTOSCALE_INSTANCE_TYPE'])
            elif 'CP_CAP_AUTOSCALE_HYBRID' in profile:
                profile['CP_CAP_SGE_QUEUE_NAME'] = '{}.family.q'.format(
                    profile.get('CP_CAP_AUTOSCALE_HYBRID_FAMILY', os.environ['instance_size']))
            else:
                profile['CP_CAP_SGE_QUEUE_NAME'] = '{}.q'.format(os.environ['instance_size'])
        if 'CP_CAP_AUTOSCALE' not in profile:
            profile['CP_CAP_AUTOSCALE'] = 'false'

    profile_indexes = profiles.keys()
    profile_queues = [profiles[index]['CP_CAP_SGE_QUEUE_NAME'] for index in profile_indexes]

    def append_suffix(item, suffix):
        return '{}.{}.q'.format(item if not item.endswith('.q') else item[:-2], suffix)

    unique_profile_queues = suffix_non_unique(profile_queues, suffix_func=append_suffix)

    for profile_index, profile in profiles.items():
        profile['CP_CAP_SGE_QUEUE_NAME'] = unique_profile_queues[profile_indexes.index(profile_index)]
        profile['CP_CAP_SGE_HOSTLIST_NAME'] = '@{}'.format(profile['CP_CAP_SGE_QUEUE_NAME'])

    common_profile['CP_CAP_SGE_STATIC'] = 'true'
    common_profile['CP_CAP_AUTOSCALE_TASK'] = 'GridEngineAutoscaling'

    logging.info('Generating sge profile scripts...')
    profiles_directory = os.getenv('CP_CAP_SCRIPTS_DIR', '/common/cap_scripts')
    complete_profiles = {}
    if os.getenv('CP_CAP_SGE_DISABLE_DEFAULT_QUEUE', 'false').lower() not in ['true', 'yes']:
        complete_profiles['common'] = common_profile
    complete_profiles.update(profiles)
    for profile_index, profile in complete_profiles.items():
        logging.info('Profile %s environment:', profile_index)
        for key, value in profile.items():
            logging.info('%s=%s', key, value)
        profile_script_name = 'sge_profile_{}.sh'.format(profile['CP_CAP_SGE_QUEUE_NAME'])
        profile_script_path = os.path.join(profiles_directory, profile_script_name)
        with open(profile_script_path, 'w') as f:
            for key, value in profile.items():
                f.write('export {}="{}"\n'.format(key, value))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Generates sge profile scripts.')
    parser.add_argument('-v', '--log-level',
                        default=logging.getLevelName(logging.ERROR),
                        help='Logging level: CRITICAL, ERROR, WARNING, INFO or DEBUG.')
    args = parser.parse_args()

    logging.basicConfig(level=args.log_level, format=_LOGGING_FORMAT)

    generate_sge_profiles()
