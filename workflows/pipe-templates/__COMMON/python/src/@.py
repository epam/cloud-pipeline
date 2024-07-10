from pipeline import Logger, TaskStatus
Logger.log_task_event("Task1",
                      "Running python pipeline",
                      status=TaskStatus.SUCCESS)
