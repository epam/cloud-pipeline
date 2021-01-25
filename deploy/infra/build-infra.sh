# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

export CP_AWS="aws"
export CP_AZURE="az"
export CP_GOOGLE="gcp"
export CP_NA="NA"
export INSTALL_SCRIPT_PATH="$( cd "$(dirname "$0")" ; pwd -P )"

function check_installed {
    command -v "$1" >/dev/null 2>&1
    return $?
}

function install_azure_cli {
    echo "Install azure-cli..."
    if check_installed "yum"; then
            rpm --import https://packages.microsoft.com/keys/microsoft.asc
            sh -c 'echo -e "[azure-cli]\nname=Azure CLI\nbaseurl=https://packages.microsoft.com/yumrepos/azure-cli\nenabled=1\ngpgcheck=1\ngpgkey=https://packages.microsoft.com/keys/microsoft.asc" > /etc/yum.repos.d/azure-cli.repo'
            yum install -q -y azure-cli
        elif check_installed "apt-get"; then
            apt-get install apt-transport-https lsb-release software-properties-common dirmngr -y > /dev/null
            AZ_REPO=$(lsb_release -cs)
            echo "deb [arch=amd64] https://packages.microsoft.com/repos/azure-cli/ $AZ_REPO main" | \
            tee /etc/apt/sources.list.d/azure-cli.list
            apt-key --keyring /etc/apt/trusted.gpg.d/Microsoft.gpg adv \
            --keyserver packages.microsoft.com \
            --recv-keys BC528686B50D79E339D3721CEB3E94ADBE1229CF
            apt-get update -y > /dev/null
            apt-get install -y azure-cli > /dev/null
        fi
        echo "Azure-cli installed."
}

function terminate_instance {
    _instance_id=$1
    instance_json=$(az vm show --ids $_instance_id)

    echo "Terminating instance $_instance_id"
    az vm delete -y --ids "$_instance_id"
    terminate_instance_status=$?

    disk_id=$(echo $instance_json | jq -r '.storageProfile.osDisk.managedDisk.id')
    echo "Delete disk: $disk_id"
    az disk delete -y --ids $disk_id

    nic_id=$(echo $instance_json | jq -r '.networkProfile.networkInterfaces[0].id')
    public_ip=$(az network nic show --ids $nic_id | jq -r '.ipConfigurations[0].publicIpAddress.id')
    echo "Delete nic: $nic_id"
    az network nic delete --ids $nic_id

    echo "Delete ip address: $public_ip"
    az network public-ip delete --ids $public_ip

    if [ $terminate_instance_status -ne 0 ]; then
        echo "ERROR: unable to terminate instance $_instance_id. Please delete it manually"
        echo "$terminate_instance_json"
        return 1
    fi
}

