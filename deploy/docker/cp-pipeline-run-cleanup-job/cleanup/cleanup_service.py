# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import logging
from datetime import datetime, timedelta, timezone

from cleanup.state import read_last_processed_date, write_last_processed_date

logger = logging.getLogger(__name__)

_OUTPUT_PARAM_TYPE = 'output'


def _get_output_paths(run, extra_names):
    """Extract output storage paths from a run's parameters."""
    paths = []
    params = run.get('pipelineRunParameters') or []
    for param in params:
        name = param.get('name', '')
        ptype = param.get('type', '')
        value = param.get('value') or ''
        if not value:
            continue
        if ptype.lower() == _OUTPUT_PARAM_TYPE or name in extra_names:
            paths.append(value)
    return paths


def _strip_storage_prefix(path, storage_path_mask):
    """Remove the storage root prefix from a full path to get relative path."""
    if storage_path_mask and path.startswith(storage_path_mask):
        rel = path[len(storage_path_mask):]
        return rel.lstrip('/')
    return path


def _chunks(lst, size):
    for i in range(0, len(lst), size):
        yield lst[i:i + size]


class CleanupService:

    def __init__(self, config, api_client):
        self._cfg = config
        self._api = api_client

    def run(self):
        cfg = self._cfg
        if cfg.dry_run:
            logger.info('DRY RUN MODE — no data will be deleted or archived')

        cutoff = datetime.now(tz=timezone.utc) - timedelta(days=cfg.cleanup_days_after)
        end_date_to = cutoff.strftime('%Y-%m-%d %H:%M:%S.000')
        start_date_from = read_last_processed_date(cfg.state_file)
        logger.info(
            'Starting cleanup: statuses=%s, window=[%s, %s], dry_run=%s',
            cfg.cleanup_statuses, start_date_from or 'beginning', end_date_to, cfg.dry_run,
        )

        collected_ids = []
        runs_processed = 0
        paths_deleted = 0
        paths_failed = 0

        page = 1
        while True:
            try:
                runs, total = self._api.filter_runs_by_statuses(
                    cfg.cleanup_statuses, end_date_to, page, cfg.page_size, start_date_from,
                )
            except Exception:
                logger.exception('Failed to fetch runs page %d', page)
                break

            for run in runs:
                run_id = run.get('id')
                collected_ids.append(run_id)
                output_paths = _get_output_paths(run, set(cfg.output_param_names))

                for path in output_paths:
                    try:
                        storage = self._api.find_datastorage(path)
                    except Exception:
                        logger.warning('Could not find storage for path %s (run %s)', path, run_id)
                        paths_failed += 1
                        continue

                    if not storage:
                        logger.warning('No storage found for path %s (run %s)', path, run_id)
                        paths_failed += 1
                        continue

                    storage_id = storage.id
                    relative_path = _strip_storage_prefix(path, storage.path)
                    item_type = 'Folder' if path.endswith('/') else 'File'
                    items = [{'path': relative_path, 'type': item_type}]

                    if cfg.dry_run:
                        logger.info(
                            '[DRY RUN] Would delete %s %s from storage %s (run %s)',
                            item_type, relative_path, storage_id, run_id,
                        )
                    else:
                        try:
                            self._api.delete_datastorage_items(storage_id, items, cfg.delete_data_totally)
                            logger.info(
                                'Deleted %s %s from storage %s (run %s)',
                                item_type, relative_path, storage_id, run_id,
                            )
                            paths_deleted += 1
                        except Exception:
                            logger.exception(
                                'Failed to delete %s %s from storage %s (run %s)',
                                item_type, relative_path, storage_id, run_id,
                            )
                            paths_failed += 1

            runs_processed += len(runs)
            logger.info('Processed %d / %d runs so far', runs_processed, total)

            if runs_processed >= total or not runs:
                break
            page += 1

        runs_archived = 0
        if cfg.archive_runs and collected_ids:
            logger.info('Archiving %d runs by IDs...', len(collected_ids))
            for batch in _chunks(collected_ids, cfg.archive_batch_size):
                if cfg.dry_run:
                    logger.info('[DRY RUN] Would archive run IDs: %s', batch)
                else:
                    try:
                        self._api.archive_runs_by_ids(batch)
                        logger.info('Archived %d runs', len(batch))
                        runs_archived += len(batch)
                    except Exception:
                        logger.exception('Failed to archive batch of %d runs', len(batch))

        if not cfg.dry_run:
            write_last_processed_date(cfg.state_file, end_date_to)

        logger.info(
            'Cleanup complete: runs_processed=%d, paths_deleted=%d, paths_failed=%d, runs_archived=%d',
            runs_processed, paths_deleted, paths_failed, runs_archived,
        )
