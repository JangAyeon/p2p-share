#!/bin/bash

# Git 브랜치 정리 스크립트
# 원격에 없는 로컬 브랜치를 삭제합니다.
# 사용법: ./scripts/git-cleanup.sh 또는 scripts/git-cleanup.sh

set -e

# 프로젝트 루트 디렉토리로 이동
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# Fetch and prune remote branches
git checkout main
git fetch --prune
git pull
# Get list of all remote branches
remote_branches=$(git branch -r)

# Initialize an empty array to hold branches to be deleted
branches_to_delete=()

# Loop over all local branches
for branch in $(git branch --format="%(refname:short)"); do
    # If local branch is not in remote branches
    if [[ $remote_branches != *"$branch"* ]]; then
        # Add branch to the list of branches to be deleted
        branches_to_delete+=($branch)
    fi
done

# If there are branches to be deleted
if [ ${#branches_to_delete[@]} -ne 0 ]; then
    echo "The following branches do not exist on the remote and will be deleted:"
    for branch in "${branches_to_delete[@]}"; do
        echo $branch
    done

    # Ask user for confirmation
    read -p "Do you want to delete these branches? (y/n) " -n 1 -r
    echo    # move to a new line
    if [[ $REPLY =~ ^[Yy]$ ]]
    then
        # If user confirms, delete the branches
        for branch in "${branches_to_delete[@]}"; do
            git branch -d $branch
        done
    fi
else
    echo "No local branches to delete."
fi