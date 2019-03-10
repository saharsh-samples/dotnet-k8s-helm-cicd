# Kubernetes Deployment

This directory contains files needed to deploy this application to any Kubernetes cluster.

## Dependencies

- The [deploy.sh](deploy.sh) script has only been tested for the [`bash`](https://en.wikipedia.org/wiki/Bash_(Unix_shell)) shell.
- The script also relies on [`envsubst`](https://www.gnu.org/software/gettext/manual/html_node/envsubst-Invocation.html) tool to resolve variables in Kubernetes templates. The key-value pairs in [deployment.properties](deployment.properties) are exported to the script's environment and then resolved throughout all templates using the `envsubst` tool.
- The script uses [`kubectl`](https://kubernetes.io/docs/tasks/tools/install-kubectl/) to communicate with the target Kubernetes cluster.

## Overview

This directory contains the following files:

1. `deployment.properties` : This file contains values for variables used throughout the YAML files used as Kubernetes templates. The file is intended to be edited so it can provide proper configuration for the target Kubernetes cluster during specific deployments.

1. **YAML Files** : The various YAML files throughout the project are Kubernetes templates but they contain variables that need to be resolved. The variables must be in either `$VARIABLE` or `${VARIABLE}` format. The values to be substituted for these variables are contained in the [`deployment.properties`](deployment.properties) file. These files are prefixed with two digits and a dash, i.e. '`##-`'. This is done to enforce order in which the templates are processed by `kubectl apply`.

1. `deploy.sh` : This is the deployment script that is run to deploy to a Kubernetes cluster. It takes two optional arguments. The `-c [context]` argument can be used to specify the `kubectl` context to use. If not provided, `minikube` is used as the context by default. The `-p [properties file path]` argument can be used to supply an alternate properties file. By default [`deployment.properties`](deployment.properties) is used.

## Deployment

### Application Container Image

Before the application can be deployed, it needs to be hosted in a registry accessible to the Kubernetes cluster.

#### Remote Clusters

For remote clusters, this typically means pushing the image to be deployed into a public or private container registry. For public registries, pushing the image into the registry so it is discoverable over Internet is sufficient. For private registries, a secret may need to be added in the cluster as well so that the images can be pulled. See following documentation for more information.

- [Openshift](https://docs.openshift.com/container-platform/3.5/dev_guide/managing_images.html#using-image-pull-secrets)
- [Azure Kubernetes Service](https://docs.microsoft.com/en-us/azure/container-registry/container-registry-auth-aks)

#### Minikube and Minishift

For local clusters, the external container registry solution from above will still work. Additionally, one of the following can be done as well.

##### Run a Local Docker Registry

One option is to run a local docker registry and push your image there. For example, following commands push the local `sample-dotnet-app` image build for this project to the local registry.

    docker run -d -p 5000:5000 --name registry registry:2.7.1

    docker tag sample-dotnet-app localhost:5000/sample-dotnet-app

    docker push localhost:5000/sample-dotnet-app

Now the `APP_IMAGE` variable in [`deployment.properties`](deployment.properties) can be set to use the image from this local registry. For the Minikube VM, this registry is running at a different address than `localhost`, typically `192.168.99.1`. Using the address of registry as seen by the Minikube VM (let's say, for example, `192.168.99.1`), the `APP_IMAGE` variable can be set to `192.168.99.1:5000/sample-dotnet-app`.

##### Use Minikube Docker Daemon

Another option is to use the Minikube Docker daemon directly. The host machine's Docker client can be configured to use Minikube Docker daemon instead of the default one by running the following.

    eval $(minikube docker-env)

Now `docker build` will push images directly to the Minikube Docker daemon. This approach has one downside as it requires that `imagePullPolicy:Always` in the `deployment` object be turned off.

### Configuring `deployment.properties`

The `deployment.properties` file is intended to capture all environment specific configuration. This way the YAML template files themselves don't have to change to capture environment specific details.

Important configuration items:

- `APP_NAMESPACE` : Value assigned to this is used as the value for the `namespace` metadata field for all Kubernetes objects defined in the YAML template files.

- `APP_IMAGE` : Defines where the deployment object will find the image to use for running an instance of the application. See the [Application Container Image](#application-container-image) section above for details.

- `APP_SERVICE_TYPE` : Service type to assign to the `sample-dotnet-app` service resource. Typically leave this to `ClusterIP`. However, if you want to be able to access the application from outside the cluster (i.e. your laptop instead of the Minikube VM) when running in Minkube, change this to `NodePort`. This is recommended since it's difficult to setup ingress capability on Minikube.

- `APP_INGRESS_HOST` : This will be set as the value for `host` field in the ingress spec. The default is based on Minishift.

- `APP_ENV_*` : Variables starting with this prefix follow a convention of this repository to indicate that the value will be injected as an environment variable into each application pod instance.

### Creating `kubectl` context

The `kubectl` context is important as it defines how `kubectl` will discover and authenticate itself to the target Kubernetes cluster. These contexts are stored by default in `$HOME/.kube/config`.

- Minikube and Minishift automatically create their contexts with the names `minikube` and `minishift` respectively.

- Context for Openshift clusters can be created using `oc login`, but the automatically created context name is not very convenient. Alternatively, `oc config set-context` command can also be used. See [documentation](https://docs.openshift.com/container-platform/3.11/cli_reference/manage_cli_profiles.html#manually-configuring-cli-profiles).

- Contexts for any Kubernetes cluster can be manually created using the `kubectl config set-context` command. Run `kubectl config set-context --help` for documentation.

### Kubernetes Namespace

The application will be deployed into a specific Kubernetes namespace. This namespace must exist before the deployment script can be run. If the namespace already exists, then the user associated with the `kubectl` context, see [above](#creating-kubectl-context), must have the appropriate privileges needed to complete the deployment.

If the namespace does not exist, it can be created using the following `kubectl` command.

    kubectl create namespace [namespace-name]

For Openshift, the namespace should be created as a project.

    oc new-project [namespace-name]

### Running the Deployment Script

Before running the script, make sure:

- [Dependencies](#dependencies) are met.
- The appropriate `kubectl` [context](#creating-kubectl-context) has been created.
- [Application container image](#application-container-image) is accessible.
- [Kubernetes namespace](#kubernetes-namespace) has been created.
- The [deployment.properties](deployment.properties) file has been properly configured. You can pass another file path using the `-p` option.

Run the deployment script by passing it the appropriate context.

    ./deploy.sh -c [kubectl-context]

If deploying to Minikube, you don't have to specify the context.

    ./deploy.sh

Running the script does the following:

1. The specified `kubectl` context is set.
1. All key value pairs specified in `deployment.properties` are set in the script's environment.
1. All files in this directory ending with `.yml` are processed using `envsubst`. The resulting resolved file is placed in the `.resolved` subdirectory.
1. `kubectl apply -f .resolved` is run to process all templates in the `.resolved` directory. If the objects don't exist they are created. Otherwise, any changes are applied to objects and the application is redeployed.

**NOTE** : It is safe to run `deploy.sh` from any directory on the system.