function build_ami {
    local cloud_provider="$1"
    local region="$2"
    local base_ami="$3"
    local base_ami_disk="$4"
    local base_ami_size="$5"
    local ssh_key_name="$6"
    local security_groups="$7"
    local subnet="$8"
    local base_ami_name="$9"
    local user_data_script="${10}"
    local output_path="${11}"
    local make_public="${12}"

    if [ -z "$output_path" ]; then
        echo "Output path is not set"
        return 1
    fi
    if [ -z "$base_ami_name" ] || [ "$base_ami_name" == "NA" ]; then
        echo "Image name is not specified, refusing to build image"
        return 1
    fi

    if [ "$cloud_provider" == "$CP_AWS" ]; then
        if [ "$base_ami" == "NA" ]; then
            base_ami=$(aws ec2 describe-images --region $region --owners amazon --filters 'Name=name,Values=amzn2-ami-hvm-2.0.????????-x86_64-gp2' 'Name=state,Values=available' --output json | jq -r '.Images | sort_by(.CreationDate) | last(.[]).ImageId')
            echo "Amazon Linux 2 AMI will be used by default ($base_ami), as other value was not specified"
        fi
        if [ "$base_ami_disk" == "NA" ]; then
            base_ami_disk="10"
            echo "Root device will be set to $base_ami_disk, as other value was not specified"
        fi
        if [ -z "$base_ami_size" ] || [ "$base_ami_size" == "NA" ]; then
            base_ami_size="m5.large"
            echo "AMI will be built using $base_ami_size, as other value was not specified"
        elif [ "$base_ami_size" == "gpu" ]; then
            base_ami_size="p2.xlarge"
            echo "AMI will be built using $base_ami_size, as other value was not specified for GPU AMI"
        elif [ "$base_ami_size" == "common" ]; then
            base_ami_size="m5.large"
            echo "AMI will be built using $base_ami_size, as other value was not specified for Common AMI"
        fi
        if [ "$subnet" == "NA" ]; then
            subnet=""
            echo "Subnet is not defined - default VPC/subnet will be used for a current region"
        else
            subnet="--subnet-id $subnet"
        fi
        if [ "$security_groups" == "NA" ]; then
            security_groups=""
            echo "Security groups are not defined - default VPC groups will be used for a current region"
        else
            security_groups="--security-group-ids $security_groups"
        fi
        if [ "$ssh_key_name" == "NA" ]; then
            ssh_key_name=""
            echo "SSH key name is not defined - it will be not possible to ssh into the instance ran for AMI creation"
        else
            ssh_key_name="--key-name $ssh_key_name"
        fi
        if [ -z "$user_data_script" ] || [ "$user_data_script" == "NA" ]; then
            echo "Cannot find user data script"
            return 1
        fi
        echo

        echo "Preparing user data installation script"
        user_data_script_creds="/tmp/install-node.sh"

        \cp $user_data_script $user_data_script_creds
        sed -i "s/{{AWS_ACCESS_KEY_ID}}/$AWS_ACCESS_KEY_ID/g" $user_data_script_creds
        sed -i "s/{{AWS_SECRET_ACCESS_KEY}}/$AWS_SECRET_ACCESS_KEY/g" $user_data_script_creds
        echo

        echo "Getting base image ($base_ami) details"
        base_image_details_json=$(aws ec2 describe-images --region "$region" --image-id "$base_ami")
        if [ $? -ne 0 ]; then
            echo "ERROR: unable to get base image ($base_ami) details"
            echo "$base_image_details_json"
            return 1
        fi
        base_image_root_device=$(echo $base_image_details_json | jq -r '.Images[0].BlockDeviceMappings[0].DeviceName')
        if [ -z "$base_image_root_device" ] || [ "$base_image_root_device" == "null" ]; then
            echo "ERROR: unable to get base image ($base_ami) root device"
            echo "$base_image_details_json"
            return 1
        fi
        echo "Base image ($base_ami) root device is $base_image_root_device"
        echo

        echo "Starting instance from the base image $base_ami"
        run_instance_json=$(aws ec2 run-instances --image-id "$base_ami" \
                                                  --region "$region" \
                                                  --count 1 \
                                                  --instance-type "$base_ami_size" \
                                                  --block-device-mapping "DeviceName=$base_image_root_device,Ebs={VolumeSize=$base_ami_disk,VolumeType=gp2}" \
                                                  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$base_ami_name}]" \
                                                  --user-data file://$user_data_script_creds \
                                                  $subnet \
                                                  $ssh_key_name \
                                                  $security_groups)
        run_instance_result=$?
        rm -f $user_data_script_creds

        if [ $run_instance_result -ne 0 ]; then
            echo "ERROR: unable to start instance"
            echo "$run_instance_json"
            return 1
        fi
        echo

        echo "Wait for the initialization finish"
        instance_id=$(echo $run_instance_json | jq -r '.Instances[0].InstanceId')
        echo "Instance is started with: $instance_id. Waiting for initialization..."

        instance_state_code=0
        while [ "$instance_state_code" != "16" ]; do
            instance_state_code=$(aws ec2 --region $region describe-instances --instance-id "$instance_id" | jq -r '.Reservations[0].Instances[0].State.Code')
            sleep 5
        done
        echo "Instance $instance_id is running"
        echo

        echo "Waiting for installation script to complete..."
        user_data_state="none"
        while [ "$user_data_state" != "done" ]; do
            user_data_state=$(aws ec2 --region $region describe-tags --filters "Name=resource-id,Values=$instance_id" | jq '.Tags[] | select(.Key=="user_data") | .Value' -r)
            sleep 5
        done
        echo "Installation script has completed"
        echo


        echo "Checking if image with name $base_ami_name already exists in region $region"
        existing_image_id=$(aws ec2 --region $region describe-images --filters "Name=name,Values=$base_ami_name" | jq -r '.Images[0].ImageId')
        if [ "$existing_image_id" ] && [ "$existing_image_id" != "null" ]; then
            echo "Image with name $base_ami_name already exists with id $existing_image_id, deleting it"
            deregister_image_json=$(aws ec2 --region $region deregister-image --image-id "$existing_image_id")
            if [ $? -ne 0 ]; then
                echo "ERROR: unable to delete an existing image $existing_image_id"
                echo "$deregister_image_json"
                return 1
            fi
            echo "Image with name $base_ami_name and id $existing_image_id is deleted"
        else
            echo "Image with name $base_ami_name does not exist, proceeding with creation"
        fi
        echo

        echo "Creating new AMI from instance $instance_id with name $base_ami_name"
        create_image_json=$(aws ec2 --region $region create-image --instance-id "$instance_id" --name "$base_ami_name")
        if [ $? -ne 0 ]; then
            echo "ERROR: unable to create image from instance $instance_id"
            echo "$create_image_json"
            return 1
        fi

        new_image_id=$(echo $create_image_json | jq -r '.ImageId')
        echo "Cretion of image $base_ami_name with id $new_image_id started"
        echo

        echo "Waiting for creation of image $new_image_id with name $base_ami_name"
        new_image_state="none"
        while [ "$new_image_state" != "available" ]; do
            new_image_state=$(aws ec2 --region $region describe-images --image-id "$new_image_id" | jq -r '.Images[0].State')
            sleep 5
        done
        echo "Image $new_image_id with name $base_ami_name is created"
        echo

        echo "Terminating instance $instance_id"
        terminate_instance_json=$(aws ec2 --region $region terminate-instances --instance-id "$instance_id")
        if [ $? -ne 0 ]; then
            echo "ERROR: unable to terminate instance $instance_id. Please delete it manually"
            echo "$terminate_instance_json"
            return 1
        fi
        echo

        if [ "$make_public" == "true" ]; then
            echo "Public access will be granted to the image $new_image_id ($base_ami_name)"
            aws ec2 modify-image-attribute --region $region --image-id $new_image_id --launch-permission "Add=[{Group=all}]"
            if [ $? -ne 0 ]; then
                echo "ERROR: unable to grant public access to the image $new_image_id"
            fi
            echo "Public access IS GRANTED to the image $new_image_id ($base_ami_name)"
        fi

        echo "Image is created:"
        echo "-> Name: $base_ami_name"
        echo "-> ID: $new_image_id"
        echo "-> Region: $region"

        echo "${cloud_provider},${region},${new_image_id},${base_ami_name},$(date '+%Y-%m-%d/%H:%M:%S')" >> $output_path

    elif [ "$cloud_provider" == "$CP_AZURE" ]; then
        # Disable telemetry upload for az cli commands to improve performance a bit
        export AZURE_CORE_COLLECT_TELEMETRY=false

        if [ -z "$base_ami" ] || [ "$base_ami" == "NA" ]; then
            base_ami="OpenLogic:CentOS:7.6:latest"
            echo "CentOS Image will be used by default ($base_ami), as other value was not specified"
        fi
        if [ -z "$base_ami_disk" ] || [ "$base_ami_disk" == "NA" ]; then
            base_ami_disk="30"
            echo "Root device will be set to $base_ami_disk, as other value was not specified"
        fi
        if [ -z "$base_ami_size" ] || [ "$base_ami_size" == "NA" ]; then
            base_ami_size="Standard_D2s_v3"
            echo "Image will be built using $base_ami_size, as other value was not specified"
        elif [ "$base_ami_size" == "gpu" ]; then
            base_ami_size="Standard_NC6"
            echo "Image will be built using $base_ami_size, as other value was not specified for GPU Image"
        elif [ "$base_ami_size" == "common" ]; then
            base_ami_size="Standard_D2s_v3"
            echo "Image will be built using $base_ami_size, as other value was not specified for Common Image"
        fi

        local _default_vnet=$(az network vnet list --resource-group $CP_AZURE_RESOURCE_GROUP --query "[?location=='$region']" | jq -r '.[].name' | head -n 1)
        if [ -z "$subnet" ] || [ "$subnet" == "NA" ]; then
            echo "Subnet is not defined, will try to determine a default one"
            if [ -z "$_default_vnet" ] || [ "$_default_vnet" == "null" ]; then
                echo "ERROR: unable to get a default VNET name for $region region, exiting"
                return 1
            fi
            echo "Default region $region VNET is $_default_vnet"
            subnet=$(az network vnet subnet list --resource-group $CP_AZURE_RESOURCE_GROUP --vnet-name $_default_vnet | jq -r '.[].id' | head -n 1)
            if [ -z "$subnet" ] || [ "$subnet" == "null" ]; then
                echo "ERROR: unable to get a default subnet from $_default_vnet VNET for $region region, exiting"
                return 1
            fi
            echo "Default region $region subnet is $subnet"
        fi
        subnet="--subnet $subnet"

        if [ -z "$security_groups" ] || [ "$security_groups" == "NA" ]; then
            echo "Security groups are not defined, will try to determine a default one from the default VNET $_default_vnet"
            security_groups=$(az network vnet subnet list --resource-group $CP_AZURE_RESOURCE_GROUP --vnet-name $_default_vnet | jq -r '.[].networkSecurityGroup.id')
            if [ -z "$security_groups" ] || [ "$security_groups" == "null" ]; then
                echo "WARN: unable to get a default security group for $_default_vnet VNET from $region region. Will try to determine a default one from the region"
                security_groups=$(az network nsg list --query "[?location=='$region']" | jq -r '.[].id' | head -n 1)
            fi
            if [ -z "$security_groups" ] || [ "$security_groups" == "null" ]; then
                echo "ERROR: unable to get a default security group for $region region. Exiting"
                return 1
            fi
            echo "Default region $region security group is $security_groups"
        fi
        security_groups="--nsg $security_groups"

        if [ -z "$user_data_script" ] || [ "$user_data_script" == "NA" ]; then
            echo "ERROR: cannot find user data script"
            return 1
        fi
        echo

        NODE_ADMIN_USER=pipeline
        echo "Getting base image ($base_ami) details"
        base_image_details_json=$(az vm image show --location "$region" --urn "$base_ami")
        if [ $? -ne 0 ]; then
            echo "ERROR: unable to get base image ($base_ami) details"
            echo "$base_image_details_json"
            return 1
        fi

        echo "Base image ($base_ami)"
        echo

        echo "Starting instance from the base image $base_ami"
        run_instance_json=$(az vm create --resource-group $CP_AZURE_RESOURCE_GROUP \
                                                  --name "$base_ami_name" \
                                                  --image "$base_ami" \
                                                  --location "$region" \
                                                  --size "$base_ami_size" \
                                                  --os-disk-size-gb $base_ami_disk \
                                                  $subnet \
                                                  $security_groups \
                                                  --tags Name=$base_ami_name \
                                                  --custom-data $user_data_script \
                                                  --generate-ssh-keys \
                                                  --admin-username $NODE_ADMIN_USER)
        run_instance_result=$?

        if [ $run_instance_result -ne 0 ]; then
            echo "ERROR: unable to start instance"
            echo "$run_instance_json"
            return 1
        fi
        echo

        echo "Getting instance id"
        instance_id=$(echo $run_instance_json | jq -r '.id')
        echo "Instance is started with id: $instance_id"

        echo "Run installation script..."
        az vm run-command invoke --ids $instance_id --command-id RunShellScript --scripts '/bin/cat /var/lib/waagent/CustomData | /bin/base64 --decode | /bin/bash --login &> /var/log/vm-image-install.log'

        echo "Installation script done, will wait for 30 seconds more"
        sleep 30
        # FIXME: change back to SSH instead of run-command
        echo "Deprovision the VM..."
        az vm run-command invoke --ids $instance_id \
                         --command-id RunShellScript \
                         --scripts '/usr/bin/python -u /usr/sbin/waagent -deprovision+user -force' &> /dev/null &
        _run_cmd_pid=$!
        echo "Deprovision command sent, sleeping 60 seconds..."
        sleep 60
        kill $_run_cmd_pid
        echo "Deprovision done"

        echo "Clean up process is completed."

        echo
        echo "Checking if image with name $base_ami_name already exists in region $region"
        existing_image_id=$(az image show --name $base_ami_name --resource-group $CP_AZURE_RESOURCE_GROUP | jq -r '.id')
        if [ "$existing_image_id" ] && [ "$existing_image_id" != "null" ]; then
            echo "Image with name $base_ami_name already exists with id $existing_image_id, deleting it"
            deregister_image_json=$(az image delete --ids "$existing_image_id")
            if [ $? -ne 0 ]; then
                echo "ERROR: unable to delete an existing image $existing_image_id"
                echo "$deregister_image_json"
                return 1
            fi
            echo "Image with name $base_ami_name and id $existing_image_id is deleted"
        else
            echo "Image with name $base_ami_name does not exist, proceeding with creation"
        fi
        echo

        echo "Creating new Image from instance $instance_id with name $base_ami_name"
        echo "-> Deallocating instance"
        az vm deallocate --ids $instance_id
        
        echo "-> Generalizing instance"
        az vm generalize --ids $instance_id
        
        echo "-> Saving image"
        create_image_json=$(az image create --resource-group $CP_AZURE_RESOURCE_GROUP --name "$base_ami_name" --source $instance_id --location "$region")
        if [ $? -ne 0 ]; then
            echo "ERROR: unable to create image from instance $instance_id"
            echo "$create_image_json"
            return 1
        fi

        new_image_id=$(echo $create_image_json | jq -r '.id')
        echo "Image $new_image_id with name $base_ami_name is created"
        echo

        terminate_instance $instance_id
        echo

        if [ "$make_public" == "true" ]; then
            echo "WARN: Public access is requested, but is NOT SUPPORTED for the $CP_AZURE cloud provider"
        fi

        echo "Image is created:"
        echo "-> Name: $base_ami_name"
        echo "-> ID: $new_image_id"
        echo "-> Region: $region"

        echo "${cloud_provider},${region},${new_image_id},${base_ami_name},$(date '+%Y-%m-%d/%H:%M:%S')" >> $output_path

    else
        echo "Cloud provider $cloud_provider is not supported"
        return 1
    fi
}

