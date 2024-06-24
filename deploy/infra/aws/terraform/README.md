# This terraform project serves to automatically deploy Cloud Pipeline infrastructure in the cloud.

1. ### Download the latest version of Terraform from https://www.terraform.io/downloads.

Or install it using a terminal, e.g.
`brew install terraform`  
`yum install terraform`  
`apt install terraform`  

2. ### Open the terminal, clone this project, and go to the project folder.

You will see the terraform configuration files. They are divided into separate files for ease of reference. Each file is named according to its purpose. Terraform can also read the configuration from one shared configuration file `main.tf`.

3. ### Enter the command to initialize the terraform project and load the necessary modules:
`terraform init`.

4. ### To prepare the deployment, replace the data in the `vars.tf` file with the required values. Here you will be able to set the region, 12-digit account ID, project name, subnets, and other parameters. 

5. ### After preparing the environment, set the credentials by typing in the terminal:

`export AWS_ACCESS_KEY_ID=<your_aws_access_key_id>`  
`export AWS_SECRET_ACCESS_KEY=<your_aws_secret_access_key>`  

Under these credentials, Terraform will deploy to the cloud.

6. ### To run terraform from the root project folder, type `terraform plan`. Terraform will plan the deployment, check for possible configuration issues, and display information about what changes you can make to the cloud.

If you are satisfied with the displayed information, you can start terraforming.

7. ### To do this, type `terraform apply` and after a while, enter confirmation of the deployment by typing `yes` to continue or `no` if you do not want to continue.

After successfully completing a terraform job you will see a message about successful completion, number of resources deployed, predefined outputs with public dns names of instances, IPs, security groups, and other.

In order to see all the managed terraform configuration, type `terraform show -no-color > plan.json` this will output all the information to the json file.

8. ### To destroy the infrastructure in the cloud created in the previous step, type: "terraform destroy" and confirm your intention by typing "yes".

# Optional
### Terraform supports several environment files with different parameters, e.g. `dev` and `prod`.
You can leave the `vars.tf` file unchanged (this is the preferred option). Then you need to change the necessary parameters in one of the `*.auto.tfvars` files to use in `terraform apply` with the `-var-file parameter`. For example, to use variables from `example.auto.tfvars` file, you need to change this file to meet your requirements and run the command: `terraform apply -var-file="./vars/example.auto.tfvars"`.

## DNS and NAT
There is possibility to include in the deployment process DNS names and NAT gateway setup. For this purpose there is a terraform configs `dns.tf` and `nat.tf`. In order to include them in the deployment, you need to move them or copy them from the templates folder to the level above - in the folder with the main terraform configuration files. Then run `terraform apply`. It doesn't matter if you did a deployment without dns and nat files before, terraform will build the new services into the configuration.
