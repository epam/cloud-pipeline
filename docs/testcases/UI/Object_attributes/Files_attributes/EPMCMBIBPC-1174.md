# Add several keys to Object_attributes/Files_attributes

*Note: For Azure, step 2 should be skipped*

Test verifies adding file attributes with various key/value string shapes, including a space-only pair being rejected.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1172](EPMCMBIBPC-1172.md) case with a **Key** that is a short string without spaces and a **Value** that is a short string without spaces | All values are displayed correctly |
| 2 | Perform the [EPMCMBIBPC-1172](EPMCMBIBPC-1172.md) case with a **Key** that is a very long string with spaces and a **Value** that is a one-symbol string (this item may be unavailable on a specific platform, e.g. Azure) | All values are displayed correctly |
| 3 | Perform the [EPMCMBIBPC-1172](EPMCMBIBPC-1172.md) case with a **Key** that is a one-symbol string and a **Value** that is a string with spaces | All values are displayed correctly |
| 4 | Perform the [EPMCMBIBPC-1172](EPMCMBIBPC-1172.md) case with a **Key** that is a space character and a **Value** that is a space character | The message "Enter key" appears |
