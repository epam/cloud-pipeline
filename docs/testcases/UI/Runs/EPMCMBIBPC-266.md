# [MANUAL] Navigate to node from pipeline log

Test verifies that the IP hyperlink in a finished run's Instance section opens that run's node, with a matching IP.

**Prerequisites**:
- A pipeline that has just finished; the node it ran on must still exist

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-113](EPMCMBIBPC-113.md) case |  |
| 2 | Click the **Instance** label |  |
| 3 | Click the hyperlink near the **IP** label | The description of the node the pipeline ran on opens; the IPs should match |
