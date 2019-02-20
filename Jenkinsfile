pipeline {

    environment {

        imageRepo = "saharshsingh/sample-dotnet-app"

        ocpClusterUrl = "https://192.168.99.100:8443"
        tillerNS = "tiller"

        appName = "sample-dotnet-app"
        productionNamespace = "sample-projects"
        qaNamespace = "${productionNamespace + '-qa'}"
        developmentNamespace = "${productionNamespace + '-dev'}"
    }

    // no default agent/pod to stand up
    agent none 

    stages {

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

                // set build version from version.txt file and current branch
                script {
                    def version = readFile 'version.txt'
                    if(!"master".equals(BRANCH_NAME)) {
                        version = version + '-' + BRANCH_NAME
                    }
                    env.buildVersion = version
                }

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
                            deployment/helm
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
                            deployment/helm
                        '''
                    }

                }
            }
        }

    }
}
