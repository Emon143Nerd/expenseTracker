#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/server"
CP="../lib/*:./src:./resources"
mkdir -p out
javac -d out -cp "$CP" $(find src -name "*.java")
java -cp "out:../lib/*:resources" com.expensedash.server.ServerMain
