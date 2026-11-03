# [MANUAL] Export log file content validation

Test verifies that the exported log file's content matches the console, with local timezone timestamps.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Complete the [EPMCMBIBPC-1555](EPMCMBIBPC-1555.md) case |  |
| 2 | Open the log file in a text editor | <li> all log information from the console is present in the file <li> the time is adjusted to your timezone <li> a newline character may appear at the end of lines |
