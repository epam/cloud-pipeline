_rocky_version=8.9

rsync --info=progress2 -DrltK --inplace --safe-links --delete-after \
  --include="AppStream/" \
  --include="AppStream/x86_64/" \
  --include="AppStream/source/" \
  --include="AppStream/x86_64/***" \
  --include="AppStream/source/***" \
  --include="BaseOS/" \
  --include="BaseOS/x86_64/" \
  --include="BaseOS/source/" \
  --include="BaseOS/x86_64/***" \
  --include="BaseOS/source/***" \
  --include="HighAvailability/" \
  --include="HighAvailability/x86_64/" \
  --include="HighAvailability/source/" \
  --include="HighAvailability/x86_64/***" \
  --include="HighAvailability/source/***" \
  --include="NFV/" \
  --include="NFV/x86_64/" \
  --include="NFV/source/" \
  --include="NFV/x86_64/***" \
  --include="NFV/source/***" \
  --include="PowerTools/" \
  --include="PowerTools/x86_64/" \
  --include="PowerTools/source/" \
  --include="PowerTools/x86_64/***" \
  --include="PowerTools/source/***" \
  --include="RT/" \
  --include="RT/x86_64/" \
  --include="RT/source/" \
  --include="RT/x86_64/***" \
  --include="RT/source/***" \
  --include="ResilientStorage/" \
  --include="ResilientStorage/x86_64/" \
  --include="ResilientStorage/source/" \
  --include="ResilientStorage/x86_64/***" \
  --include="ResilientStorage/source/***" \
  --include="devel/" \
  --include="devel/x86_64/" \
  --include="devel/source/" \
  --include="devel/x86_64/***" \
  --include="devel/source/***" \
  --include="extras/" \
  --include="extras/x86_64/" \
  --include="extras/source/" \
  --include="extras/x86_64/***" \
  --include="extras/source/***" \
  --include="plus/" \
  --include="plus/x86_64/" \
  --include="plus/source/" \
  --include="plus/x86_64/***" \
  --include="plus/source/***" \
  --exclude='*' \
  rsync://mirror.atl.genesisadaptive.com/rocky/$_rocky_version/ /rocky-linux/

mkdir -p /rocky-linux/Devel/x86_64/
rsync --info=progress2 -DrltK --inplace --safe-links --delete-after \
    rsync://mirror.atl.genesisadaptive.com/rocky/$_rocky_version/Devel/x86_64/ /rocky-linux/Devel/x86_64/

mkdir -p /rocky-linux/Devel/source/
rsync --info=progress2 -DrltK --inplace --safe-links --delete-after \
    rsync://mirror.atl.genesisadaptive.com/rocky/$_rocky_version/Devel/source/ /rocky-linux/Devel/source/

mkdir -p /rocky-linux/nfv/x86_64/
rsync --info=progress2 -DrltK --inplace --safe-links --delete-after \
    rsync://mirror.atl.genesisadaptive.com/rocky/$_rocky_version/nfv/x86_64/ /rocky-linux/nfv/x86_64/

mkdir -p /rocky-linux/nfv/source/
rsync --info=progress2 -DrltK --inplace --safe-links --delete-after \
    rsync://mirror.atl.genesisadaptive.com/rocky/$_rocky_version/nfv/source/ /rocky-linux/nfv/source/


rm -rf /rocky-linux-rpms
mkdir -p /rocky-linux-rpms
_rpms_list=$(find /rocky-linux -name '*.rpm')
for _rpm in $_rpms_list; do
    /bin/mv "$_rpm" /rocky-linux-rpms/
done



export LANG=en_US.UTF-8
export LANGUAGE=en_US.UTF-8
export LC_ALL=en_US.UTF-8
localedef -v -c -i en_US -f UTF-8 en_US.UTF-8

yum install curl \
            wget \
            python2 \
            python2-pip \
            yum-priorities \
            yum-utils \
            createrepo -y

pip install awscli==1.18.213

CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR=$(mktemp -d)
docker run  -it \
                --rm \
                -v ${CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR}:/rpmcache \
                rockylinux:${_rocky_version} \
                bash

/bin/mv $CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR/*.rpm /rocky-linux-rpms/
rm -rf $CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR


createrepo "/rocky-linux-rpms" --workers $(nproc)
cat > "/rocky-linux-rpms/cloud-pipeline.repo" << EOF
[cloud-pipeline]
name=Cloud Pipeline Packages Cache
baseurl=https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/rocky/$_rocky_version/
enabled=1
gpgcheck=0
priority=1
EOF

# rpm8
i=0
_rpms_list=$(find /rocky-linux-rpms -type f)
for _rpm in $_rpms_list; do
    _rpm_file_name=$(basename $_rpm)
    _rpm_file_name_spaces=$(echo "$_rpm_file_name" | tr "+" " ")
    nohup aws s3 cp "$_rpm" "s3://cloud-pipeline-oss-builds/tools/repos/rocky/$_rocky_version/$_rpm_file_name_spaces" &> /dev/null &
    i=$(($i+1))
    echo "${i}: $_rpm_file_name_spaces"
    if (( $i >= $(nproc) )); then
        i=0
        wait
    fi
done

