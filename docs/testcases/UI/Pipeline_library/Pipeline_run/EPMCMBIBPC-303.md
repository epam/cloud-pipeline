# [MANUAL] Validation instance type selection

Test verifies that selecting a node type on the Configuration tab updates the displayed CPU/RAM labels while keeping the entered disk size.

**Prerequisites**:
- Create a pipeline
- Open its Configuration tab

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | In the **Exec environment** section, input a positive natural number into the **Disk (Gb)** field |  |
| 2 | Click the dropdown list near the **Node type** label |  |
| 3 | In the appeared list select `m4.xlarge` | The node configuration labels show 4 CPU, 16 RAM, [the disk size entered at step 1] Gb |
| 4 | Repeat step 2 |  |
| 5 | In the appeared list select `m5.large` | The node configuration labels show 2 CPU, 8 RAM, [the disk size entered at step 1] Gb |
