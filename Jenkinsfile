pipeline {

    environment {

        appName            = "sample-dotnet-app"
        helmChartDirectory = "deployment/helm"
        helmChartFile      = "${helmChartDirectory + '/Chart.yaml'}"

        imageRepo = "saharshsingh/sample-dotnet-app"

        ocpClusterUrl        = "https://192.168.99.100:8443"
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
                    readFile(helmChartFile).split('\r|\n').eachWithIndex({ line, count ->
                        if(line.trim().startsWith("version")) {
                            def version = line.replaceFirst(".*version.*(\\d+\\.\\d+\\.\\d+).*", "\$1")
                            if(!"master".equals(BRANCH_NAME)) {
                                version = version + '-' + BRANCH_NAME
                            }
                            env.buildVersion = version
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
      - name: dind
        image: 'docker:18.09.2-dind'
        imagePullPolicy: IfNotPresent
        securityContext:
          privileged: true
        volumeMounts:
          - mountPath: /var/lib/docker
            name: dind-storage
    volumes:
      - name: dind-storage
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
                container('dind') {

                    script {

                        sh 'docker build -t "${imageRepo}:${buildVersion}" ${appName}'

                        if("master".equals(BRANCH_NAME) || "develop".equals(BRANCH_NAME)) {
                            withCredentials([usernamePassword(credentialsId:'image-registry-auth', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                                sh '''
                                echo "$PASS" | docker login --username "$USER" --password-stdin
                                docker push "${imageRepo}:${buildVersion}"
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
                withCredentials([usernamePassword(credentialsId:'github-auth', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                    sh '''

                    # Configure Git for tagging/committing and pushing
                    git config --global user.email "jenkins@email.com"
                    git config --global user.name "Jenkins"
                    printf "exec echo \\"${PASS}\\"" > $HOME/askgitpass.sh
                    chmod a+x $HOME/askgitpass.sh

                    # Tag Release Candidate
                    git tag -a "v${buildVersion}" -m "Release v${buildVersion} successfully deployed"
                    GIT_ASKPASS=$HOME/askgitpass.sh git push https://${USER}@github.com/saharsh-samples/dotnet-k8s-helm-cicd "v${buildVersion}"

                    # Increment version on main branch
                    main_branch="develop"
                    git checkout $main_branch
                    git reset --hard origin/$main_branch

                    new_version="$(echo "${buildVersion}" | cut -d '.' -f 1,2).$(($(echo "${buildVersion}" | cut -d '.' -f 3) + 1))"
                    sed -i -E s/"version.*[0-9]+\\.[0-9]+\\.[0-9]+"/"version: $new_version"/ ${helmChartFile}

                    git commit -a -m "Updated version from ${buildVersion} to $new_version"
                    GIT_ASKPASS=$HOME/askgitpass.sh git push https://${USER}@github.com/saharsh-samples/dotnet-k8s-helm-cicd $main_branch
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
                        if("master".equals(BRANCH_NAME)) {
                            env.namespace = env.qaNamespace
                            env.helmRelease = env.appName + '-qa'
                        } else {
                            env.namespace = env.developmentNamespace
                            env.helmRelease = env.appName + '-dev'
                        }
                    }

                    withCredentials([string(credentialsId:'ocp-cluster-auth-token', variable: 'TOKEN')]) {
                        sh '''

                        export HOME="`pwd`"
                        export TILLER_NAMESPACE=${tillerNS}

                        kubectl config set-cluster development --server="${ocpClusterUrl}" --insecure-skip-tls-verify
                        kubectl config set-credentials jenkins --token="$TOKEN"
                        kubectl config set-context helm --cluster=development --namespace="${tillerNS}" --user=jenkins
                        kubectl config use-context helm

                        helm upgrade --install \
                            --namespace "${namespace}" \
                            --set image.repository="${imageRepo}" \
                            --set image.tag="${buildVersion}" \
                            ${helmRelease} \
                            ${helmChartDirectory}
                        '''
                    }

                }
            }
        }

        stage('Confirm Promotion to Production') {

            when { branch 'master' }

            steps {
                timeout(time : 5, unit : 'DAYS') {
                    input "Promote ${imageRepo}:${env.buildVersion} to production?"
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

                    withCredentials([string(credentialsId:'ocp-cluster-auth-token', variable: 'TOKEN')]) {
                        sh '''

                        export HOME="`pwd`"
                        export TILLER_NAMESPACE=${tillerNS}

                        kubectl config set-cluster development --server="${ocpClusterUrl}" --insecure-skip-tls-verify
                        kubectl config set-credentials jenkins --token="$TOKEN"
                        kubectl config set-context helm --cluster=development --namespace="${tillerNS}" --user=jenkins
                        kubectl config use-context helm

                        helm upgrade --install \
                            --namespace "${productionNamespace}" \
                            --set image.repository="${imageRepo}" \
                            --set image.tag="${buildVersion}" \
                            ${appName} \
                            ${helmChartDirectory}
                        '''
                    }

                }
            }
        }

    }
}
