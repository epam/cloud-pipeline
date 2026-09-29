# Set project parameters

Test verifies setting project-level attributes pointing at the reference panel, BWA folder and output storage.

**Prerequisites**:
- Perform the [EPMCMBIBPC-1411](EPMCMBIBPC-1411.md) and [EPMCMBIBPC-1519](EPMCMBIBPC-1519.md) cases

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the folder created at step 4 of the EPMCMBIBPC-1411 case |  |
| 2 | If attributes are not displayed: click the button for customizing the display of additional panels (in the upper-right corner) -> click **Attributes** in the list that appears |  |
| 3 | Click **+ Add** in the **Attributes** panel |  |
| 4 | Enter the value `Exome_Panel` into the **Key** field |  |
| 5 | Enter the path to the `gencode.v27.genes.bed` file created at step 20 of the EPMCMBIBPC-1519 case into the **Value** field |  |
| 6 | Click **Add** |  |
| 7 | Repeat steps 3-6 with **Key**: `GRCh38_BWA`, **Value**: the path to the `bwa` folder created at step 24 of the EPMCMBIBPC-1519 case |  |
| 8 | Repeat steps 3-6 with **Key**: `Project_Output`, **Value**: the path to the storage created at step 4 of the EPMCMBIBPC-1519 case, in the form `{path_to_storage}/${RUN_ID}` |  |
