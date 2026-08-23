#!/bin/bash
cd "$(dirname "$0")"
mkdir -p out
javac -d out src/main/java/com/arumdaun/church/*.java
java -cp out com.arumdaun.church.Main