###############
# Parse options
###############
POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    -p|--cloud-provider)
    export CP_CLOUD_PROVIDER="$2"
    shift # past argument
    shift # past value
    ;;
    -r|--region)
    export CP_CLOUD_REGION="$2"
    shift # past argument
    shift # past value
    ;;
    -pub|--make-public)
    export CP_MAKE_IMAGES_PUBLIC="true"
    shift # past argument
    ;;
    -bc|--base-common-image)
    export CP_BASE_COMMON_IMAGE_ID="$2"
    shift # past argument
    shift # past value
    ;;
    -bg|--base-gpu-image)
    export CP_BASE_GPU_IMAGE_ID="$2"
    shift # past argument
    shift # past value
    ;;
    -d|--default-disk)
    export CP_DEFAULT_DISK="$2"
    shift # past argument
    shift # past value
    ;;
    -dc|--default-common-size)
    export CP_DEFAULT_COMMON_SIZE="$2"
    shift # past argument
    shift # past value
    ;;
    -dg|--default-gpu-size)
    export CP_DEFAULT_GPU_SIZE="$2"
    shift # past argument
    shift # past value
    ;;
    -k|--aws-ssh-key-name)
    export CP_AWS_SSH_KEY_NAME="$2"
    shift # past argument
    shift # past value
    ;;
    -rg|--az-resource-group)
    export CP_AZURE_RESOURCE_GROUP="$2"
    shift # past argument
    shift # past value
    ;;
    -ac|--az-credentials-path)
    export CP_AZURE_AUTH_LOCATION="$2"
    shift # past argument
    shift # past value
    ;;
    -sg|--security-groups)
    export CP_SECURITY_GROUPS="$2"
    shift # past argument
    shift # past value
    ;;
    -n|--subnet)
    export CP_SUBNET="$2"
    shift # past argument
    shift # past value
    ;;
    -i|--image-name-prefix)
    export CP_IMAGE_PREFIX="$2"
    shift # past argument
    shift # past value
    ;;
    -o|--output)
    export CP_OUTPUT="$2"
    shift # past argument
    shift # past value
    ;;
    *)    # unknown option
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters



