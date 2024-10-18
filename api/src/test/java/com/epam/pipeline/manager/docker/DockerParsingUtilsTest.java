/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.entity.docker.HistoryEntry;
import com.epam.pipeline.entity.docker.RawImageDescription;
import com.epam.pipeline.entity.execution.OSSpecificLaunchCommandTemplate;
import com.epam.pipeline.utils.StreamUtils;
import org.junit.Assert;
import org.junit.Test;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.docker.DockerParsingUtils.processCommands;
import static org.assertj.core.api.Assertions.assertThat;

public class DockerParsingUtilsTest {
    private static final String LATEST_DATE_ENTRY_JSON = "{\"created\":\"2017-10-02T18:59:07.729529044Z\"}";
    private static final String EARLIEST_DATE_ENTRY_JSON = "{\"created\":\"2017-10-02T18:57:48.784338364Z\"}";
    private static final String SHORT_DATE_ENTRY_JSON = "{\"created\":\"2017-10-02T18:58:48.784338Z\"}";
    private static final int EARLIEST_MINUTES = 57;
    private static final int LATEST_MINUTES = 59;
    private static final String POD_LAUNCH_CMD = "set -o pipefail; command -v wget >/dev/null 2>&1 && { " +
            "LAUNCH_CMD=\"wget --no-check-certificate " +
            "-q -O - 'var1'\"; }; command -v curl >/dev/null 2>&1 && { LAUNCH_CMD=\"curl " +
            "-s -k 'var2'\"; }; eval var3 | bash /dev/stdin \"var4\" 'var5' 'var6'";
    private static final String ARGS_CMD = "|4 CELL_PROFILER_VERSION=4.2.1 CYTHON_VERSION=0.29.30 " +
            "NUMPY_VERSION=1.23.1 PYTHON_3_DISTRIBUTION_URL=https://www.python.org/ftp/python/3.8.13" +
            "/Python-3.8.13.tgz /bin/sh -c python3.8 -m pip install wheel && wget " +
            "\"https://files.pythonhosted.org/packages/61/c7" +
            "/e1a31b6a092a5b91952fe96801b2d3167fcb3bad8386c023dd83de4c4ab8/centrosome-1.2.0.tar.gz\" " +
            "-O /tmp/centrosome.tar.gz && cd /tmp && tar -zxf centrosome.tar.gz && cd centrosome* && sed " +
            "-i \"s/setup_requires=\\[\\\"cython\\\", \\\"numpy\\\", \\\"pytest\\\",\\],/setup_requires" +
            "=\\[\\\"cython==$CYTHON_VERSION\\\", \\\"numpy==$NUMPY_VERSION\\\", \\\"pytest\\\",\\],/g\" " +
            "setup.py && python3.8 -m pip install . && rm -rf /tmp/centrosome*";

    private static final String ARGS_CMD_DOCKERFILE = "RUN /bin/sh -c " +
            "python3.8 -m pip install wheel && wget \"https://files.pythonhosted.org/packages/61/c7" +
            "/e1a31b6a092a5b91952fe96801b2d3167fcb3bad8386c023dd83de4c4ab8/centrosome-1.2.0.tar.gz\" " +
            "-O /tmp/centrosome.tar.gz && cd /tmp && tar -zxf centrosome.tar.gz && cd centrosome* && sed " +
            "-i \"s/setup_requires=\\[\\\"cython\\\", \\\"numpy\\\", \\\"pytest\\\",\\],/setup_requires" +
            "=\\[\\\"cython==$CYTHON_VERSION\\\", \\\"numpy==$NUMPY_VERSION\\\", \\\"pytest\\\",\\],/g\" " +
            "setup.py && python3.8 -m pip install . && rm -rf /tmp/centrosome*";
    private static final String RUN_AND_ARG_CMD = "RUN |1 TARGETARCH=amd64 /bin/sh -c echo \"/usr/local/nvidia/lib\" " +
            ">> /etc/ld.so.conf.d/nvidia.conf && echo \"/usr/local/nvidia/lib64\" >> /etc/ld.so.conf.d/nvidia.conf";

