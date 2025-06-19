from logging import Logger

import llama_index.core
from cp_ai.common import create_logger, cp_ai_settings


agents_logger: Logger = create_logger('agent',
                              file_path=cp_ai_settings.cp_agents_logs_file)
llama_index.core.set_global_handler('simple',
                                    logger=agents_logger)
