# .NET + Kubernetes + Helm Reference Project

This project is intended as a reference project for containerized .NET applications deployed to Kubernetes using CI/CD pipelines. The project contains a fully functional ASP.NET 2.2 WebAPI web service, Kuberetes templates and helm charts needed to deploy the application, and a Jenkinsfile that can be used to stand up a CI/CD pipeline for the project.

## Sample .NET Application

The sample application is a very basic ASP.NET 2.2 WebAPI project. Details regarding the application itself are contained in the [sample-dotnet-app](sample-dotnet-app) subdirectory.

## Kubernetes Deployment

There are references for following deployment strategies in this repository. Each supports deploying to any Kubernetes cluster (including Openshift). Consult the README for each for further details.

* [Basic Kubernetes Deployment](deployment/k8s/README.md)
* [Helm](deployment/helm-k8s/README.md)

## CI/CD Pipeline

The repo also contains a [Jenkinsfile](Jenkinsfile) that can be used to setup a CI/CD pipeline for the application. The pipeline is intended to run as changes to branches are pushed to the origin repository of the application (e.g. git server hosted by Github or Bitbucket). The pipeline, depending on the branch, builds the deployable container image, deploys the application to intended environments, and automates versioning related tasks (e.g. creating git tags and incrementing application version).

### Branching Strategy

The pipeline assumes the repository uses a [Gitflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)-based branching strategy. Specifically, the branching model has the following characteristics:

1. The main branch is `develop`. All feature branches are created from this branch. Upon completion, the feature branches are merged back into the `develop` branch and deleted.

1. The `develop` branch is occasionally merged into the `master` branch to create release candidates.

1. The `master` branch is never merged into `develop`. Instead, if fixes need to be done for a release, then a branch should be created from `master`, and any changes should be both merged into `master` and cherry-picked back into `develop`.

### Versioning

