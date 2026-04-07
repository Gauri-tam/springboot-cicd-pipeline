# CI/CD Setup Guide — Jenkins + Docker + Kubernetes (Minikube)
## Stack: Java Spring Boot · Docker · Minikube · Jenkins

---

## Project Structure

```
cicd-project/
├── Jenkinsfile                          ← Pipeline definition
├── spring-boot-app/
│   ├── Dockerfile                       ← Multi-stage Docker build
│   ├── pom.xml                          ← Maven build file
│   └── src/main/java/com/example/demo/
│       └── DemoApplication.java         ← Spring Boot app
└── k8s/
    ├── deployment.yaml                  ← K8s Deployment (2 replicas)
    ├── service.yaml                     ← NodePort Service (port 30080)
    └── configmap.yaml                   ← App environment config
```

---

## Prerequisites

Install these tools on your machine before starting:

| Tool       | Version  | Download |
|------------|----------|----------|
| Java JDK   | 17+      | https://adoptium.net |
| Maven      | 3.9+     | https://maven.apache.org |
| Docker     | Latest   | https://docs.docker.com/get-docker |
| Minikube   | Latest   | https://minikube.sigs.k8s.io |
| kubectl    | Latest   | https://kubernetes.io/docs/tasks/tools |
| Jenkins    | LTS      | https://www.jenkins.io/download |

---

## STEP 1 — Start Minikube

```bash
# Start Minikube with enough resources
minikube start --cpus=2 --memory=4096 --driver=docker

# Verify it's running
minikube status
kubectl get nodes
```

---

## STEP 2 — Install & Start Jenkins

### Option A: Run Jenkins via Docker (Recommended)

```bash
docker run -d \
  --name jenkins \
  --restart=always \
  -p 8090:8080 \
  -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v $(which minikube):/usr/local/bin/minikube \
  -v ~/.kube:/root/.kube \
  -v ~/.minikube:/root/.minikube \
  jenkins/jenkins:lts
```

> NOTE: Mounting docker.sock gives Jenkins access to Docker on your host.
> Mounting .kube and .minikube gives Jenkins access to your cluster.

### Option B: Install Jenkins on Host (Linux)

```bash
# Ubuntu/Debian
wget -q -O - https://pkg.jenkins.io/debian/jenkins.io.key | sudo apt-key add -
sudo sh -c 'echo deb http://pkg.jenkins.io/debian-stable binary/ > /etc/apt/sources.list.d/jenkins.list'
sudo apt update && sudo apt install jenkins -y
sudo systemctl start jenkins
```

### Get Initial Admin Password

```bash
# Docker installation:
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword

# Host installation:
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
```

Open Jenkins at: http://localhost:8090

---

## STEP 3 — Configure Jenkins

### 3.1 Install Plugins

Go to: Manage Jenkins → Plugins → Available Plugins

Install these plugins:
- Git Plugin
- Pipeline
- Maven Integration
- Docker Pipeline
- Kubernetes CLI Plugin (optional)

### 3.2 Configure Tools

Go to: Manage Jenkins → Global Tool Configuration

**JDK:**
- Click "Add JDK"
- Name: `JDK-17`
- JAVA_HOME: `/path/to/your/java17`  (e.g., `/usr/lib/jvm/java-17-openjdk`)

**Maven:**
- Click "Add Maven"
- Name: `Maven-3.9`
- Check "Install automatically" → Select version 3.9.x

### 3.3 Add Docker & kubectl to Jenkins PATH (Docker install)

```bash
docker exec -u root jenkins bash -c "
  apt-get update && apt-get install -y docker.io kubectl && \
  usermod -aG docker jenkins
"
docker restart jenkins
```

---

## STEP 4 — Create Jenkins Pipeline Job

1. Go to Jenkins Dashboard → **New Item**
2. Enter name: `spring-boot-cicd`
3. Select **Pipeline** → Click OK
4. Under **Pipeline** section:
   - Definition: `Pipeline script from SCM`
   - SCM: `Git`
   - Repository URL: your Git repo URL
   - Branch: `*/main`
   - Script Path: `Jenkinsfile`
5. Click **Save**

---

## STEP 5 — Build & Deploy

```bash
# Push your code to Git, then in Jenkins:
# Click "Build Now" on your pipeline job

# Watch the build in Console Output
# Each stage runs: Checkout → Build → Docker → Minikube → Deploy → Verify
```

---

## STEP 6 — Access Your Application

```bash
# Get Minikube IP
minikube ip

# Access the app (NodePort 30080)
curl http://$(minikube ip):30080

# Or open in browser:
minikube service spring-boot-app-service --url
```

Expected response: `Hello from Spring Boot on Kubernetes!`

---

## Useful Commands

```bash
# Check pod status
kubectl get pods -l app=spring-boot-app

# View pod logs
kubectl logs -l app=spring-boot-app --tail=50

# Describe deployment
kubectl describe deployment spring-boot-app

# Scale up/down
kubectl scale deployment spring-boot-app --replicas=3

# Roll back if needed
kubectl rollout undo deployment/spring-boot-app

# View rollout history
kubectl rollout history deployment/spring-boot-app

# Watch Minikube dashboard
minikube dashboard
```

---

## CI/CD Flow Diagram

```
Developer pushes code
        │
        ▼
   Git Repository
        │
        ▼ (webhook or manual trigger)
   Jenkins Pipeline
        │
   ┌────┴────────────────────────────────────────┐
   │  Stage 1: Checkout                          │
   │  Stage 2: Maven Build + Unit Tests          │
   │  Stage 3: Docker Build (multi-stage)        │
   │  Stage 4: Load image → Minikube             │
   │  Stage 5: kubectl apply → K8s Deployment    │
   │  Stage 6: Rollout Verify + Health Check     │
   └────┬────────────────────────────────────────┘
        │
        ▼
   Kubernetes (Minikube)
   ┌──────────────────────────────┐
   │  Deployment (2 replicas)     │
   │  ┌──────────┐ ┌──────────┐  │
   │  │  Pod 1   │ │  Pod 2   │  │
   │  │ :8080    │ │ :8080    │  │
   │  └──────────┘ └──────────┘  │
   │         NodePort Service     │
   │         port: 30080          │
   └──────────────────────────────┘
        │
        ▼
   http://<minikube-ip>:30080
```

---

## Troubleshooting

**Jenkins can't find Docker:**
```bash
# Add jenkins user to docker group (host install)
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

**Minikube not reachable from Jenkins container:**
```bash
# Make sure .kube and .minikube are mounted with correct permissions
ls -la ~/.kube/config   # should be readable
minikube update-context  # refresh kube context
```

**Image not found in Minikube:**
```bash
# List images available inside Minikube
minikube image ls | grep spring-boot

# Re-load manually
minikube image load spring-boot-app:latest
```

**Pods crashing (CrashLoopBackOff):**
```bash
kubectl describe pod <pod-name>
kubectl logs <pod-name> --previous
```
