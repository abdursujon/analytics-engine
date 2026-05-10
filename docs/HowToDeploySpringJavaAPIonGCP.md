# HowToDeploySpringJavaAPIonGCP

This guide documents the **exact working path** to deploy a **Spring Boot (Java, Gradle)** API to **Google Cloud Run** using **Docker + Cloud Build**.  

---

## Prerequisites

- Google Cloud project created
- Billing enabled
- Cloud Shell access
- Spring Boot project with:
  - `Dockerfile`
  - `build.gradle`
  - `gradlew`
  - App listening on port **8080**

---

## 1. Set Correct Project

List projects and confirm ID:
```bash
gcloud projects list
```

Set project:
```bash
gcloud config set project analytics-engine
```

Verify:
```bash
gcloud config get-value project
```

---

## 2. Enable Required APIs

```bash
gcloud services enable run.googleapis.com artifactregistry.googleapis.com cloudbuild.googleapis.com
```

---

## 3. Choose Region (London)

London region:
```
europe-west2
```

---

## 4. Create Artifact Registry (Docker Repo)

```bash
gcloud artifacts repositories create app-repo \
  --repository-format=docker \
  --location=europe-west2 \
  --description="docker repo"
```

---

## 5. Configure Docker Auth (one-time)

```bash
gcloud auth configure-docker europe-west2-docker.pkg.dev
```

---

## 6. Build JAR (Outside Docker)

Cloud Shell cannot download Gradle inside Docker reliably.

Build first:
```bash
./gradlew bootJar
```

Ensure:
```
build/libs/*.jar
```

---

## 7. Final Dockerfile (Runtime Only)

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
CMD ["java","-jar","app.jar"]
```

---

## 8. Use Cloud Build (IMPORTANT)

**Do NOT use `docker push` from Cloud Shell.**  
Cloud Shell networking is unstable.

Use Cloud Build instead.

From repo root:
```bash
gcloud builds submit --tag europe-west2-docker.pkg.dev/analytics-engine/app-repo/app
```

What this does:
- Uploads source
- Builds Docker image
- Pushes image to Artifact Registry

---

## 9. Fix Cloud Build Permissions (one-time)

Grant Cloud Build permission to push images:
```bash
gcloud projects add-iam-policy-binding analytics-engine \
  --member="serviceAccount:506639246506@cloudbuild.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"
```

---

## 10. Deploy to Cloud Run

```bash
gcloud run deploy spring-data-analysis \
  --image europe-west2-docker.pkg.dev/analytics-engine/app-repo/app \
  --region europe-west2 \
  --allow-unauthenticated
```

Cloud Run requirements:
- App listens on port **8080**
- No extra config needed for Spring Boot defaults

---

## 11. Result

- Cloud Run prints a public HTTPS URL
- First request may take **1–5 seconds** (cold start)
- Subsequent requests are instant
- Scales to zero when idle (free tier)

---

## Deployment Flow Summary

1. `git pull`
2. `./gradlew bootJar`
3. `gcloud builds submit`
4. `gcloud run deploy`

---

## Notes

- No VM required
- No SSH
- No Docker push from Cloud Shell
- No GitHub Actions required (can be added later)
- Cloud Run free tier is sufficient for low traffic APIs

# After changes made in git repo this has be done to make changes in api
git reset --hard
git clean -fd
git pull
./gradlew bootJar
gcloud builds submit --tag europe-west2-docker.pkg.dev/analytics-engine/app-repo/app
gcloud run deploy spring-data-analysis \
--image europe-west2-docker.pkg.dev/analytics-engine/app-repo/app \
--region europe-west2 \
--allow-unauthenticated