    private static final String RUN_AND_EMPTY_ARG_CMD = "RUN |1 TARGETARCH= /bin/sh -c echo \"/usr/local/nvidia/lib\"" +
            " >> /etc/ld.so.conf.d/nvidia.conf && echo \"/usr/local/nvidia/lib64\" >> /etc/ld.so.conf.d/nvidia.conf";

    private static final String COMMAND_WITH_OUT_RUN = "|1 /bin/sh -c echo \"/usr/local/nvidia/lib\" " +
            ">> /etc/ld.so.conf.d/nvidia.conf && echo \"/usr/local/nvidia/lib64\" >> /etc/ld.so.conf.d/nvidia.conf";

    private static final String RUN_AND_ARG_CMD_DOCKERFILE = "RUN /bin/sh -c echo \"/usr/local/nvidia/lib\" >> " +
            "/etc/ld.so.conf.d/nvidia.conf && echo \"/usr/local/nvidia/lib64\" >> /etc/ld.so.conf.d/nvidia.conf";

    @Test
    public void shouldCalculateLatestAndEarliestDateTimeProperly() {
        HistoryEntry entryWithEarliestDate = new HistoryEntry();
        entryWithEarliestDate.setV1Compatibility(EARLIEST_DATE_ENTRY_JSON);
        HistoryEntry entryWithLatestDate = new HistoryEntry();
        entryWithLatestDate.setV1Compatibility(LATEST_DATE_ENTRY_JSON);
        HistoryEntry entryWithShortDate = new HistoryEntry();
        entryWithShortDate.setV1Compatibility(SHORT_DATE_ENTRY_JSON);
        RawImageDescription description = new RawImageDescription();
        description.setHistory(Arrays.asList(entryWithEarliestDate, entryWithLatestDate, entryWithShortDate));

        assertThat(DockerParsingUtils.getEarliestDate(description).toInstant().atZone(ZoneOffset.UTC).getMinute())
                .isEqualTo(EARLIEST_MINUTES);
        assertThat(DockerParsingUtils.getLatestDate(description).toInstant().atZone(ZoneOffset.UTC).getMinute())
                .isEqualTo(LATEST_MINUTES);
    }

