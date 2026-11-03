# [MANUAL] Validation of file-folder siblings in the Path parameter pop-up

Test verifies that the path-parameter browse pop-up lets the user select a file or a folder even when both share the same name at one hierarchy level.

**Prerequisites**:
- Storage with a file and a folder of the same name at one hierarchy level
- Created pipeline

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the pipeline |  |
| 2 | Open **Configuration** tab |  |
| 3 | Add any Path parameter |  |
| 4 | Click the browse button to select a path |  |
| 5 | Navigate to the storage hierarchy level from the prerequisites |  |
| 6 | Select the file from the prerequisites | The file is selected only |
| 7 | Deselect the file |  |
| 8 | Select the folder from the prerequisites | The folder is selected only |
