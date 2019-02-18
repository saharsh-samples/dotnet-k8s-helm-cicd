pipeline {

    // define environment
    environment {
        imageRepo = "saharshsingh/sample-dotnet-app"
        imageTag = "${new java.text.SimpleDateFormat('yyyyMMdd-HHmmss').format(new java.util.Date())}"

        ocpClusterUrl = "https://192.168.99.100:8443"
        tillerNS = "tiller"
        appNS = "sample-projects"
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

                // build dotnet binaries
                container('dotnet') {
                    sh 'dotnet publish -c Release -o out sample-dotnet-app'
                }

                // build container image
                container('dind') {
                    
                    withCredentials([usernamePassword(credentialsId:'image-registry-auth', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                        sh '''
                        IMAGE="${imageRepo}:${imageTag}"

                        echo "$PASS" | docker login --username "$USER" --password-stdin

                        docker build -t $IMAGE sample-dotnet-app
                        docker push $IMAGE
                        '''
                    }
                }
            }
        }

        stage('Deploy') {

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

                // Deploy using helm install
                container('helm') {

                    withCredentials([string(credentialsId:'ocp-cluster-auth-token', variable: 'TOKEN')]) {
                        sh '''
                        export HOME="`pwd`"
                        export TILLER_NAMESPACE=${tillerNS}

                        kubectl config set-cluster development --server="${ocpClusterUrl}" --insecure-skip-tls-verify
                        kubectl config set-credentials jenkins --token="$TOKEN"
                        kubectl config set-context helm --cluster=development --namespace="${tillerNS}" --user=jenkins
                        kubectl config use-context helm

                        helm install \
                            --name sample-dotnet-app \
                            --namespace "${appNS}" \
                            --set image.repository="${imageRepo}" \
                            --set image.tag="${imageTag}" \
                            deployment/helm
                        '''
                    }

                    
                }
            }
        }

    }
}
