/**
 * Installs Helm to designated Kubernetes cluster
 *
 * Params:
 *   - tillerNs         : Kubernetes Namespace where Tiller server is deployed
 *   - k8sCluster       : Kubernetes Cluster URL
 *   - clusterAuthToken : Token used to authenticate to the Kubernetes Cluster.
 *                        This will be set in the `kubectl` context
 *   - namespace        : Kubernetes namespace where application will be
 *                        deployed
 *   - appVersion       : Version of application to pass to the application
 *                        (application specific)
 *   - ingressHost      : Ingress Host to set given target environment
 *   - imageRepo        : Registry URL (including app subpath) from where to pull
 *                        application container image
 *   - imageTag         : Tag of application container image to pull
 *   - imagePullPolicy  : Image pull policy to set in application's deployment
 *                        template
 *   - releaseName      : Helm Release to use
 *   - chartDirectory   : Location of directory in repo containing Helm charts
 *                        (relative to repo top level) 
 */
def helmInstall(tillerNs, k8sCluster, clusterAuthToken, namespace, appVersion, ingressHost, imageRepo, imageTag, imagePullPolicy, releaseName, chartDirectory) {
    sh '''

    export HOME="`pwd`"
    export TILLER_NAMESPACE="''' + tillerNS + '''"

    kubectl config set-cluster development --server="''' + k8sCluster + '''" --insecure-skip-tls-verify
    kubectl config set-credentials jenkins --token="''' + clusterAuthToken + '''"
    kubectl config set-context helm --cluster=development --namespace="''' + namespace + '''" --user=jenkins
    kubectl config use-context helm

    helm upgrade --install --wait \
        --namespace "''' + namespace + '''" \
        --set app.version="''' + appVersion + '''" \
        --set ingress.host="''' + ingressHost + '''" \
        --set image.repository="''' + imageRepo + '''" \
        --set image.tag="''' + imageTag + '''" \
        --set image.pullPolicy="''' + imagePullPolicy + '''" \
        ''' + releaseName + ''' \
        ''' + chartDirectory
}

/**
 * REFERENCE CI/CD PIPELINE FOR KUBERNETES NATIVE .NET APPLICATION
 */
