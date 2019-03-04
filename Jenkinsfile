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

pipeline {

    environment {

        appName            = "sample-dotnet-app"
        helmChartDirectory = "deployment/helm"
        helmChartFile      = "${helmChartDirectory + '/Chart.yaml'}"

        imageRepo = "saharshsingh/sample-dotnet-app"

        k8sClusterUrl        = "https://192.168.99.100:8443"
        tillerNS             = "tiller"
        productionNamespace  = "sample-projects"
        qaNamespace          = "${productionNamespace + '-qa'}"
        developmentNamespace = "${productionNamespace + '-dev'}"
    }

    // no default agent/pod to stand up
    agent none 

    stages {

        stage('Initialize') {

            agent any

            steps {

                // set build version from helm chart and current branch
                script {
                    readFile(helmChartFile).split('\r|\n').each({ line ->
                        if(line.trim().startsWith("version")) {
                            def version = line.replaceFirst(".*version.*(\\d+\\.\\d+\\.\\d+).*", "\$1")
                            if(!"master".equals(BRANCH_NAME)) {
                                version = version + '-' + BRANCH_NAME
                                version = version.replace('/', '-')
                            }
                            env.buildVersion         = version
                            env.buildVersionWithHash = version + '-' + sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                        }
                    })
                }
            }
        }

        // Build and deliver application container image
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

                // build dotnet binaries
                container('dotnet') {
                    sh 'dotnet publish -c Release -o out ${appName}'
                }

                // build container image
                container('buildah') {

                    script {

                        sh 'buildah bud -t "${imageRepo}:${buildVersion}" ${appName}'

                        if("master".equals(BRANCH_NAME) || "develop".equals(BRANCH_NAME)) {
                            withCredentials([usernamePassword(credentialsId:'image-registry-auth', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                                sh '''
                                buildah push --creds="$USER:$PASS" "${imageRepo}:${buildVersion}"
                                '''
                            }
                        }
                    }
                }
            }
        }

        stage('Tag and Increment Version') {

            when { branch 'master' }

            agent any

            steps {
                withCredentials([usernamePassword(credentialsId:'git-auth', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                    sh '''

                    # Configure Git for tagging/committing and pushing
                    ORIGIN=$(echo "$(git config remote.origin.url)" | sed -E "s~(http[s]*://)~\\1${USER}@~")
                    git config --global user.email "jenkins@email.com"
                    git config --global user.name "Jenkins"
                    printf "exec echo \\"${PASS}\\"" > $HOME/askgitpass.sh
                    chmod a+x $HOME/askgitpass.sh

                    # Tag Release Candidate
                    TAG="v${buildVersion}"
                    git tag -a "$TAG" -m "Release $TAG successfully deployed"
                    GIT_ASKPASS=$HOME/askgitpass.sh git push "$ORIGIN" "$TAG"

                    # Increment version on main branch
                    main_branch="develop"
                    git checkout $main_branch
                    git reset --hard origin/$main_branch

                    new_version="$(echo "${buildVersion}" | cut -d '.' -f 1,2).$(($(echo "${buildVersion}" | cut -d '.' -f 3) + 1))"
                    sed -i -E s/"version.*[0-9]+\\.[0-9]+\\.[0-9]+"/"version: $new_version"/ ${helmChartFile}

                    git commit -a -m "Updated version from ${buildVersion} to $new_version"
                    GIT_ASKPASS=$HOME/askgitpass.sh git push "$ORIGIN" $main_branch
                    '''
                }
            }

        }

        stage('Deploy to Staging') {

            when { anyOf { branch 'master'; branch 'develop' } }

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
                        def namespace = developmentNamespace
                        def releaseName = appName + '-dev'
                        def imagePullPolicy = 'Always'
                        if("master".equals(BRANCH_NAME)) {
                            namespace = qaNamespace
                            releaseName = appName + '-qa'
                            imagePullPolicy = 'IfNotPresent'
                        }

                        withCredentials([string(credentialsId:'k8s-cluster-auth-token', variable: 'token')]) {
                            helmInstall(tillerNS, k8sClusterUrl, token, namespace, buildVersionWithHash, imageRepo, buildVersion, imagePullPolicy, releaseName, helmChartDirectory)
                        }
                    }

                }
            }
        }

        stage('Confirm Promotion to Production') {

            when { branch 'master' }

            steps {
                timeout(time : 5, unit : 'DAYS') {
                    input "Promote ${imageRepo}:${buildVersion} to production?"
                }
            }

        }

        stage('Promote to Production') {

            when { branch 'master' }

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

                    withCredentials([string(credentialsId:'k8s-cluster-auth-token', variable: 'token')]) {
                        helmInstall(tillerNS, k8sClusterUrl, token, productionNamespace, buildVersionWithHash, imageRepo, buildVersion, 'IfNotPresent', appName, helmChartDirectory)
                    }

                }
            }
        }

    }
}