###########################
# Validate mandatory/default inputs
###########################
if [ -z "$CP_CLOUD_PROVIDER" ]; then
    echo "Cloud provider is not specified, use \"-p|--cloud-provider\" option"
    exit 1
fi
if [ -z "$CP_CLOUD_REGION" ]; then
    echo "Cloud region is not specified, use \"-r|--region\" option. Comma-separated list can be used to build ami is several regions"
    exit 1
else
    # Parse regions into an arrays
    IFS="," read -ra regions_arr <<< "$CP_CLOUD_REGION"
    export regions_arr
fi

if [ -z "$CP_IMAGE_PREFIX" ]; then
    export CP_IMAGE_PREFIX="CloudPipeline-Image"
    echo "Iamge prefix is not specified, $CP_IMAGE_PREFIX will be used by default, use \"-i|--image-name-prefix\" option"
fi
if [ -z "$CP_DEFAULT_COMMON_SIZE" ]; then
    export CP_DEFAULT_COMMON_SIZE="common"
    echo "Common AMI will be built using default size for $CP_CLOUD_PROVIDER cloud provider as other value was not specified, use \"-dc|--default-common-size\" option"
fi
if [ -z "$CP_DEFAULT_GPU_SIZE" ]; then
    export CP_DEFAULT_GPU_SIZE="gpu"
    echo "GPU AMI will be built using default size for $CP_CLOUD_PROVIDER cloud provider as other value was not specified, use \"-dg|--default-gpu-size\" option"
