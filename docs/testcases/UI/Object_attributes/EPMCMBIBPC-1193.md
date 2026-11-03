# [Negative] Try to enter wrong key-values of attributes

Test verifies that adding an attribute with an empty key, or an empty value, is rejected for a pipeline, folder, and data storage.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-858](EPMCMBIBPC-858.md) case (for a pipeline, folder, and data storage) with an empty **Key** and an empty **Value** | The message "Enter key" appears |
| 2 | Perform the [EPMCMBIBPC-858](EPMCMBIBPC-858.md) case (for a pipeline, folder, and data storage) with an empty **Key** and a non-empty **Value** | The message "Enter key" appears |
| 3 | Perform the [EPMCMBIBPC-858](EPMCMBIBPC-858.md) case (for a pipeline, folder, and data storage) with a non-empty **Key** and an empty **Value** | The message "Enter value" appears |
