#!/bin/bash
#
# Gerrit Workflow Example Script - Trunk-Based Development
#
# This script demonstrates the proper Gerrit workflow using trunk-based development.
# Developers work directly on the trunk branch and use refs/for/trunk for code review.
# No feature branches are created - all work happens on trunk.
#
# Prerequisites:
# - Git installed
# - Access to a Gerrit server
# - commit-msg hook installed in your local repository

set -e  # Exit on error

# Configuration
GERRIT_URL="http://localhost:8080"  # Replace with your Gerrit URL
GERRIT_SSH_HOST="localhost:29418"   # SSH host and port
PROJECT_NAME="example-project"      # Replace with your project name
BRANCH="trunk"                      # The target branch (trunk-based development)

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
    echo "Downloading commit-msg hook from $GERRIT_URL/tools/hooks/commit-msg"
    curl -Lo .git/hooks/commit-msg "$GERRIT_URL/tools/hooks/commit-msg"
    chmod +x .git/hooks/commit-msg
    echo "Commit-msg hook installed successfully"
else
    echo "Commit-msg hook already installed"
fi

# Step 3: Ensure we're on the trunk branch (trunk-based development)
echo "Step 3: Ensuring we're on the trunk branch..."
git checkout $BRANCH
git pull origin $BRANCH

# Step 4: Make changes to the code
echo "Step 4: Making changes..."
echo "// Example change - $(date)" >> example.txt
git add example.txt

# Step 5: Commit the changes
echo "Step 5: Committing changes..."
git commit -m "Add example change

This is an example change to demonstrate the trunk-based Gerrit workflow.
The commit-msg hook will automatically add a Change-Id."

# Step 6: Push the change to refs/for/trunk for review
echo "Step 6: Pushing to refs/for/$BRANCH for code review..."
echo "You can push using either HTTP or SSH:"
echo ""
echo "HTTP (recommended for most users):"
echo "  git push http://$GERRIT_URL/git/$PROJECT_NAME HEAD:refs/for/$BRANCH"
echo ""
echo "SSH (for advanced users):"
echo "  git push ssh://$GERRIT_SSH_HOST/$PROJECT_NAME HEAD:refs/for/$BRANCH"
echo ""
echo "Using HTTP method:"
git push http://$GERRIT_URL/git/$PROJECT_NAME HEAD:refs/for/$BRANCH

# The Change-Id will be preserved in the commit message
CHANGE_ID=$(git log -1 --pretty=%B | grep -o "Change-Id: I[0-9a-f]\{40\}" | cut -d' ' -f2)
echo "Change created with Change-Id: $CHANGE_ID"

# Step 7: Make additional changes (creating a new patchset)
echo "Step 7: Making additional changes for a new patchset..."
echo "// Additional changes - $(date)" >> example.txt
git add example.txt

# Step 8: Amend the commit (preserves Change-Id)
echo "Step 8: Amending the commit..."
git commit --amend --no-edit

# Step 9: Push the updated change to refs/for/trunk
echo "Step 9: Pushing updated change to refs/for/$BRANCH..."
git push http://$GERRIT_URL/git/$PROJECT_NAME HEAD:refs/for/$BRANCH

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
git push http://$GERRIT_URL/git/$PROJECT_NAME HEAD:refs/for/$BRANCH -f

echo "Rebased change with Change-Id: $CHANGE_ID"

# Step 13: Demonstrate working on another change while first is in review
echo "Step 13: Working on another change while first is in review..."
echo "// Second change - $(date)" >> example2.txt
git add example2.txt

git commit -m "Add second example change

This demonstrates working on multiple changes simultaneously
in a trunk-based workflow."

git push http://$GERRIT_URL/git/$PROJECT_NAME HEAD:refs/for/$BRANCH

SECOND_CHANGE_ID=$(git log -1 --pretty=%B | grep -o "Change-Id: I[0-9a-f]\{40\}" | cut -d' ' -f2)
echo "Second change created with Change-Id: $SECOND_CHANGE_ID"

# Step 14: Demonstrate direct push to trunk (after approval)
echo "Step 14: Demonstrating direct push to trunk (after approval)..."
echo "Note: This would only be done after the change is approved in Gerrit."
echo "Direct push to trunk requires a valid Change-Id in the commit message."

# Step 15: Show how to fetch and work on a specific change
echo "Step 15: Fetching and working on a specific change..."
echo "To work on a specific change from Gerrit:"
echo ""
echo "HTTP:"
echo "  git fetch http://$GERRIT_URL/git/$PROJECT_NAME refs/changes/12/$CHANGE_ID/1"
echo "  git checkout FETCH_HEAD"
echo ""
echo "SSH:"
echo "  git fetch ssh://$GERRIT_SSH_HOST/$PROJECT_NAME refs/changes/12/$CHANGE_ID/1"
echo "  git checkout FETCH_HEAD"
echo ""
echo "Virtual branches (refs/changes/XX/CHANGEID/PATCHSET) are automatically advertised"
echo "by both HTTP and SSH protocols, allowing you to fetch specific changes for review."

# Step 16: Demonstrate cleanup
echo "Step 16: Cleaning up local changes..."
git reset --hard origin/$BRANCH
git clean -fd

echo "Workflow example completed successfully!"
echo ""
echo "This script demonstrated trunk-based development:"
echo "1. Working directly on the trunk branch"
echo "2. Creating changes and pushing to refs/for/trunk for review"
echo "3. Updating changes with new patchsets"
echo "4. Rebasing changes on the latest trunk"
echo "5. Working on multiple changes simultaneously"
echo ""
echo "Key principles of trunk-based development:"
echo "- No feature branches are created"
echo "- All work happens on the trunk branch"
echo "- Changes are reviewed via refs/for/trunk"
echo "- Multiple changes can be worked on simultaneously"
echo "- Changes are merged directly to trunk after approval"
echo ""
echo "In a real workflow, you would also:"
echo "- Wait for reviews and approvals in the Gerrit UI"
echo "- Submit the change through the Gerrit UI or using the 'git review' command"
echo "- Handle merge conflicts if they occur during rebase"
echo "- Use the Gerrit web interface to manage the review process"