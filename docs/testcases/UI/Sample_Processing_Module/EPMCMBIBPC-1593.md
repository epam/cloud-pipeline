# Validation of set parameters from project metadata

Test verifies binding configuration parameters to project-level attributes via the `project.` expression prefix.

**Prerequisites**:
- Perform the [EPMCMBIBPC-1588](EPMCMBIBPC-1588.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the detached configuration created at step 5 of the [EPMCMBIBPC-1588](EPMCMBIBPC-1588.md) case |  |
| 2 | Click the field opposite the **REFERENCE_GENOME_PATH** parameter |  |
| 3 | Enter `project.` | A list appears containing: type, GRCh38_BWA, Exome_Panel, Project_Output |
| 4 | Select `GRCh38_BWA` in the list that appears |  |
| 5 | Click the field opposite the **PANEL** parameter |  |
| 6 | Enter `project.` | A list appears containing: type, GRCh38_BWA, Exome_Panel, Project_Output |
| 7 | Select `Exome_Panel` in the list that appears |  |
| 8 | Click the field opposite the **RESULT_DIR** parameter |  |
| 9 | Enter `project.` | A list appears containing: type, GRCh38_BWA, Exome_Panel, Project_Output |
| 10 | Select `Project_Output` in the list that appears |  |
| 11 | Click **Save** |  |
