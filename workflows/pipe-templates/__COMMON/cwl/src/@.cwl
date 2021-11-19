cwlVersion: v1.0
class: CommandLineTool
inputs:
  message:
    type: string
outputs:
  result:
    type: stdout
baseCommand: echo
arguments: [$(inputs.message)]
stdout: result.txt