    @Test
    public void testProcessCommands() {
        final List<String> commands = new ArrayList<>();
        final OSSpecificLaunchCommandTemplate template = OSSpecificLaunchCommandTemplate.builder()
                .command("set -o pipefail; command -v wget >/dev/null 2>&1 && { LAUNCH_CMD=\"wget " +
                        "--no-check-certificate -q -O - '$linuxLaunchScriptUrl'\"; }; command -v " +
                        "curl >/dev/null 2>&1 && { LAUNCH_CMD=\"curl -s -k '$linuxLaunchScriptUrl'\"; }; " +
                        "eval $LAUNCH_CMD | bash /dev/stdin \"$gitCloneUrl\" '$gitRevisionName' '$pipelineCommand'")
                .build();
        final List<OSSpecificLaunchCommandTemplate> podLaunchTemplatesLinux = Collections.singletonList(template);
        final String podLaunchTemplatesWin = "Add-Type @\"\n" +
                "using System.Net;\n" +
                "using System.Security.Cryptography.X509Certificates;\n" +
                "public class TrustAllCertsPolicy : ICertificatePolicy {\n" +
                "    public bool CheckValidationResult(\n" +
                "        ServicePoint srvPoint, X509Certificate certificate,\n" +
                "        WebRequest request, int certificateProblem) {\n" +
                "            return true;\n" +
                "        }\n" +
                " }\n" +
                "\"@\n" +
                "[System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy\n" +
                "Invoke-WebRequest %s -Outfile .\\launch.py\n" +
                "@\"\n" +
                "%s\n" +
                "\"@ | Out-File -FilePath .\\task.ps1 -Encoding ascii -Force\n" +
                "$env:CP_TASK_PATH = Join-Path $(pwd) \"task.ps1\"\n" +
                "python .\\launch.py";
        commands.add(" ADD  file:file1 in / ");
        commands.add("LABEL org.label-schema.schema-version=1.0 org.label-schema.name=CentOS Base Image " +
                "org.label-schema.vendor=CentOS org.label-schema.license=GPLv2 org.label-schema.build-date=20191024");
        commands.add("CMD cmd1");
        commands.add("ENTRYPOINT entrypoint1");
        commands.add("/bin/sh -c yum install -y wget bzip2 gcc zlib-devel bzip2-devel xz-devel make ncurses-devel " +
                "unzip git curl cairo epel-release nfs-utils && yum clean all && curl " +
                "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python -");
        commands.add("ENV ANACONDA_HOME=/opt/local/anaconda");
        commands.add("ARG PYTHON_3_DISTRIBUTION_URL=https://www.python.org/ftp/python/3.8.13/Python-3.8.13.tgz");
        commands.add("ARG CELL_PROFILER_VERSION=4.2.1");
        commands.add("ARG NUMPY_VERSION=1.23.1");
        commands.add("ARG CYTHON_VERSION=0.29.30");
        commands.add("CMD cmd2");
        commands.add("ENTRYPOINT entrypoint2");
        commands.add("ENV PATH=/opt/local/anaconda/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        commands.add("ADD file:file2 in /");
        commands.add("ADD multi:db8a2a5f608acf2bb5634642f8cc134bbcc9b3b8c6727a2255c779e6a7183d5a in /tmp//");
        commands.add(ARGS_CMD);
        commands.add("COPY file:file3 in /start.sh");
        commands.add("COPY dir:dir in /start.sh");
        commands.add("1d");
        commands.add(POD_LAUNCH_CMD);
        commands.add(" ARG TARGETARCH");
        commands.add(RUN_AND_ARG_CMD);
        commands.add(RUN_AND_EMPTY_ARG_CMD);
        commands.add(COMMAND_WITH_OUT_RUN);

        final List<String> commandsPatternsToSkip = StreamUtils.appended(
                podLaunchTemplatesLinux.stream().map(OSSpecificLaunchCommandTemplate::getCommand),
                podLaunchTemplatesWin
        ).map(DockerParsingUtils::getLaunchPodPattern).collect(Collectors.toList());

        final List<String> result = processCommands("BASE_IMAGE", commands, commandsPatternsToSkip);

        Assert.assertEquals("FROM BASE_IMAGE", result.get(0));

        Assert.assertEquals(1, result.stream().filter(r -> r.startsWith("CMD ")).count());
        Assert.assertEquals("CMD cmd2", result.get(result.size() - 2));

        Assert.assertEquals(1, result.stream().filter(r -> r.startsWith("ENTRYPOINT ")).count());
        Assert.assertEquals("ENTRYPOINT entrypoint2", result.get(result.size() - 1));

        Assert.assertTrue(result.stream().anyMatch(r -> r.startsWith("ARG ")));
        Assert.assertTrue(result.stream().anyMatch(r -> r.startsWith("LABEL ")));

        Assert.assertTrue(result.stream().anyMatch(r -> r.matches("ADD <source-location> .+")));
        Assert.assertTrue(result.stream().anyMatch(r -> r.matches("COPY <source-location> .+")));

        Assert.assertTrue(result.stream().noneMatch(r -> r.matches("ADD file:.+")));
        Assert.assertTrue(result.stream().noneMatch(r -> r.matches("ADD multi:.+")));
        Assert.assertTrue(result.stream().noneMatch(r -> r.matches("COPY file:.+")));
        Assert.assertTrue(result.stream().noneMatch(r -> r.matches("COPY dir:.+")));

        Assert.assertTrue(result.contains(ARGS_CMD_DOCKERFILE));
        Assert.assertEquals(3, result.stream().filter(s -> s.equals(RUN_AND_ARG_CMD_DOCKERFILE)).count());

        Assert.assertFalse(result.contains(POD_LAUNCH_CMD));
    }
}
