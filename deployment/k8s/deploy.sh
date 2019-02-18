#!/bin/bash

# Deteremine script constants
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
KUBE_CONTEXT="minikube"
PROPERTIES_FILE="$SCRIPT_DIR/deployment.properties"
TEMPLATES_DIR="$SCRIPT_DIR/.resolved"

# Usage text
USAGE="
Usage: ${0} [-c kubernetes_context] [-p properties_file]

    Defaults:
        Kubernetes Context : $KUBE_CONTEXT
        Properties file : $PROPERTIES_FILE
"

# Process CLI args
while getopts ":c:p:h" opt; do
    case $opt in
        c )
            KUBE_CONTEXT="$OPTARG"
            ;;
        p )
            PROPERTIES_FILE="$OPTARG"
            ;;
        h )
            echo "$USAGE"
            exit 0
            ;;
        \? )
            echo "$USAGE"
            exit 1
            ;;
    esac
done

# Exit immediately on error
set -e

# Set correct Kubernetes context
kubectl config use-context "$KUBE_CONTEXT"

# Clean templates directory
if [ -d "$TEMPLATES_DIR" ]; then
    rm -r "$TEMPLATES_DIR";
fi
mkdir "$TEMPLATES_DIR"

# Resolve all template variables
eval $(grep -vE '^(\s*$|#)' "$PROPERTIES_FILE" | sed 's/^.*=/export &/')
for unresolved in `ls $SCRIPT_DIR/*.yml`
do
    echo "Resolving '$unresolved' ..."
    if [ -f "$unresolved" ]; then
        resolved="$TEMPLATES_DIR/$(basename "$unresolved")"
        envsubst < "$unresolved" > "$resolved"
    fi
    echo "Created '$resolved' ..."
    echo
done

# Deploy using resolved templates
kubectl apply -f "$TEMPLATES_DIR"
