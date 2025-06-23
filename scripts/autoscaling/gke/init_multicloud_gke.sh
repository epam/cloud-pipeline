#!/bin/bash

_API_URL="@API_URL@"
_API_TOKEN="@API_TOKEN@"

launch_token="/etc/user_data_launched"
if [[ -f "$launch_token" ]]; then exit 0; fi

user_data_log="/var/log/user_data.log"
exec > "$user_data_log" 2>&1

function update_nameserver {
  local nameserver="$1"
  local ping_times="$2"

  local is_nameserver_reachable="0"
  if [ "$nameserver" ] && [[ "$nameserver" != "@"*"@" ]]; then
    if [ "$ping_times" ]; then
      if [ "$ping_times" == "infinity" ]; then
        ping_times=86400
      fi
      for i in $(seq 1 $ping_times); do
        echo "Pinging nameserver $nameserver on port 53"
        if nc -z -w 1 $nameserver 53 ; then
          echo "nameserver $nameserver can be reached on port 53"
          is_nameserver_reachable="1"
          break
        fi
      done

      if [ "$is_nameserver_reachable" != "1" ]; then
        echo "Elapsed $ping_times retries, but $nameserver can NOT be reached on port 53"
      fi
    fi

    chattr -i /etc/resolv.conf
    sed -i '/nameserver/d' /etc/resolv.conf
    echo "nameserver $nameserver" >> /etc/resolv.conf
    chattr +i /etc/resolv.conf
  fi
}


GLOBAL_DISTRIBUTION_URL="@GLOBAL_DISTRIBUTION_URL@"
if [ ! "$GLOBAL_DISTRIBUTION_URL" ] || [[ "$GLOBAL_DISTRIBUTION_URL" == "@"*"@" ]]; then
  GLOBAL_DISTRIBUTION_URL="https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/"
fi
export GLOBAL_DISTRIBUTION_URL

@custom_script_pre@
@WELL_KNOWN_HOSTS@

nameserver_val="@dns_proxy@"
nameserver_post_val="@dns_proxy_post@"
http_proxy_val="@http_proxy@"
https_proxy_val="@https_proxy@"
no_proxy_val="@no_proxy@"

update_nameserver "$nameserver_val" "30"

mkdir -p /etc/containerd/certs.d/
@DOCKER_CERTS@

######### Change /etc/containerd/config.toml:
containerd_config=/etc/containerd/config.toml
sed -i '/oom_score = -999/a root = "/mnt/disks/ssd0/containerd/"' $containerd_config
sed -i '/runtime_type = "io.containerd.runc.v2"/a [plugins."io.containerd.grpc.v1.cri".registry]\n  config_path = "/etc/containerd/certs.d"' $containerd_config
sed -i '/mirror/d' $containerd_config

systemctl restart containerd
