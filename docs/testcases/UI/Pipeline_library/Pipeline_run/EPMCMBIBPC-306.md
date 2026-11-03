# Cmd template check

Test verifies that a run's **Cmd template** field matches the expected generated command for a Python pipeline.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the pipeline created in the [EPMCMBIBPC-298](EPMCMBIBPC-298.md) case |  |
| 2 | Select version |  |
| 3 | Open **CONFIGURATION** tab |  |
| 4 | Click on **Advanced** header | Check the **Cmd template** field; it should equal `python $SCRIPTS_DIR/src/[main_file]` |
