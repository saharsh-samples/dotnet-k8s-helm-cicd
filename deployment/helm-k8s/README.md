# Deploy Using Helm

This directory contains files needed to deploy this application to any Kubernetes cluster **using [Helm](https://docs.helm.sh/)**.

## Overview

Like the basic Kubernetes deployment directory, this directory serves as a reference for deploying a typical cloud native web application to Kubernetes. Here the deployment is done using the popular Kubernetes application package managemenet tool, Helm. If approaching deployment to Kubernetes for the first time, go through [basic Kuberentes deployment](../k8s/README.md) first to familiaraize yourself with basic concepts.

## Dependencies

1. Install [`kubectl`](https://kubernetes.io/docs/tasks/tools/install-kubectl/) (or [`oc`](https://docs.openshift.com/container-platform/3.11/cli_reference/) if targetting Openshift). You will need this to be able to create namespaces/projects and `kubectl` context outside of helm.

1. The [`helm`](https://docs.helm.sh/using_helm/#install-helm) client.

## Setup

### Initialize Helm and Install `tiller`

As documented in [official docs](https://docs.helm.sh/using_helm/#initialize-helm-and-install-tiller), before using Helm you need to initialize the client and install `tiller` server in the target cluster. To do this, you need to do two things.

#### Set `kubectl` context

Creating the appropriate `kubectl` context is covered in detail in the basic Kubernetes deployment [README](../k8s/README). Once, the context is created, it must be set as the active context before using Helm. To check the current active context run the following.

        kubectl config current-context

To set the active context, run the following.

        kubectl config set-context [context]

#### Run `helm init`

Running `helm init` will initialize the Helm client using the current `kubectl` context and install `tiller` server in the target Kubernetes cluster.

##### Openshift

Since Openshift has authentication and authorization built-in, simply running `helm init` won't work. Instead, for Openshift, install `tiller` server using the following commands.

NOTE: Determine the version of Helm client by running `helm version`. Replace the `[helm-version]` below with the client version (e.g. `v2.13.1`).

        > helm init --client-only
        
        > export TILLER_NAMESPACE=tiller
        
        > oc new-project $TILLER_NAMESPACE

        > oc project $TILLER_NAMESPACE

        > oc process -f \
          https://github.com/openshift/origin/raw/master/examples/helm/tiller-template.yaml \
          -p TILLER_NAMESPACE="$TILLER_NAMESPACE" \
          -p HELM_VERSION=[helm-version] | \
          oc create -f -

If successful, running `helm version` now will show you status for both client and server.

### Setup Kubernetes namespace

If authorization is not setup across projects on the Kubernetes cluster, just creating the namespace is sufficient.

        kubectl create namespace sample-projects

However, a bit more setup is needed for Openshift. Instead of running the above, run the following for Openshift.

        > oc new-project sample-projects

        > oc policy add-role-to-user edit "system:serviceaccount:${TILLER_NAMESPACE}:tiller" -n sample-projects

### Application Container Image

Finally, before the application can be deployed, it needs to be hosted in a registry accessible to the Kubernetes cluster. The basic Kubernetes deployment [README](../k8s/README) covers this in detail. Once the application container image is pushed and accessible, make sure to set the `image` subfields correctly in [`values.yaml`](values.yaml).

## Install Helm chart

After setup is complete, installing the Helm charts is straightforward.

        helm install --name sample-dotnet-app --namespace sample-projects .