fi
if [ -z "$CP_OUTPUT" ]; then
    export CP_OUTPUT=~/.pipe/cloud-images.txt
    echo "Output path is not set, images spec will be written to $CP_OUTPUT"
fi
mkdir -p $(dirname $CP_OUTPUT)
rm -f $CP_OUTPUT

##############################
# Check dependencies installed
##############################

if check_installed "apt-get"; then
    package_manager="apt-get"
elif check_installed "yum"; then
    package_manager="yum"
else
    echo "Unable to determine a package manager. Is it ubuntu/centos?"
    exit 1
fi

if ! check_installed "wget"; then
    $package_manager install -y wget
fi
if ! check_installed "curl"; then
    $package_manager install -y wget
fi
if ! check_installed "python"; then
    echo "Installing python"
    $package_manager install -y python
fi
if ! check_installed "pip"; then
    echo "Installing pip"
    curl https://bootstrap.pypa.io/2.7/get-pip.py | python -
fi
if ! check_installed "jq"; then
    echo "Installing jq"
    wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq
    chmod +x /usr/bin/jq
fi

if [ "$CP_CLOUD_PROVIDER" == "$CP_AWS" ]; then
    if ! check_installed "aws"; then
        echo "Installing aws cli"
        pip install --upgrade pip awscli
    fi
    export AWS_DEFAULT_REGION=${regions_arr[0]}
    if [ -z "$AWS_ACCESS_KEY_ID" ] || [ -z "$AWS_SECRET_ACCESS_KEY" ] || ! aws sts get-caller-identity &> /dev/null; then
        echo "AWS CLI is not configured correctly. Please export credentials as envrionment variables (file-base authentication won't work)"
        exit 1
    fi
