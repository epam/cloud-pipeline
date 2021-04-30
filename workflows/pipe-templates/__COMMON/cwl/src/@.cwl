cwlVersion: v1.0
class: CommandLineTool
inputs:
  message:
    type: string
outputs: []
baseCommand: pipe_log
arguments: [SUCCESS, $(inputs.message), "Task1"]
