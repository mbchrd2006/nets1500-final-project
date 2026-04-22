#!/bin/bash
# Compile all Java source files
mkdir -p out
javac -d out src/*.java && echo "Compilation successful."
