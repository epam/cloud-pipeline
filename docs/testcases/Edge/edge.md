# Edge testcases

Testcases below are prepared for validating endpoints URLs for runs with different options.  
Each case can be performed by the following scenario:

- the run is being launched by the command from the "**Launch command**" column
- after the run initialization is over, shall be checked available run endpoints URLs/paths according to the "**Expected results**" column

## 1. Centos NoMachine case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/centos`<br />**Sub Domain**: No<br />**NoMachine**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/centos:latest \\<br />-cmd 'sleep infinity' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `/pipeline-<RUN-ID>-8089-0` |

## 2. Ubuntu:16.04 NoMachine case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/ubuntu:16.04`<br />**Sub Domain**: No<br />**NoMachine**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/ubuntu:16.04 \\<br />-cmd 'sleep infinity' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `/pipeline-<RUN-ID>-8089-0` |

## 3. Ubuntu:18.04 NoMachine case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/ubuntu:18.04`<br />**Sub Domain**: No<br />**NoMachine**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/ubuntu:18.04 \\<br />-cmd 'sleep infinity' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `/pipeline-<RUN-ID>-8089-0` |

## 4. RStudio case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Sub Domain**: No | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' | `/pipeline-<RUN-ID>-8788-0` |

## 5. RStudio NoMachine case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Sub Domain**: No<br />**NoMachine**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `/pipeline-<RUN-ID>-8788-0`<br />`/pipeline-<RUN-ID>-8089-0` |

## 6. RStudio friendly-URL case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf`<br />**Sub Domain**: No | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' | `/asdf` |

## 7. RStudio friendly-URL NoMachine case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf`<br />**Sub Domain**: No<br />**NoMachine**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `/asdf-RStudio`<br />`/asdf-NoMachine` |

## 8. RStudio host case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf.com`<br />**Sub Domain**: No | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf.com" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' | `https://asdf.com:31081/` |

## 9. RStudio host NoMachine case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf.com`<br />**Sub Domain**: No<br />**NoMachine**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf.com" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `https://asdf.com:31081/Rstudio`<br />`https://asdf.com:31081/NoMachine` |

## 10. RStudio host/friendly-URL case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf.com/asdf`<br />**Sub Domain**: No | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf.com/asdf" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' | `https://asdf.com:31081/asdf` |

## 11. RStudio host/friendly-URL NoMachine case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf.com/asdf`<br />**Sub Domain**: No<br />**NoMachine**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf.com/asdf" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `https://asdf.com:31081/asdf-RStudio`<br />`https://asdf.com:31081/asdf-NoMachine` |

## 12. Jupyter case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/jupyter-lab`<br />**Sub Domain**: No | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/jupyter-lab:latest \\<br />-cmd '/start.sh --lab' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' | `/pipeline-<RUN-ID>-8888-0` |

## 13. Jupyter friendly-URL case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/jupyter-lab`<br />**Friendly URL**: `asdf`<br />**Sub Domain**: No | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/jupyter-lab:latest \\<br />-cmd '/start.sh --lab' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' | `/asdf` |

## 14. Jupyter friendly-URL NoMachine case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/jupyter-lab`<br />**Friendly URL**: `asdf`<br />**Sub Domain**: No<br />**NoMachine**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/jupyter-lab:latest \\<br />-cmd '/start.sh --lab' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `/asdf-JupyterLab`<br />`/asdf-NoMachine`<br /><br />\* NoMachine service is not available on port 8089 caused by the anaconda's own dbus installation. But the endpoint URL path shall have the view as described. |

## 15. Centos Spark case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/centos`<br />**Sub Domain**: No<br />**Apache Spark cluster**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/centos:latest \\<br />-cmd 'sleep infinity' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_SPARK 'boolean?true' | `/pipeline-<RUN-ID>-8088-1000` |

## 16. Centos NoMachine Spark case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/centos`<br />**Sub Domain**: No<br />**NoMachine**: Enabled<br />**Apache Spark cluster**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/centos:latest \\<br />-cmd 'sleep infinity' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_SPARK 'boolean?true' \\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `/pipeline-<RUN-ID>-8088-1000`<br />`/pipeline-<RUN-ID>-8089-0` |

## 17. RStudio Spark case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Sub Domain**: No<br />**Apache Spark cluster**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_SPARK 'boolean?true' | `/pipeline-<RUN-ID>-8088-1000`<br />`/pipeline-<RUN-ID>-8788-0` |

## 18. RStudio NoMachine Spark case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Sub Domain**: No<br />**NoMachine**: Enabled<br />**Apache Spark cluster**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_SPARK 'boolean?true'\\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `/pipeline-<RUN-ID>-8788-0`<br />`/pipeline-<RUN-ID>-8088-1000`<br />`/pipeline-<RUN-ID>-8089-0` |

