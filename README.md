# Sample .NET Application

## Description

This is a very basic ASP.NET 2.2 WebAPI project. It creates an example RESTful API with the following endpoints:

- `GET /api/values`
- `POST /api/values` (accepts a JSON string in request body)
- `GET /api/values/{id}`
- `PUT /api/values/{id}` (accepts a JSON string in request body)
- `DELETE /api/values/{id}`

The API also comes with the following administrative endpoints:

- `GET /health` (basic ASP.NET Web API healthcheck)
- `GET /metrics` (Prometheus compatible metrics)

The intention here is provide a simple APS.NET web service that can be containerized and deployed to Kubernetes and Openshift.

## Build

### .NET binaries

        dotnet publish -c Release -o out sample-dotnet-app

### Container Image

        docker build -t sample-dotnet-app sample-dotnet-app

## Run Locally

Once built, the container can be started using a command like below.

        docker run -d -p 8080:80 \
                -v $PWD/config/appusers.json:/app/config/appusers.json \
                -e VALUES_SERVICE_TYPE="simple" \
                --name sample-dotnet-app sample-dotnet-app

Things to note:

- `-v $PWD/config/appusers.json:/app/config/appusers.json`: This mounts the configuration file containing authorized users into the container instance. One of these users must be specified using Basic Authentication on each API request. If not provided, the app becomes unusable as all requests will return a `401 UNAUTHORIZED`.

- `-e VALUES_SERVICE_TYPE="simple"`: To play around with polymorphism and dependency injection, two implementations of the underlying service that manages values storage have been provided. Use this environment variable to pick between them. Available choices are `simple` and `default`. As expected, if not specified, the `default` implementation is used.

## Deploy to Kubernetes Cluster

There are references for following deployment strategies in this repository. Each supports deploying to any Kubernetes cluster (including Openshift). Consult the README for each for further details.

* [Basic Kubernetes Deployment](deployment/k8s/README.md)
* [Helm](deployment/helm/README.md)

### CI/CD Pipeline

The repo also contains a [Jenkinsfile](Jenkinsfile) that can be used to setup a CI/CD pipeline for the application. Currently the pipeline supports deploying to a single environment. The pipeline has following dependencies:

1. Jenkins `username and password` credential named `image-registry-auth` that allows pushing the application container to the target docker registry.

1. An instance of the `tiller` server installed and running in the `tiller` namespace on the target Kubernetes cluster.

1. Jenkins `text` credential named `ocp-cluster-auth-token` that contains the token for the service account that will be used to connect to the target OCP cluster. This service account needs to have `edit` privileges in the `tiller` namespace.

1. Jenkins having the ability to run privileged containers so that the `dind` container instance can run.

#### Pipeline setup on Minishift

Following set of instructions is an example of how to achieve the above setup in a vanilla Minishift instance.

1. Install the Jenkins (ephemeral) service from the provided catalog under `jenkins` namespace.

1. Give the `jenkins` service account ability to run privileged containers.

        oc login -u system:admin
        oc adm policy add-scc-to-user privileged -n jenkins -z jenkins

1. Install the `tiller` server in Minishift under the `tiller` namespace. See [deployment/helm/README.md](deployment/helm/README.md) for instructions.

1. Give the `jenkins` service account `edit` privileges in `tiller` namespace.

        oc policy add-role-to-user edit "system:serviceaccount:jenkins:jenkins" -n tiller

1. Get the service token for `jenkins` service account.

        oc serviceaccounts get-token jenkins -n jenkins

1. In Jenkins, create the following three credentials:
    - Credentials that will be used to pull this Git repository.
    - `image-registry-auth` : A `username and password` credential containing the username nad password for the image registry where the `sample-dotnet-app` container image will be pushed.
    - `ocp-cluster-auth-token` : A `text` credential containing the Jenkins service account login token retrieved in previous step.

1. Create the `sample-project` namespace and give the `tiller` service account `edit` privileges.

        oc new-project sample-projects

        oc policy add-role-to-user edit \
            "system:serviceaccount:tiller:tiller" \
            -n sample-projects

## Consume

Following is an example flow to use the web service. The user information is contained in the [config/appusers.json](config/appusers.json) file.

**NOTE**: The `host:port` value of `localhost:8080` below is based on the application being deployed using [Run Locally](#run-locally) section above. Change the value as needed when consuming the application that has been deployed using other means.

- Health Check

        curl -i localhost:8080/health

- Prometheus compatible metrics

        curl -i localhost:8080/metrics

- Create a value

        curl -i -X POST \
                -H "Authorization: user:usrpass" \
                -H "Content-Type: application/json" \
                -d '"My Original Value"' \
                localhost:8080/api/values

- Fetch all stored values

        curl -i localhost:8080/api/values \
                -H "Authorization: user:usrpass"

- Change value

        curl -i -X PUT \
                -H "Authorization: user:usrpass" \
                -H "Content-Type: application/json" \
                -d '"My Changed Value"' \
                localhost:8080/api/values/1

- Fetch value by ID

        curl -i localhost:8080/api/values/1 \
                -H "Authorization: user:usrpass"

- Delete value by ID

        curl -i -X DELETE \
                -H "Authorization: user:usrpass" \
                localhost:8080/api/values/1
                
