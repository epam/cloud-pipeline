# [MANUAL] Validation of "AND" operand

Test verifies that an `and`-combined query across `node.disk`, `node.type` and `run.status` narrows results to matches on all three.

**Prerequisites**:
- Several pipelines launched with different disk size values and on different instance types, completed with different statuses

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `node.disk>20 and node.type="c4.l*" and run.status=FAILURE` into the search field and press **Enter** |  |
| 3 | Click on a pipeline |  |
| 4 | Click the **Instance** section | A pipeline is displayed that was launched on a `c4.large`-type instance, with a disk size greater than 20Gb, and completed with `FAILURE` status |
