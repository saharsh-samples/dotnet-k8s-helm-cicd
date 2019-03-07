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
 *   - imageRepo        : Registry URL (including app subpath) from where to pull
 *                        application container image
 *   - imageTag         : Tag of application container image to pull
 *   - imagePullPolicy  : Image pull policy to set in application's deployment
 *                        template
 *   - releaseName      : Helm Release to use
 *   - chartDirectory   : Location of directory in repo containing Helm charts
 *                        (relative to repo top level) 
 */
def helmInstall(tillerNs, k8sCluster, clusterAuthToken, namespace, appVersion, imageRepo, imageTag, imagePullPolicy, releaseName, chartDirectory) {
    sh '''

    export HOME="`pwd`"
    export TILLER_NAMESPACE="''' + tillerNS + '''"

    kubectl config set-cluster development --server="''' + k8sCluster + '''" --insecure-skip-tls-verify
    kubectl config set-credentials jenkins --token="''' + clusterAuthToken + '''"
    kubectl config set-context helm --cluster=development --namespace="''' + namespace + '''" --user=jenkins
    kubectl config use-context helm

    helm upgrade --install \
        --namespace "''' + namespace + '''" \
        --set app.version="''' + appVersion + '''" \
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
        string(name: 'appName', defaultValue: 'sample-dotnet-app', description: 'Used as the base in Helm Release names')
        string(name: 'appDirectory', defaultValue: 'sample-dotnet-app', description: 'Relative path to .NET code and Dockerfile')
        string(name: 'helmChartDirectory', defaultValue: 'deployment/helm', description: 'Relative path to Helm chart and templates')
        string(name: 'sourceRegistry', defaultValue: 'docker.io/saharshsingh', description: 'Registry where image will be pused for long term storage')

        // Cluster Properties
        string(name: 'k8sClusterUrl', defaultValue: 'https://192.168.99.100:8443', description: 'Target cluster for all deployments')
        string(name: 'productionNamespace', defaultValue: 'sample-projects', description: 'Production namespace. Appended with -dev and -qa for those environments')
        string(name: 'tillerNS', defaultValue: 'tiller', description: 'Namespace on K8S cluster where tiller server is installed')

        // Jenkins Properties
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

        // Jenkins Properties
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

                    // Read Helm Chart file line by line
                    readFile(helmChartFile).split('\r|\n').each({ line ->

                        // Look for line that starts with 'version'
                        if(line.trim().startsWith("version")) {

                            // Strip out everything on the line except the semantic version (i.e. #.#.#)
                            def version = line.replaceFirst(".*version.*(\\d+\\.\\d+\\.\\d+).*", "\$1")

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
         * Uses microsoft/dotnet:2.2-sdk and saharshsingh/container-management:1.0
         * images to build .NET binaries, create container image, and push the container
         * image to ACR for long term storage 
         */
        stage('Build and deliver container image') {

            // 'Build and deliver' agent pod template
            agent {
                kubernetes {
                    cloud 'openshift'
                    label 'dotnet'
                    yaml """
apiVersion: v1
kind: Pod
spec:
    containers:
      - name: jnlp
        image: 'jenkinsci/jnlp-slave:alpine'
      - name: dotnet
        image: 'microsoft/dotnet:2.2-sdk'
        imagePullPolicy: IfNotPresent
        command:
          - /bin/cat
        tty: true
      - name: buildah
        image: 'saharshsingh/container-management:1.0'
        imagePullPolicy: IfNotPresent
        command:
          - /bin/cat
        tty: true
        securityContext:
          privileged: true
        volumeMounts:
          - mountPath: /var/lib/containers
            name: buildah-storage
    volumes:
      - name: buildah-storage
        emptyDir: {}
"""
                }
            }

            steps {

                // build dotnet binaries (ideally this should include automated testing)
                container('dotnet') {
                    sh 'dotnet publish -c Release -o out ${appDirectory}'
                }

                // build (and optionally deliver) container image
                container('buildah') {

                    script {

                        sh 'buildah bud -t "${imageRepo}:${buildVersion}" ${appDirectory}'

                        // only push to registry for branches where deploy stages won't be skipped
                        if(releaseBranch.equals(BRANCH_NAME) || mainBranch.equals(BRANCH_NAME)) {
                            withCredentials([usernamePassword(credentialsId: imageRegistryCredentialId, usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                                sh '''
                                buildah push --creds="$USER:$PASS" "${imageRepo}:${buildVersion}"
                                '''
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
                    sed -i -E s/"version.*[0-9]+\\.[0-9]+\\.[0-9]+"/"version: $new_version"/ ${helmChartFile}

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
                    cloud 'openshift'
                    label 'helm'
                    yaml """
apiVersion: v1
kind: Pod
spec:
    containers:
      - name: jnlp
        image: 'jenkinsci/jnlp-slave:alpine'
      - name: helm
        image: 'saharshsingh/helm:2.12.3'
        imagePullPolicy: IfNotPresent
        command:
          - /bin/cat
        tty: true
"""
                }
            }

            steps {

                // Deploy to K8s using helm install
                container('helm') {

                    script {

                        // by default use values for dev envrionment
                        def namespace = developmentNamespace
                        def releaseName = appName + '-dev'
                        def imagePullPolicy = 'Always'

                        // if on release branch, override them for QA environment
                        if(releaseBranch.equals(BRANCH_NAME)) {
                            namespace = qaNamespace
                            releaseName = appName + '-qa'
                            imagePullPolicy = 'IfNotPresent'
                        }

                        withCredentials([string(credentialsId: k8sTokenCredentialId, variable: 'token')]) {
                            helmInstall(tillerNS, k8sClusterUrl, token, namespace, buildVersionWithHash, imageRepo, buildVersion, imagePullPolicy, releaseName, helmChartDirectory)
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
                    cloud 'openshift'
                    label 'helm'
                    yaml """
apiVersion: v1
kind: Pod
spec:
    containers:
      - name: jnlp
        image: 'jenkinsci/jnlp-slave:alpine'
      - name: helm
        image: 'saharshsingh/helm:2.12.3'
        imagePullPolicy: IfNotPresent
        command:
          - /bin/cat
        tty: true
"""
                }
            }

            steps {

                // Deploy to K8s using helm install
                container('helm') {

                    withCredentials([string(credentialsId: k8sTokenCredentialId, variable: 'token')]) {
                        helmInstall(tillerNS, k8sClusterUrl, token, productionNamespace, buildVersionWithHash, imageRepo, buildVersion, 'IfNotPresent', appName, helmChartDirectory)
                    }

                }
            }
        }

    }
}