elif [ "$CP_CLOUD_PROVIDER" == "$CP_AZURE" ]; then

    if ! check_installed "az"; then
        echo "Installing az cli"
        install_azure_cli
    fi

    if ! az account show &> /dev/null; then
        echo "Login with $CP_AZURE_AUTH_LOCATION file..."
        az login --service-principal --username $(cat $CP_AZURE_AUTH_LOCATION | jq -r .clientId) --password $(cat $CP_AZURE_AUTH_LOCATION | jq -r .clientSecret) --tenant $(cat $CP_AZURE_AUTH_LOCATION | jq -r .tenantId)
    fi


    if [ -z "$CP_AZURE_AUTH_LOCATION" ] || ! az account show &> /dev/null; then
        echo "AZURE CLI is not configured correctly. Please export path to credentials file as envrionment variable (AZURE_AUTH_LOCATION) (file-base authentication won't work)"
        exit 1
    fi

    if [ -z "$CP_AZURE_RESOURCE_GROUP" ]; then
        echo "Azure default resource group is not defined. Please specify it via CP_AZURE_RESOURCE_GROUP environment variable or using -rg|--az-resource-group option"
        exit 1
    fi

else
    echo "Cloud provider $CP_CLOUD_PROVIDER is not supported"
    exit 1
fi



