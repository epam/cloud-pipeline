# Check node reuse for tool launch

Test verifies that relaunching the same tool with the same settings reuses the previous node.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-494](EPMCMBIBPC-494.md) case |  |
| 2 | Perform the [EPMCMBIBPC-501](EPMCMBIBPC-501.md) case |  |
| 3 | Perform the [EPMCMBIBPC-494](EPMCMBIBPC-494.md) case with the same run settings as at step 1 | The node created at step 1 is reused, with a new label equal to the pipeline ID |
