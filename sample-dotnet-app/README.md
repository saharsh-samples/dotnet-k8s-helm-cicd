# Sample .NET Application

## Description

This is a very basic ASP.NET 2.2 WebAPI project. It creates an example RESTful API with the following endpoints:

- `GET /api/values`
- `POST /api/values` (accepts a JSON string in request body)
- `GET /api/values/{id}`
- `PUT /api/values/{id}` (accepts a JSON string in request body)
- `DELETE /api/values/{id}`

The API also comes with the following administrative endpoints:

- `GET /info` (metadata about the app, i.e. name, description, and version)
- `GET /health` (basic ASP.NET Web API healthcheck)
- `GET /metrics` (Prometheus compatible metrics)

The intention here is provide a simple APS.NET web service that can be containerized and deployed to Kubernetes and Openshift.

## Build

### .NET binaries and Container Image

        docker build -t sample-dotnet-app .

## Run Locally

Once built, the container can be started using a command like below.

        docker run -d -p 8080:80 \
                -v $PWD/config/appusers.json:/app/config/appusers.json \
                -e VALUES_SERVICE_TYPE="simple" \
                --name sample-dotnet-app sample-dotnet-app

Things to note:

- `-v $PWD/config/appusers.json:/app/config/appusers.json`: This mounts the configuration file containing authorized users into the container instance. One of these users must be specified using Basic Authentication on each API request. If not provided, the app becomes unusable as all requests will return a `401 UNAUTHORIZED`.

- `-e VALUES_SERVICE_TYPE="simple"`: To play around with polymorphism and dependency injection, two implementations of the underlying service that manages values storage have been provided. Use this environment variable to pick between them. Available choices are `simple` and `default`. As expected, if not specified, the `default` implementation is used.

## Consume

Following is an example flow to use the web service. The user information is contained in the [config/appusers.json](config/appusers.json) file.

**NOTE**: The `host:port` value of `localhost:8080` below is based on the application being deployed using [Run Locally](#run-locally) section above. Change the value as needed when consuming the application that has been deployed using other means.

- Application metadata.

        curl -i localhost:8080/info

NOTE: These values can be overridden using `APP_NAME`, `APP_DESCRIPTION`, and `APP_VERSION` environment variables.

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
                
