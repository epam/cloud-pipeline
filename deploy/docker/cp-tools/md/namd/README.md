# Overview

This docker image offers [NAMD - NAnoscale Molecular Dynamics](http://www.ks.uiuc.edu/Research/namd/) and  [VMD - Visual Molecular Dynamics](https://www.ks.uiuc.edu/Research/vmd/) which are built using Nvidia CUDA support and can run using Nvidia Teska (K80 and V100) cards, offered by the Cloud Platform.

This environment can be accessed using two approaches:
1. `SSH-based` - for scripting and batch jobs
2. `Desktop-based` - for interactive analysis and modelling

**Note:** option #2 uses  `noMachine` software to provide remote desktop access. This requires additional client software installation: [noMachine Download Link](http://.../nomachine-enterprise-client_6.2.4_1.exe)

# Versions

* OS: `Centos7`
* Desktop: `Xfce4`
* NAMD: `2.13`
* VMD: `1.9.3`
* CUDA: `9.0`

# GPUs

By default, this environment will run using GPU-enabled nodes.

To override this behavior - run this image using any other `p2` or `p3` instance type to get more hardware resources.
