#!/bin/bash
#
# Gerrit Workflow Example Script
#
# This script demonstrates the basic Gerrit workflow using Git commands.
# It shows how to create changes, update them, and submit them using the
# refs/for virtual branch mechanism.
#
# Prerequisites:
# - Git installed
# - Access to a Gerrit server
# - commit-msg hook installed in your local repository

set -e  # Exit on error

# Configuration
GERRIT_URL="http://localhost:8080"  # Replace with your Gerrit URL
PROJECT_NAME="example-project"      # Replace with your project name
BRANCH="trunk"                      # The target branch

# Step 1: Clone the repository
echo "Step 1: Cloning the repository..."
if [ ! -d "$PROJECT_NAME" ]; then
    git clone "$GERRIT_URL/$PROJECT_NAME.git"
    cd "$PROJECT_NAME"
else
    cd "$PROJECT_NAME"
    git fetch origin
fi

# Step 2: Install the commit-msg hook if not already installed
echo "Step 2: Installing commit-msg hook..."
if [ ! -f ".git/hooks/commit-msg" ]; then
    curl -Lo .git/hooks/commit-msg "$GERRIT_URL/tools/hooks/commit-msg"
    chmod +x .git/hooks/commit-msg
fi

# Step 3: Create a local branch for your change
echo "Step 3: Creating a local branch..."
CHANGE_BRANCH="feature-$(date +%Y%m%d-%H%M%S)"
git checkout -b "$CHANGE_BRANCH" origin/$BRANCH

# Step 4: Make changes to the code
echo "Step 4: Making changes..."
echo "// Example change - $(date)" >> example.txt
git add example.txt

# Step 5: Commit the changes
echo "Step 5: Committing changes..."
git commit -m "Add example change

This is an example change to demonstrate the Gerrit workflow.
The commit-msg hook will automatically add a Change-Id."

# Step 6: Push the change to refs/for/trunk for review
echo "Step 6: Pushing to refs/for/$BRANCH..."
git push origin HEAD:refs/for/$BRANCH

# The Change-Id will be preserved in the commit message
CHANGE_ID=$(git log -1 --pretty=%B | grep -o "Change-Id: I[0-9a-f]\{40\}" | cut -d' ' -f2)
echo "Change created with Change-Id: $CHANGE_ID"

# Step 7: Make additional changes (creating a new patchset)
echo "Step 7: Making additional changes for a new patchset..."
echo "// Additional changes - $(date)" >> example.txt
git add example.txt

# Step 8: Amend the commit
echo "Step 8: Amending the commit..."
git commit --amend --no-edit

# Step 9: Push the updated change to refs/for/trunk
echo "Step 9: Pushing updated change to refs/for/$BRANCH..."
git push origin HEAD:refs/for/$BRANCH

echo "Updated change with Change-Id: $CHANGE_ID"

# Step 10: Simulate review and approval (this would normally be done in the Gerrit UI)
echo "Step 10: In a real workflow, reviewers would now review and approve the change in Gerrit."
echo "After approval, the change can be submitted to the trunk branch."

# Step 11: Demonstrate rebasing on latest trunk
echo "Step 11: Rebasing on latest trunk..."
git fetch origin
git rebase origin/$BRANCH

# Step 12: Push the rebased change
echo "Step 12: Pushing rebased change..."
git push origin HEAD:refs/for/$BRANCH -f

echo "Rebased change with Change-Id: $CHANGE_ID"

# Step 13: Demonstrate cherry-picking a change to another branch
echo "Step 13: Cherry-picking to another branch..."
OTHER_BRANCH="release"
git fetch origin $OTHER_BRANCH
git checkout -b cherry-pick-to-$OTHER_BRANCH origin/$OTHER_BRANCH
git cherry-pick $CHANGE_BRANCH

# Step 14: Push the cherry-picked change
echo "Step 14: Pushing cherry-picked change..."
git push origin HEAD:refs/for/$OTHER_BRANCH

# The cherry-picked commit will have a new Change-Id
NEW_CHANGE_ID=$(git log -1 --pretty=%B | grep -o "Change-Id: I[0-9a-f]\{40\}" | cut -d' ' -f2)
echo "Cherry-picked change with new Change-Id: $NEW_CHANGE_ID"

echo "Workflow example completed successfully!"
echo "This script demonstrated:"
echo "1. Creating a change and pushing to refs/for/$BRANCH"
echo "2. Updating the change with a new patchset"
echo "3. Rebasing the change on the latest trunk"
echo "4. Cherry-picking the change to another branch"
echo ""
echo "In a real workflow, you would also:"
echo "- Wait for reviews and approvals in the Gerrit UI"
echo "- Submit the change through the Gerrit UI or using the 'git review' command"
echo "- Handle merge conflicts if they occur during rebase or cherry-pick"