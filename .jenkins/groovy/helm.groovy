/**
 * Data object containing necessary configuration needed for Helm install
 */
class InstallContext {

    // Kubernetes Namespace where Tiller server is deployed
    String tillerNS

    // Kubernetes Cluster URL
    String k8sCluster

    // Token used to authenticate to the Kubernetes Cluster. This will be set in the `kubectl` context
    String clusterAuthToken

    // Kubernetes namespace where application will be deployed
    String namespace

    // Version of application to pass to the application (application specific)
    String appVersion

    // Ingress Host to set given target environment
    String ingressHost

    // Registry URL (including app subpath) from where to pull application container image
    String imageRepo

    // Tag of application container image to pull
    String imageTag

    // Image pull policy to set in application's deployment template
    String imagePullPolicy

    // Helm Release to use
    String releaseName

    // Location of directory in repo containing Helm charts (relative to repo top level)
    String chartDirectory
}

/**
 * returns a new instance of InstallContext containing no data
 */
def newInstallContext() {
    return new InstallContext()
}

/**
 * Installs Helm to designated Kubernetes cluster
 *
 * Params:
 *   - context : data object containing necessary configuration for install
 */
def install(InstallContext context) {
    sh '''

    export HOME="`pwd`"
    export TILLER_NAMESPACE="''' + context.tillerNS + '''"

    kubectl config set-cluster development --server="''' + context.k8sCluster + '''" --insecure-skip-tls-verify
    kubectl config set-credentials jenkins --token="''' + context.clusterAuthToken + '''"
    kubectl config set-context helm --cluster=development --namespace="''' + context.namespace + '''" --user=jenkins
    kubectl config use-context helm

    helm upgrade --install --wait \
        --namespace "''' + context.namespace + '''" \
        --set app.version="''' + context.appVersion + '''" \
        --set ingress.host="''' + context.ingressHost + '''" \
        --set image.repository="''' + context.imageRepo + '''" \
        --set image.tag="''' + context.imageTag + '''" \
        --set image.pullPolicy="''' + context.imagePullPolicy + '''" \
        ''' + context.releaseName + ''' \
        ''' + context.chartDirectory
}

return this
