#!/bin/sh

docker build -t medischeduler .
docker run -p 8080:8080 medischeduler