pipeline {

    parameters {

        // Application Properties
        string(name: 'appName', defaultValue: 'dotnet-k8s-helm-sample', description: 'Used as the base in Helm Release names')
        string(name: 'appDirectory', defaultValue: 'sample-dotnet-app', description: 'Relative path to .NET code and Dockerfile')
        string(name: 'helmChartDirectory', defaultValue: 'deployment/helm-k8s', description: 'Relative path to Helm chart and templates')
        string(name: 'sourceRegistry', defaultValue: 'docker.io/saharshsingh', description: 'Registry where image will be pused for long term storage')

        // Cluster Properties
        string(name: 'k8sClusterUrl', defaultValue: 'https://192.168.99.100:8443', description: 'Target cluster for all deployments')
        string(name: 'productionNamespace', defaultValue: 'sample-projects', description: 'Production namespace. Appended with -dev and -qa for those environments')
        string(name: 'tillerNS', defaultValue: 'tiller', description: 'Namespace on K8S cluster where tiller server is installed')
        string(name: 'devIngressHost', defaultValue: 'dotnet-k8s-helm-sample-sample-projects-dev.192.168.99.100.nip.io', description:'Ingress Host to set when deploying in Dev environment.')
        string(name: 'qaIngressHost', defaultValue: 'dotnet-k8s-helm-sample-sample-projects-qa.192.168.99.100.nip.io', description:'Ingress Host to set when deploying in QA environment.')
        string(name: 'prodIngressHost', defaultValue: 'dotnet-k8s-helm-sample-sample-projects.192.168.99.100.nip.io', description:'Ingress Host to set when deploying in Production environment.')

        // Jenkins Properties
        string(name: 'k8sCloudForDynamicSlaves', defaultValue: 'openshift', description: 'Cloud name for Kubernetes cluster where Jenkins slave pods will be spawned')
        string(name: 'imageRegistryCredentialId', defaultValue: 'image-registry-auth', description: 'ID of Jenkins credential containing container image registry username and password')
        string(name: 'k8sTokenCredentialId', defaultValue: 'k8s-cluster-auth-token', description: 'ID of Jenkins credential containing Kubernetes Cluster authentication token for Helm deploys')
        string(name: 'gitCredentialId', defaultValue: 'git-auth', description: 'ID of Jenkins credential containing Git server username and password')
        string(name: 'confirmationTimeoutValue', defaultValue: '5', description: 'Integer indicating length of time to wait for manual confirmation')
        string(name: 'confirmationTimeoutUnits', defaultValue: 'DAYS', description: 'Time unit to use for CONFIRMATION_WAIT_VALUE')

        // Git Properties
        string(name: 'mainBranch', defaultValue: 'develop', description: 'Main branch of Git repostory. This is the source and destination of feature branches')
        string(name: 'releaseBranch', defaultValue: 'master', description: 'Release branch of Git repostory. Merges to this trigger releases (Git tags and version increments)')
    }

    environment {

        // Application Properties
        appName            = "${appName}"
        appDirectory       = "${appDirectory}"
        helmChartDirectory = "${helmChartDirectory}"
        helmChartFile      = "${helmChartDirectory + '/Chart.yaml'}"
        imageRepo          = "${sourceRegistry + '/' + appName}"

        // Cluster Properties
        k8sClusterUrl        = "${k8sClusterUrl}"
        tillerNS             = "${tillerNS}"
        productionNamespace  = "${productionNamespace}"
        qaNamespace          = "${productionNamespace + '-qa'}"
        developmentNamespace = "${productionNamespace + '-dev'}"
        prodIngressHost      = "${prodIngressHost}"
        qaIngressHost        = "${qaIngressHost}"
        devIngressHost       = "${devIngressHost}"

        // Jenkins Properties
        k8sCloudForDynamicSlaves  = "${k8sCloudForDynamicSlaves}"
        imageRegistryCredentialId = "${imageRegistryCredentialId}"
        k8sTokenCredentialId      = "${k8sTokenCredentialId}"
        gitCredentialId           = "${gitCredentialId}"
        confirmationTimeoutValue  = "${confirmationTimeoutValue}"
        confirmationTimeoutUnits  = "${confirmationTimeoutUnits}"

        // Git Properties
        mainBranch    = "${mainBranch}"
        releaseBranch = "${releaseBranch}"
    }

    // no default agent/pod to stand up
    agent none 

    stages {

        /**
         * STAGE - INITIALIZE
         *
         * The intention of this stage is to do any initialization that modifies the 
         * pipeline environment before running any of the other stages.
         */
        stage('Initialize') {

            agent any

            steps {

                // set build version from helm chart and current branch
                script {

                    // Read Pod templates for dynamic slaves from files
                    env.buildahAgentYaml = readFile '.jenkins/buildah-agent.yml'
                    env.helmAgentYaml = readFile '.jenkins/helm-agent.yml'

                    // Read Helm Chart file line by line
                    readFile(helmChartFile).split('\r|\n').each({ line ->

                        // Look for line that starts with 'appVersion'
                        if(line.trim().startsWith("appVersion")) {

                            // Strip out everything on the line except the semantic version (i.e. #.#.#)
                            def version = line.replaceFirst(".*appVersion.*(\\d+\\.\\d+\\.\\d+).*", "\$1")

                            // If not on release branch, append branch name to semantic version
                            if(! releaseBranch.equals(BRANCH_NAME)) {
                                version = version + '-' + BRANCH_NAME
                                // feature branches may have the 'feature/branch-name' structure
                                // replace any '/' with '-' to keep version useable as image tag
                                version = version.replace('/', '-')
                            }

                            // Set version information to build environment
                            env.buildVersion         = version
                            // Set version + "git commit hash" information to environment
                            env.buildVersionWithHash = version + '-' + sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                        }
                    })
                }
            }
        }

        /**
         * STAGE - Build and deliver application container image
         *
         * Uses saharshsingh/container-management:1.0 image to build .NET binaries,
         * create container image, and push the container image to registry for long
         * term storage
         */
        stage('Build and deliver container image') {

            // 'Build and deliver' agent pod template
            agent {
                kubernetes {
                    cloud k8sCloudForDynamicSlaves
                    label 'buildah'
                    yaml buildahAgentYaml
                }
            }

            steps {

                // build (and optionally deliver) container image
                container('buildah') {

                    script {

                        sh 'buildah bud -t "${imageRepo}:${buildVersion}" ${appDirectory}'

                        // only push to registry for branches where deploy stages won't be skipped
                        if(releaseBranch.equals(BRANCH_NAME) || mainBranch.equals(BRANCH_NAME)) {
                            withCredentials([usernamePassword(credentialsId: imageRegistryCredentialId, usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                                sh 'buildah push --creds="$USER:$PASS" "${imageRepo}:${buildVersion}"'

                                // if releasing, slide the latest tag
                                if(releaseBranch.equals(BRANCH_NAME)) {
                                    sh '''
                                    buildah tag "${imageRepo}:${buildVersion}" "${imageRepo}:latest"
                                    buildah push --creds="$USER:$PASS" "${imageRepo}:latest"
                                    '''
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * STAGE - Tag and Increment Version
         *
         * Only executes on release branch builds. Creates a Git tag on current commit
         * using version from Helm chart in repository. Also, checks out the HEAD of
         * main branch and increments the patch component of the Helm chart version
         */
        stage('Tag and Increment Version') {

            when { branch releaseBranch }

            agent any

            steps {
                withCredentials([usernamePassword(credentialsId: gitCredentialId, usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                    sh '''

                    # Configure Git for tagging/committing and pushing
                    ORIGIN=$(echo "$(git config remote.origin.url)" | sed -E "s~(http[s]*://)~\\1${USER}@~")
                    git config --global user.email "jenkins@email.com"
                    git config --global user.name "Jenkins"
                    printf "exec echo \\"${PASS}\\"" > $HOME/askgitpass.sh
                    chmod a+x $HOME/askgitpass.sh

                    # Tag Release Candidate
                    TAG="v${buildVersion}"
                    git tag -a "$TAG" -m "Release $TAG created and delivered"
                    GIT_ASKPASS=$HOME/askgitpass.sh git push "$ORIGIN" "$TAG"

                    # Increment version on main branch
                    git checkout ${mainBranch}
                    git reset --hard origin/${mainBranch}

                    new_version="$(echo "${buildVersion}" | cut -d '.' -f 1,2).$(($(echo "${buildVersion}" | cut -d '.' -f 3) + 1))"
                    sed -i -E s/"appVersion.*[0-9]+\\.[0-9]+\\.[0-9]+"/"appVersion: $new_version"/ ${helmChartFile}

                    git commit -a -m "Updated version from ${buildVersion} to $new_version"
                    GIT_ASKPASS=$HOME/askgitpass.sh git push "$ORIGIN" ${mainBranch}
                    '''
                }
            }

        }

        /**
         * STAGE - Deploy to Staging
         *
         * Only executes on main and release branch builds. Deploys to either 'Dev'
         * or 'QA' environment, based on whether main or release branch is being
         * built.
         */
        stage('Deploy to Staging') {

            when { anyOf { branch releaseBranch; branch mainBranch } }

            // 'Deploy' agent pod template
            agent {
                kubernetes {
                    cloud k8sCloudForDynamicSlaves
                    label 'helm'
                    yaml helmAgentYaml
                }
            }

            steps {

                // Deploy to K8s using helm install
                container('helm') {

                    script {

                        // by default use values for dev envrionment
                        def namespace       = developmentNamespace
                        def ingressHost     = devIngressHost
                        def releaseName     = appName + '-dev'
                        def imagePullPolicy = 'Always'

                        // if on release branch, override them for QA environment
                        if(releaseBranch.equals(BRANCH_NAME)) {
                            namespace       = qaNamespace
                            ingressHost     = qaIngressHost
                            releaseName     = appName + '-qa'
                            imagePullPolicy = 'IfNotPresent'
                        }

                        withCredentials([string(credentialsId: k8sTokenCredentialId, variable: 'token')]) {
                            helmInstall(tillerNS, k8sClusterUrl, token, namespace, buildVersionWithHash, ingressHost, imageRepo, buildVersion, imagePullPolicy, releaseName, helmChartDirectory)
                        }
                    }

                }
            }
        }

        /**
         * STAGE - Confirm Promotion to Production
         *
         * Pipeline halts for configured amount of time and waits for someone to click Proceed or Abort.
         */
        stage('Confirm Promotion to Production') {

            when { branch releaseBranch }

            steps {
                timeout(time : Integer.parseInt(confirmationTimeoutValue), unit : confirmationTimeoutUnits) {
                    input "Promote ${imageRepo}:${buildVersion} to production?"
                }
            }

        }

        /**
         * STAGE - Promote to Production
         *
         * Once promotion is confirmed in previous step, build is promoted to production
         */
        stage('Promote to Production') {

            when { branch releaseBranch }

            // 'Deploy' agent pod template
            agent {
                kubernetes {
                    cloud k8sCloudForDynamicSlaves
                    label 'helm'
                    yaml helmAgentYaml
                }
            }

            steps {

                // Deploy to K8s using helm install
                container('helm') {

                    withCredentials([string(credentialsId: k8sTokenCredentialId, variable: 'token')]) {
                        helmInstall(tillerNS, k8sClusterUrl, token, productionNamespace, buildVersionWithHash, prodIngressHost, imageRepo, buildVersion, 'IfNotPresent', appName, helmChartDirectory)
                    }

                }
            }
        }

    }
}
