FROM eclipse-temurin:22-jre-alpine

WORKDIR /usr/app
COPY build/install/BLT .

ENTRYPOINT ["/usr/app/bin/BLT"]