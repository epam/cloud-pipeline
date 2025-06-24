from cp_ai.common.logger import create_logger
from cp_ai.common.settings import cp_ai_settings

cp_api_logger = create_logger('cloud-pipeline-api', file_path=cp_ai_settings.cp_restapi_logs_file)