##################################
# Run build in each of the requested regions
##################################
echo
for region_item in "${regions_arr[@]}"; do
    echo "Building new image(s):"
    echo "-> Provider: $CP_CLOUD_PROVIDER"
    echo "-> Region: $region_item"
    echo

    echo "-> Common image"
    build_ami   "${CP_CLOUD_PROVIDER}" \
                "${region_item}" \
                "${CP_BASE_COMMON_IMAGE_ID:-NA}" \
                "${CP_DEFAULT_DISK:-NA}" \
                "${CP_DEFAULT_COMMON_SIZE:-NA}" \
                "${CP_AWS_SSH_KEY_NAME:-NA}" \
                "${CP_SECURITY_GROUPS:-NA}" \
                "${CP_SUBNET:-NA}" \
                "${CP_IMAGE_PREFIX}-Common" \
                "$INSTALL_SCRIPT_PATH/$CP_CLOUD_PROVIDER/install-common-node.sh" \
                "$CP_OUTPUT" \
                "${CP_MAKE_IMAGES_PUBLIC:-true}"

    if [ $? -ne 0 ]; then
        echo "ERROR: Common image build failed for a current provider ($CP_CLOUD_PROVIDER) and region ($region_item)"
    fi

    echo

    echo "-> GPU image"
    build_ami   "${CP_CLOUD_PROVIDER}" \
                "${region_item}" \
                "${CP_BASE_GPU_IMAGE_ID:-NA}" \
                "${CP_DEFAULT_DISK:-NA}" \
                "${CP_DEFAULT_GPU_SIZE:-NA}" \
                "${CP_AWS_SSH_KEY_NAME:-NA}" \
                "${CP_SECURITY_GROUPS:-NA}" \
                "${CP_SUBNET:-NA}" \
                "${CP_IMAGE_PREFIX}-GPU" \
                "$INSTALL_SCRIPT_PATH/$CP_CLOUD_PROVIDER/install-gpu-node.sh" \
                "$CP_OUTPUT" \
                "${CP_MAKE_IMAGES_PUBLIC:-true}"

    if [ $? -ne 0 ]; then
        echo "ERROR: GPU image build failed for a current provider ($CP_CLOUD_PROVIDER) and region ($region_item)"
    fi

    echo
done
