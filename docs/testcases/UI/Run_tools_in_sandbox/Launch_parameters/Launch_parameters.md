# Launch parameters testcases

| Case ID                                            | Description/name                                                                                                           |
|----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| [**223**](223.md)                                  | Check the launch system parameters preference (CP_FSBROWSER_ENABLED)                                                       |
| [**2234**](2234.md)                                | "Custom" capabilities implementation                                                                                       |
| [**2295**](2295.md)                                | "Custom" capabilities with configured job parameters                                                                       |
| [**2319**](2319.md)                                | Check automatically blocking users, based on the AD info                                                                   |
| [**2323_1**](2323/2323_1.md)                       | Check that "Capabilities" for Tools can depend on the docker image OS                                                      |
| [**2323_2**](2323/2323_2.md)                       | Check that "Capabilities" for Pipelines and Configurations can depend on the docker image OS                               |
| [**2323_3**](2323/2323_3.md)                       | Check custom "Capabilities" for all OS                                                                                     |
| [**2342_1**](2342/2342_1.md)                       | Check restricted "system" parameters for Tool settings and Launch form                                                     |
| [**2342_2**](2342/2342_2.md)                       | Check restricted "system" parameters for Pipeline and Detached Configuration                                               |
| [**2342_3**](2342/2342_3.md)                       | Check allowed "system" parameters to specific users group for Tool settings and Launch form                                |
| [**2342_4**](2342/2342_4.md)                       | Check allowed "system" parameters for Pipeline and Detached Configuration                                                  |
| [**2342_5**](2342/2342_5.md)                       | Check possibility to change restricted "system" parameters added to the Tool settings, pipeline and Detached configuration |
| [**2342_6**](2342/2342_6.md)                       | Check restricted only to specific users groups "system" parameters via CLI                                                 |
| [**2423_1**](2423/2423_1.md)                       | Maintenance mode notification                                                                                              |
| [**2423_2**](2423/2423_2.md)                       | Check launch run in Maintenance mode                                                                                       |
| [**2423_3**](2423/2423_3.md)                       | Check switch to Maintenance mode during the run Committing                                                                 |
| [**2423_4**](2423/2423_4.md)                       | Check switch to Maintenance mode during the run Pausing and Resuming operations                                            |
| [**2423_5**](2423/2423_5.md)                       | Check disabled Hot node pools autoscaling in Maintenance mode                                                              |
| [**2642_1**](2642/2642_1.md)                       | Check Global Restriction a count of the running instances                                                                  |
| [**2642_2**](2642/2642_2.md)                       | Check running instances restriction applied to Group                                                                       |
| [**2642_3**](2642/2642_3.md)                       | Check simultaneous applying of two Group level running instances restrictions                                              |
| [**2642_4**](2642/2642_4.md)                       | Check running instances restriction applied to User                                                                        |
| [**2642_5**](2642/2642_5.md)                       | Check running instances restriction for Cluster runs                                                                       |
| [**2642_6**](2642/2642_6.md)                       | Check running instances restriction for launch tool with configured Cluster                                                |
| [**2642_7**](2642/2642_7.md)                       | Check running instances restriction for Auto-Scaled Cluster runs                                                           |
| [**2736**](2736.md)                                | Forcible terminate instances if the job is stuck in umount                                                                 |
| [**3064_1**](3064/3064_1.md)                       | Optionally hide maintenance configuration for tool jobs                                                                    |
| [**3064_2**](3064/3064_2.md)                       | Optionally hide maintenance configuration for pipeline jobs                                                                |
| [**3069**](3069.md)                                | Check hiding system capabilities                                                                                           |
| [**3074**](3074.md)                                | GUI Launch form: disk size disclaimers                                                                                     |
| [**3122_1**](3122_insufficient_capacity/3122_1.md) | Run jobs in other regions in case of insufficient capacity                                                                 |
| [**3122_2**](3122_insufficient_capacity/3122_2.md) | Restart jobs in other regions shouldn't work for cluster run                                                               |
| [**3122_3**](3122_insufficient_capacity/3122_3.md) | Restart jobs in other regions shouldn't work for run with cloud dependent parameters                                       |
| [**3122_4**](3122_insufficient_capacity/3122_4.md) | Run jobs in case of insufficient capacity for region without Run shift policy flag                                         |
| [**783**](783.md)                                  | Check the configure CPU resource                                                                                           |
| [**TC-PARAMETERS-1**](TC-PARAMETERS-1.md)          | Check the configure allowed instance types                                                                                 |
| [**TC-PARAMETERS-2**](TC-PARAMETERS-2.md)          | Check the configure allowed instance types for docker images                                                               |
| [**TC-PARAMETERS-3**](TC-PARAMETERS-3.md)          | Check the configure cluster aws ebs volume type for docker images                                                          |
