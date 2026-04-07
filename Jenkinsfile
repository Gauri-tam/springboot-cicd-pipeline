  pipeline {
    agent any

    environment {
    APP_NAME        = 'spring-boot-app'
    IMAGE_TAG       = "v${BUILD_NUMBER}"
    DOCKER_IMAGE    = "${APP_NAME}:${IMAGE_TAG}"
    K8S_MANIFEST    = 'k8s'
    APP_DIR         = 'spring-boot-app'
    }

    tools {
    maven 'Maven-3.9'     // Must match name in Jenkins > Global Tool Configuration
    jdk   'JDK-17'        // Must match name in Jenkins > Global Tool Configuration
    }

    stages {
    
    // ─────────────────────────────────────────
    stage('Checkout') {
    // ─────────────────────────────────────────
    steps {
    echo '📥 Checking out source code...'
    checkout scm
    }
    }
    
    // ─────────────────────────────────────────
    stage('Build & Test') {
    // ─────────────────────────────────────────
    steps {
    dir("${APP_DIR}") {
    echo '🔨 Building Spring Boot application...'
    sh 'mvn clean package -B'
    }
    }
    post {
    always {
    junit "${APP_DIR}/target/surefire-reports/*.xml"
    }
    }
    }
    
    // ─────────────────────────────────────────
    stage('Build Docker Image') {
    // ─────────────────────────────────────────
    steps {
    dir("${APP_DIR}") {
  echo "🐳 Building Docker image: ${DOCKER_IMAGE}"
    sh """
    docker build -t ${DOCKER_IMAGE} .
    docker tag ${DOCKER_IMAGE} ${APP_NAME}:latest
    """
                }
            }
        }

        // ─────────────────────────────────────────
        stage('Load Image into Minikube') {
        // ─────────────────────────────────────────
            steps {
                echo '📦 Loading Docker image into Minikube...'
                sh """
    minikube image load ${DOCKER_IMAGE}
    minikube image load ${APP_NAME}:latest
    """
            }
        }

        // ─────────────────────────────────────────
        stage('Deploy to Kubernetes') {
        // ─────────────────────────────────────────
            steps {
                echo '🚀 Deploying to Minikube Kubernetes cluster...'
                sh """
  # Apply ConfigMap first
    kubectl apply -f ${K8S_MANIFEST}/configmap.yaml
    
    # Update image tag in deployment and apply
    kubectl set image deployment/${APP_NAME} \
    ${APP_NAME}=${DOCKER_IMAGE} \
    --record 2>/dev/null || true
    
    # Apply full manifests (idempotent)
    kubectl apply -f ${K8S_MANIFEST}/deployment.yaml
    kubectl apply -f ${K8S_MANIFEST}/service.yaml
    
    # Update image to the new build tag
    kubectl set image deployment/${APP_NAME} \
    ${APP_NAME}=${DOCKER_IMAGE}
    """
            }
        }

        // ─────────────────────────────────────────
        stage('Verify Deployment') {
        // ─────────────────────────────────────────
            steps {
                echo '✅ Verifying rollout...'
                sh """
    kubectl rollout status deployment/${APP_NAME} --timeout=120s
    echo ""
    echo "=== Pods ==="
    kubectl get pods -l app=${APP_NAME}
    echo ""
    echo "=== Service ==="
    kubectl get svc ${APP_NAME}-service
    echo ""
    echo "=== App URL ==="
    echo "http://\$(minikube ip):30080"
    """
            }
        }
    }

    post {
        success {
            echo """
    ════════════════════════════════════
    ✅  PIPELINE SUCCESS  — Build #${BUILD_NUMBER}
  Image  : ${DOCKER_IMAGE}
  Access : http://\$(minikube ip):30080
    ════════════════════════════════════
    """
        }
        failure {
            echo """
    ════════════════════════════════════
    ❌  PIPELINE FAILED  — Build #${BUILD_NUMBER}
    Check console output for details.
    ════════════════════════════════════
    """
            // Roll back on failure
            sh "kubectl rollout undo deployment/${APP_NAME} || true"
  }
    always {
    // Clean up old local Docker images (keep last 3)
    sh """
    docker images ${APP_NAME} --format '{{.Tag}}' | \
    sort -t'v' -k2 -n | head -n -3 | \
    xargs -I{} docker rmi ${APP_NAME}:{} 2>/dev/null || true
    """
  }
  }
  }