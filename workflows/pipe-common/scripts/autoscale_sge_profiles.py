import argparse
import logging
import os

from pipeline import execute_command, suffix_non_unique, build_environment_profiles

_LOGGING_FORMAT = '%(asctime)s:%(levelname)s: %(message)s'


def launch_sge_autoscalers(command):
    common_profile, profiles = build_environment_profiles(prefix='CP_CAP_AUTOSCALE')

    for profile_index, profile in profiles.items():
        if 'CP_CAP_AUTOSCALE_QUEUE' not in profile:
            if 'CP_CAP_AUTOSCALE_INSTANCE_TYPE' in profile:
                profile['CP_CAP_AUTOSCALE_QUEUE'] = '{}.q'.format(profile['CP_CAP_AUTOSCALE_INSTANCE_TYPE'])
            elif 'CP_CAP_AUTOSCALE_HYBRID' in profile:
                profile['CP_CAP_AUTOSCALE_QUEUE'] = '{}.family.q'.format(
                    profile.get('CP_CAP_AUTOSCALE_HYBRID_FAMILY', os.environ['instance_size']))
            else:
                profile['CP_CAP_AUTOSCALE_QUEUE'] = '{}.q'.format(os.environ['instance_size'])
        if 'CP_CAP_AUTOSCALE_STATIC' not in profile:
            profile['CP_CAP_SGE_MASTER_CORES'] = '0'

    profile_indexes = profiles.keys()
    profile_queues = [profiles[index]['CP_CAP_AUTOSCALE_QUEUE'] for index in profile_indexes]

    def append_suffix(item, suffix):
        return '{}.{}.q'.format(item if not item.endswith('.q') else item[:-2], suffix)

    unique_profile_queues = suffix_non_unique(profile_queues, suffix_func=append_suffix)

    for profile_index, profile in profiles.items():
        profile['CP_CAP_AUTOSCALE_QUEUE'] = unique_profile_queues[profile_indexes.index(profile_index)]
        profile['CP_CAP_AUTOSCALE_HOSTLIST'] = '@{}'.format(profile['CP_CAP_AUTOSCALE_QUEUE'])
        profile['CP_CAP_SGE_QUEUE_NAME'] = profile['CP_CAP_AUTOSCALE_QUEUE']
        profile['CP_CAP_SGE_HOSTLIST_NAME'] = profile['CP_CAP_AUTOSCALE_HOSTLIST']

    common_profile['CP_CAP_AUTOSCALE_TASK'] = 'GridEngineAutoscaling'

    logging.info('Executing commands for each autoscaling profile...')
    complete_profiles = {'common': common_profile}
    complete_profiles.update(profiles)
    for profile_index, profile in complete_profiles.items():
        logging.info('Profile %s environment:', profile_index)
        for key, value in profile.items():
            logging.info('%s=%s', key, value)
        output = execute_command(command, environment=profile, collect_output=True) or ''
        logging.info('Profile %s output: %s', profile_index, output.strip() or '-')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Executes given command for each autoscaling profile.')
    parser.add_argument('-c', '--command',
                        required=True,
                        default=logging.getLevelName(logging.ERROR),
                        help='Command to be executed for each environment profile.')
    parser.add_argument('-v', '--log-level',
                        default=logging.getLevelName(logging.ERROR),
                        help='Logging level: CRITICAL, ERROR, WARNING, INFO or DEBUG.')
    args = parser.parse_args()

    logging.basicConfig(level=args.log_level, format=_LOGGING_FORMAT)

    launch_sge_autoscalers(args.command)