The pipeline automates majority of the activity related to versioning of the application. The version is expected to follow [typical semantic versioning](https://en.wikipedia.org/wiki/Software_versioning#Sequence-based_identifiers), and the version of the software is stored in the `version` field of the [`Chart.yaml`](deployment/helm-k8s/Chart.yaml) file used for Helm installs. This version number is expected to change under two circumstances:

1. It is automatically changed by the pipeline as a result of changes pushed to the `master` branch. In this case, the pipeline tags the `HEAD` of the `master` branch with the version number contained in the [`Chart.yaml`](deployment/helm-k8s/Chart.yaml) file. After pushing the tag to the origin repository, the pipeline then checks out the current `HEAD` of the `develop` branch, increments the third digit of the semantic version by `one`, and pushes the new `HEAD` of develop to the origin repository.

1. It is manually changed by the maintainers of the application if the major and/or minor versions need to be incremented. The change will be applied like any other change to the application source - using a feature branch that is merged into `develop` first. 

### Pipeline Stages

Following sections describe each stage of the CI/CD pipeline and indicate the branch(es) for which the stage is run.

#### Initialize

**Run for:** All branches

The intention of this stage is to do any initialization that modifies the pipeline environment before running any of the other stages. Currently, this includes the following:

* Load shared Groovy modules - These are reusable functions written in Groovy that encapsulate high level tasks related to a specific tool or concern. They are organized in files, and each file contains one or more functions that belong to the same grouping (e.g. Helm related tasks). Each file is loaded as a namespace and bound to an appropriately named field in the `modules` global variable.

* Load Kubernetes pod templates - These are YAML files describing the pod used to run certain stages in the pipeline. For example, deployment stages are run with the [`helm-agent`](.jenkins/agents/helm-agent.yml) pod template. These templates are stored in external files to reduce clutter from the main `Jenkinsfile`. They are loaded in this stage and bound to environment variables so they can be used by later stages.

* Set necessary environment variables - Following environment variables are set so they can be used by later stages.
    * **buildVersion** - The build version is largely based on the version stored in the [`Chart.yaml`](deployment/helm-k8s/Chart.yaml) file. For `master` branch, the build version is exactly that. For other branches, the build version is determined by appending a '-' followed by the name of the branch (e.g. 1.0.0-develop or 2.3.5-some-feature-request).
    * **buildVersionWithHash** - This is a combination of `buildVersion` and the short git commit hash. It is injected into the `sample-dotnet-app` instance itself as the `APP_VERSION` environment variable. This value is echoed back by the app in the `/info` endpoint.

#### Build and Deliver Container Image

**Run for:** All branches

This is the build stage. The build stage is divided into two sections:

* Build .NET binaries (typically this should also include running automated tests and other code analysis checks) using the multi stage [`Dockerfile`](sample-dotnet-app/Dockerfile)

* Build the deployable container image. If the branch is `master` or `develop`, deliver the container image to the image registry for long term storage.

This stage is run using the [`buildah-agent`](.jenkins/agents/buildah-agent.yml) which contains the [`buildah`](https://github.com/containers/buildah) tool.

NOTE: The `buildah` container needs to run as priveleged to execute successfully.

#### Tag and Increment Version

**Run for:** Only `master` branch

After a successful "build and deliver" stage, this stage runs the automated versioning tasks if the pipeline is running for the `master` branch.

* A Git tag is created using the build version and pushed to the origin repository. 

* Then, the pipeline checks out the `develop` branch, updates the `version` field in the [`Chart.yaml`](deployment/helm-k8s/Chart.yaml) file by incrementing the last digit by `one`, commits the changes, and pushes the changes to the origin repository.

This stage is run using `agent any` as no specific Kubernetes pods need to be spawned to execute this stage.

#### Deploy to Staging

**Run for:** Only `master` and `develop` branches

After a successful "build and deliver" stage, this stage deploys the application to a staging environment if the pipeline is running for `develop` or `master` branches. For `develop` branch, this means deploying the application to the `sample-projects-dev` namespace. On the other hand, for `master` branch, this means starting the release process by deploying to the `sample-projects-qa` namespace.

The deploy is done using the `helm upgrade` command with the `--install` flag. This allows first time deploys to an environment to execute as a new install, while subsequent deploys are executed as upgrades. The Helm release name, `sample-dotnet-app-dev` for `develop` and `sample-dotnet-app-qa` for master, is reused each time.

Since the version in `Chart.yaml` may not change for multiple deploys off `develop` branch, the same image tags will often be updated with new container images. For this reason an image pull policy of `Always` is used for deployments off the `develop` branch.

On the other hand, since the version will always be incremented for subsequent builds on the `master` branch, a pull policy of `IfNotPresent` is used for deployments off the `master` branch. This also falls in line with the "Build once and Promote" best practice for CI/CD pipelines. This way, the release candidate is built once, deployed to the "QA" environment, and then promoted to the production environment if deemed acceptable.

This stage is run using the [`helm-agent`](.jenkins/agents/helm-agent.yml) which contains the [Kubernetes](https://kubernetes.io/docs/reference/kubectl/overview/) and [Helm](https://github.com/helm/helm/releases/tag/v2.13.1) CLI clients.

#### Confirm Promotion to Production

**Run for:** Only `master` branch

This stage allows for a manual approval gateway before a version of the application is promoted to the production environment. The pipeline simply waits up to 5 days (configurable) for a human to click "Proceed" or "Abort". This is intended to allow time for manual testing to take place in the "QA" environment.

This stage is run using `agent any` as no specific Kubernetes pods need to be spawned to execute this stage.

#### Promote to Production

**Run for:** Only `master` branch

This is the final stage of the pipeline and only run for the `master` branch. Application versions that pass testing and verfication in the "QA" environment are deployed to the "Production" environment. The deployment is done in the same manner as it is done in the "Deploy to Staging" stage above.

This stage is run using the [`helm-agent`](.jenkins/agents/helm-agent.yml) which contains the [Kubernetes](https://kubernetes.io/docs/reference/kubectl/overview/) and [Helm](https://github.com/helm/helm/releases/tag/v2.13.1) CLI clients.

### Pipeline Setup

The pipeline is created as a "Multi Branch Pipeline" in Jenkins.

#### 

Following dependencies must be met before being able to start using the pipeline.

1. Jenkins configured with the [`Kubernetes plugin`](https://github.com/jenkinsci/kubernetes-plugin).  

1. Jenkins `username and password` credential named `git-auth` that allows Jenkins and the pipeline to fully consume this Git repository.

1. Jenkins `username and password` credential named `image-registry-auth` that allows pushing the application container to the target docker registry.

1. An instance of the `tiller` server installed and running in the `tiller` namespace on the target Kubernetes cluster.

1. The `sample-projects`, `sample-projects-qa`, and `sample-projects-dev` namespaces created in Kubernetes with `tiller` having the ability to manage projects inside them.

1. Jenkins `username and password` credential named `k8s-cluster-auth` that contains the target Kubernetes cluster URL and authentication token for the service account that will be used to connect to the cluster. This service account needs to have `edit` privileges in the `tiller` namespace.

1. The namespace where Jenkins is deployed should have a `jenkins-privileged` service account that has the ability to run privileged containers. This is needed so that the `buildah` container instance can run as privileged.

#### Pipeline setup on Minishift

Following set of instructions is an example of how to achieve the above setup in a vanilla Minishift instance.

1. Install the Jenkins (ephemeral) service from the provided catalog under `jenkins` namespace.

1. Create a `jenkins-privileged` service account and grant it the ability to run privileged containers. Run the following as a user with cluster admin access.

        oc login -u system:admin
        oc create sa jenkins-privileged -n jenkins
        oc adm policy add-scc-to-user privileged -n jenkins -z jenkins-privileged
        oc login -u developer

1. Install the `tiller` server in Minishift under the `tiller` namespace. See [deployment/helm-k8s/README.md](deployment/helm-k8s/README.md) for instructions.

1. Give the `tiller` service account `edit` privileges in `tiller` namespace.

        oc policy add-role-to-user edit -z tiller -n tiller

1. Get the service token for `tiller` service account.

        oc serviceaccounts get-token tiller -n tiller

1. In Jenkins, create the following three credentials:

    * `git-auth`: A `username and password` credential that will be used to pull this Git repository.

    * `image-registry-auth` : A `username and password` credential containing the username and password for the image registry where the `sample-dotnet-app` container image will be pushed.

    * `k8s-cluster-auth` : A `username and password` credential containing the Kubernetes cluster URL and `tiller` service account login token retrieved in previous step.

1. Create the `dev`, `qa`, and `production` namespaces and give the `tiller` service account `edit` privileges.

        oc new-project sample-projects-dev

        oc new-project sample-projects-qa

        oc new-project sample-projects

        oc policy add-role-to-user edit \
            "system:serviceaccount:tiller:tiller" \
            -n sample-projects-dev

        oc policy add-role-to-user edit \
            "system:serviceaccount:tiller:tiller" \
            -n sample-projects-qa

        oc policy add-role-to-user edit \
            "system:serviceaccount:tiller:tiller" \
            -n sample-projects

1. Create a "Multi Branch Pipeline" in Jenkins using this Git repository as the source and `git-auth` credentials created above as the credentials. At a minimum, setup periodic polling of all branches so builds can trigger automatically. However, it is strongly recommended as a best practice to setup webhooks from the repository server so builds are triggered as push events from Git server rather than as a consequence of polling from Jenkins.