## 19. RStudio friendly-URL Spark case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf`<br />**Sub Domain**: No<br />**Apache Spark cluster**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_SPARK 'boolean?true' | `/asdf-SparkUI`<br />`/asdf-RStudio` |

## 20. RStudio friendly-URL NoMachine Spark case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf`<br />**Sub Domain**: No<br />**NoMachine**: Enabled<br />**Apache Spark cluster**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_SPARK 'boolean?true' \\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `/asdf-RStudio`<br />`/asdf-SparkUI`<br />`/asdf-NoMachine` |

## 21. RStudio host Spark case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf.com`<br />**Sub Domain**: No<br />**Apache Spark cluster**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf.com" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_SPARK 'boolean?true' | `https://asdf.com:31081/SparkUI`<br />`https://asdf.com:31081/RStudio` |

## 22. RStudio host NoMachine Spark case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf.com`<br />**Sub Domain**: No<br />**NoMachine**: Enabled<br />**Apache Spark cluster**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf.com" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_SPARK 'boolean?true' \\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `https://asdf.com:31081/RStudio`<br />`https://asdf.com:31081/SparkUI`<br />`https://asdf.com:31081/NoMachine` |

## 23. RStudio host/friendly-URL Spark case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf.com/asdf`<br />**Sub Domain**: No<br />**Apache Spark cluster**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf.com/asdf" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_SPARK 'boolean?true' | `https://asdf.com:31081/asdf-SparkUI`<br />`https://asdf.com:31081/asdf-RStudio` |

## 24. RStudio host/friendly-URL NoMachine Spark case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf.com/asdf`<br />**Sub Domain**: No<br />**NoMachine**: Enabled<br />**Apache Spark cluster**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf.com/asdf" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' \\<br />CP_CAP_SPARK 'boolean?true' \\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `https://asdf.com:31081/asdf-RStudio`<br />`https://asdf.com:31081/asdf-SparkUI`<br />`https://asdf.com:31081/asdf-NoMachine` |

## 25. RStudio Sub-Domain case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Sub Domain**: Yes | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' | `https://pipeline-<RUN-ID>-8788-0.jobs.cloud-pipeline.com:31081` |

## 26. RStudio Sub-Domain friendly-URL case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `rstudio`<br />**Sub Domain**: Yes | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "rstudio" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' | `https://rstudio.jobs.cloud-pipeline.com:31081` |

## 27. RStudio Sub-Domain host case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf.com`<br />**Sub Domain**: Yes | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf.com" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' | `https://asdf.com:31081` |

## 28. RStudio Sub-Domain host/friendly-URL case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf.com/asdf`<br />**Sub Domain**: Yes | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf.com/asdf" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None' | `https://asdf.com:31081/asdf` |

## 29. RStudio Sub-Domain NoMachine case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Sub Domain**: Yes<br />**NoMachine**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />-- CP_CAP_LIMIT_MOUNTS 'None'\\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `https://pipeline-<RUN-ID>-8788-0.jobs.cloud-pipeline.com:31081`<br />`/pipeline-<RUN-ID>-8089-0` |

## 30. RStudio Sub-Domain friendly-URL NoMachine case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf`<br />**Sub Domain**: Yes<br />**NoMachine**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None'\\<br />CP_CAP_DESKTOP_NM 'boolean?true' | `https://asdf-rstudio.jobs.cloud-pipeline.com:31081`<br />`asdf-NoMachine` |

## 31. RStudio Sub-Domain friendly-URL NoMachine Spark case

| Specific options | Launch command | Expected results (endpoint URLs or paths) |
| --- | --- | --- |
| **Docker image**: `library/rstudio`<br />**Friendly URL**: `asdf`<br />**Sub Domain**: Yes<br />**NoMachine**: Enabled<br />**Apache Spark cluster**: Enabled | pipe run \\<br />-y \\<br />-id 50 \\<br />-it m5.large \\<br />-di library/rstudio:latest \\<br />-cmd '/start.sh' \\<br />-t 0 \\<br />-pt on-demand \\<br />-r 1 \\<br />--friendly-url "asdf" \\<br />-- CP_CAP_LIMIT_MOUNTS 'None'\\<br />CP_CAP_DESKTOP_NM 'boolean?true' \\<br />CP_CAP_SPARK 'boolean?true' | `https://asdf-rstudio.jobs.cloud-pipeline.com:31081`<br />`asdf-NoMachine`<br />`asdf-SparkUI` |
