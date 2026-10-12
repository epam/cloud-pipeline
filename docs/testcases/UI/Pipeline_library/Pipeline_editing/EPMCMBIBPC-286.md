# Python pipeline validation

Test verifies the tabs, buttons and initial files shown for a pipeline created from the PYTHON template.

**Prerequisites**:
- Perform the [EPMCMBIBPC-285](EPMCMBIBPC-285.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click on the pipeline created in the [EPMCMBIBPC-285](EPMCMBIBPC-285.md) case |  |
| 2 | Click on the pipeline version |  |
| 3 | Select **CODE** tab | The pipeline page appears containing: <li> **DOCUMENTS**, **CODE**, **CONFIGURATION**, **HISTORY**, **STORAGE RULES** tabs <li> **RUN**, gear icon, **GIT REPOSITORY** buttons <li> on the **CODE** tab: `+`, `+ NEW FILE`, `Upload` buttons; a file list: `{pipeline_name}.py`, `config.json`; **Rename**, **Delete** buttons opposite the file list |
