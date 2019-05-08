// Define global variable to hold dynamically loaded modules
// Modules will be loaded in 'Initialize' step
def modules = [:]

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
        string(name: 'productionNamespace', defaultValue: 'sample-projects', description: 'Production namespace. Appended with -dev and -qa for those environments')
        string(name: 'tillerNS', defaultValue: 'tiller', description: 'Namespace on K8S cluster where tiller server is installed')
        string(name: 'devIngressHost', defaultValue: 'dotnet-k8s-helm-sample-sample-projects-dev.192.168.99.100.nip.io', description:'Ingress Host to set when deploying in Dev environment.')
        string(name: 'qaIngressHost', defaultValue: 'dotnet-k8s-helm-sample-sample-projects-qa.192.168.99.100.nip.io', description:'Ingress Host to set when deploying in QA environment.')
        string(name: 'prodIngressHost', defaultValue: 'dotnet-k8s-helm-sample-sample-projects.192.168.99.100.nip.io', description:'Ingress Host to set when deploying in Production environment.')

        // Jenkins Properties
        string(name: 'k8sCloudForDynamicSlaves', defaultValue: 'openshift', description: 'Cloud name for Kubernetes cluster where Jenkins slave pods will be spawned')
        string(name: 'imageRegistryCredentialId', defaultValue: 'image-registry-auth', description: 'ID of Jenkins credential containing container image registry username and password')
        string(name: 'devClusterAuthCredentialId', defaultValue: 'k8s-cluster-auth', description: 'ID of Jenkins credential containing Development Cluster authentication for Helm deploys')
        string(name: 'qaClusterAuthCredentialId', defaultValue: 'k8s-cluster-auth', description: 'ID of Jenkins credential containing QA Cluster authentication for Helm deploys')
        string(name: 'prodClusterAuthCredentialId', defaultValue: 'k8s-cluster-auth', description: 'ID of Jenkins credential containing Production Cluster authentication for Helm deploys')
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
        tillerNS             = "${tillerNS}"
        productionNamespace  = "${productionNamespace}"
        qaNamespace          = "${productionNamespace + '-qa'}"
        developmentNamespace = "${productionNamespace + '-dev'}"
        prodIngressHost      = "${prodIngressHost}"
        qaIngressHost        = "${qaIngressHost}"
        devIngressHost       = "${devIngressHost}"

        // Jenkins Properties
        k8sCloudForDynamicSlaves    = "${k8sCloudForDynamicSlaves}"
        imageRegistryCredentialId   = "${imageRegistryCredentialId}"
        devClusterAuthCredentialId  = "${prodClusterAuthCredentialId}"
        qaClusterAuthCredentialId   = "${prodClusterAuthCredentialId}"
        prodClusterAuthCredentialId = "${prodClusterAuthCredentialId}"
        gitCredentialId             = "${gitCredentialId}"
        confirmationTimeoutValue    = "${confirmationTimeoutValue}"
        confirmationTimeoutUnits    = "${confirmationTimeoutUnits}"

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

                    // load modules
                    modules.helm   = load '.jenkins/groovy/helm.groovy'
                    modules.common = load '.jenkins/groovy/commonutils.groovy'

                    // Read Pod templates for dynamic slaves from files
                    env.buildahAgentYaml = readFile '.jenkins/agents/buildah-agent.yml'
                    env.helmAgentYaml    = readFile '.jenkins/agents/helm-agent.yml'

                    // Set version information to build environment
                    env.buildVersion         = modules.common.getVersionFromHelmChart(helmChartFile, releaseBranch)
                    // Set version + "git commit hash" information to environment
                    env.buildVersionWithHash = env.buildVersion + '-' + sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
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
                withCredentials([usernamePassword(credentialsId: gitCredentialId, usernameVariable: 'gitUser', passwordVariable: 'gitPassword')]) {
                    script {
                        modules.common.tagCommitAndIncrementVersion(gitUser, gitPassword, mainBranch, buildVersion, helmChartFile)
                    }
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
                        def clusterAuthId   = devClusterAuthCredentialId
                        def namespace       = developmentNamespace
                        def ingressHost     = devIngressHost
                        def releaseName     = appName + '-dev'
                        def imagePullPolicy = 'Always'

                        // if on release branch, override them for QA environment
                        if(releaseBranch.equals(BRANCH_NAME)) {
                            clusterAuthId   = qaClusterAuthCredentialId
                            namespace       = qaNamespace
                            ingressHost     = qaIngressHost
                            releaseName     = appName + '-qa'
                            imagePullPolicy = 'IfNotPresent'
                        }

                        withCredentials([usernamePassword(credentialsId: clusterAuthId, usernameVariable: 'clusterUrl', passwordVariable: 'token')]) {
                            script {

                                // define Helm install context
                                def context              = modules.helm.newInstallContext()
                                context.tillerNS         = tillerNS
                                context.k8sCluster       = clusterUrl
                                context.clusterAuthToken = token
                                context.namespace        = namespace
                                context.appVersion       = buildVersionWithHash
                                context.ingressHost      = ingressHost
                                context.imageRepo        = imageRepo
                                context.imageTag         = buildVersion
                                context.imagePullPolicy  = imagePullPolicy
                                context.releaseName      = releaseName
                                context.chartDirectory   = helmChartDirectory

                                // run Helm install
                                modules.helm.install(context)
                            }
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

                    withCredentials([usernamePassword(credentialsId: prodClusterAuthCredentialId, usernameVariable: 'clusterUrl', passwordVariable: 'token')]) {
                        script {

                            // define context
                            def context              = modules.helm.newInstallContext()
                            context.tillerNS         = tillerNS
                            context.k8sCluster       = clusterUrl
                            context.clusterAuthToken = token
                            context.namespace        = productionNamespace
                            context.appVersion       = buildVersionWithHash
                            context.ingressHost      = prodIngressHost
                            context.imageRepo        = imageRepo
                            context.imageTag         = buildVersion
                            context.imagePullPolicy  = 'IfNotPresent'
                            context.releaseName      = appName
                            context.chartDirectory   = helmChartDirectory

                            // run install
                            modules.helm.install(context)
                        }
                    }

                }
            }
        }

    }
}
