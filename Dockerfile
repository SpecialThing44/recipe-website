
FROM eclipse-temurin:17-jdk as builder

WORKDIR /app

RUN apt-get update && \
    apt-get install -y curl && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add && \
    apt-get update && \
    apt-get install -y sbt && \
    rm -rf /var/lib/apt/lists/*

COPY build.sbt version.sbt ./
COPY project ./project
COPY cooking-domain ./cooking-domain
COPY cooking-backend ./cooking-backend

RUN sbt "project backend" clean stage

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN groupadd -r recipe && useradd -r -g recipe recipe

COPY --from=builder /app/cooking-backend/target/universal/stage /app

RUN chown -R recipe:recipe /app

USER recipe

EXPOSE 9000

CMD ["bin/cooking-backend", "-Dplay.http.secret.key=${SECRET_KEY:-changeme}"]
