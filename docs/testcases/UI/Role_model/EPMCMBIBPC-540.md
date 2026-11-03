# Validate of set read allow automatically if any other permission was allowed

Test verifies that allowing the Write permission automatically checks Read as well.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-539](EPMCMBIBPC-539.md) case |  |
| 2 | Click on the checkbox in the **Allow** column opposite the **Write** permission | The checkbox in the **Allow** column opposite the **Read** permission becomes checked |
