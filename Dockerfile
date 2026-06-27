
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

RUN apt-get update && \
    apt-get install -y curl gnupg && \
    curl -fsSL https://scala.jfrog.io/artifactory/debian/gpg | gpg --dearmor -o /usr/share/keyrings/sbt-archive-keyring.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/sbt-archive-keyring.gpg] https://scala.jfrog.io/artifactory/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    apt-get update && \
    apt-get install -y sbt && \
    rm -rf /var/lib/apt/lists/*

COPY build.sbt version.sbt ./
COPY project ./project
COPY cooking-domain ./cooking-domain
COPY cooking-backend ./cooking-backend

RUN sbt "project backend" clean stage

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd -r recipe && useradd -r -g recipe recipe

COPY --from=builder /app/cooking-backend/target/universal/stage /app

RUN chown -R recipe:recipe /app

USER recipe

EXPOSE 9000

CMD ["bin/cooking-backend"]
