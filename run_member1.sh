#!/bin/bash
# Run Member 1 analysis (compile first if needed)
mkdir -p out outputs/tables outputs/figures outputs/processed
javac -d out src/*.java && java -Xmx2g -cp out Member1Analysis
