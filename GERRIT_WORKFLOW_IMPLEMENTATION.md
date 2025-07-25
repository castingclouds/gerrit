# Gerrit Workflow Implementation

## Supported Workflow: Trunk-Based Development Only

- **All development is trunk-based.**
- **All pushes require a Change-Id.**
- **Direct pushes to `trunk` are only allowed with a valid Change-Id and after review.**
- **No feature branches:** Any push to `refs/heads/*` other than `trunk` is rejected.
- **All collaboration happens through changes/patch sets and code review.**
- **No branch-based collaboration.**

> **Note:** In the future, support for branch-based workflows may be added as a project-level choice, but is not available now.

## Enforcement

- **Reject any push to `refs/heads/*` except `refs/heads/trunk`.**
- **Require Change-Id for all pushes to `trunk`.**
- **Clear error messages** are provided for any attempt to push to a non-trunk branch.

## Example Error Message

```
Direct push to branch 'feature-xyz' is not allowed.
This project is configured for trunk-based development only.
All changes must be made on trunk and require a Change-Id.
```

## Overview

The Gerrit workflow enforces a **trunk-based, change-id enforced development model** where:
- **Only direct pushes to trunk branch** are allowed (with Change-Id required)
- **All other branches are rejected** - no feature branch support
- **Virtual branches** (`refs/changes/*`) are created for isolated review via `refs/for/*`
- **All collaboration happens through code review** - no direct branch collaboration

## Key Features

### 1. Change-Id Requirements

**Trunk branch only**: Change-Id is **required** for direct pushes to trunk

Change-Ids are essential for:
- Tracking changes across rebases
- Referencing specific changes in virtual branches (`refs/changes/*`)
- Pulling/fetching specific changes for review
- Maintaining change history and context
- Enabling the virtual branch workflow (fetch, pull, merge, rebase)

```bash
# ✅ Trunk branch - requires Change-Id
git commit -m "Add new feature

Change-Id: I1234567890abcdef1234567890abcdef12345678"
git push origin trunk

# ❌ Trunk branch - missing Change-Id
git commit -m "Add new feature"
git push origin trunk
# Error: Direct push to trunk branch requires a valid Change-Id

# ✅ Trunk branch - with Change-Id
git commit -m "Add new feature

Change-Id: I1234567890abcdef1234567890abcdef12345678"
git push origin trunk
# Success: Direct push to trunk

# ❌ Trunk branch - missing Change-Id
git commit -m "Add new feature"
git push origin trunk
# Error: Direct push to trunk branch requires a valid Change-Id

# ❌ Feature branch - not allowed
git push origin feature-branch
# Error: Direct push to branch 'feature-branch' is not allowed.
# This project is configured for trunk-based development only.

# Why Change-Ids are needed:
# Without Change-Id, you can't easily reference your change:
# ❌ This won't work - no Change-Id to reference
# git fetch origin refs/changes/??/????/1

# ✅ With Change-Id, you can reference your change:
# git fetch origin refs/changes/12/CHANGEID/1
# git checkout FETCH_HEAD
```

### 2. Branch Protection and Code Review

Only trunk branch allows direct pushes (with Change-Id required). All other branches are rejected:

```bash
# ✅ Trunk branch - direct push (with Change-Id)
git push origin trunk
# Requires Change-Id in commit message

# ❌ Feature branch - not allowed
git push origin feature-branch
# Error: Direct push to branch 'feature-branch' is not allowed.
# This project is configured for trunk-based development only.

# ✅ Code review workflow (for any target)
git push origin HEAD:refs/for/trunk
# Creates a change for review in virtual branch refs/changes/XX/CHANGEID/1
```

### 3. Magic Branch Processing and Virtual Branches

Pushes to `refs/for/*` create virtual branches under `refs/changes/*` for review:

**Virtual Branch Naming Convention:**
- Format: `refs/changes/XX/CHANGEID/PATCHSET`
- `XX`: Last two digits of the change number
- `CHANGEID`: The Change-Id from the commit message
- `PATCHSET`: Patch set number (1, 2, 3, etc.)

**Example:**
```bash
# Push creates virtual branch
git push origin HEAD:refs/for/trunk
# Creates: refs/changes/12/CHANGEID/1

# Update creates new patch set
git commit --amend
git push origin HEAD:refs/for/trunk
# Creates: refs/changes/12/CHANGEID/2
```

```bash
# Create a new change (creates virtual branch refs/changes/XX/CHANGEID/1)
git push origin HEAD:refs/for/trunk

# Update an existing change (creates new patch set refs/changes/XX/CHANGEID/2)
git commit --amend
git push origin HEAD:refs/for/trunk

# Create change with topic
git push origin HEAD:refs/for/trunk%topic=my-feature

# Submit change immediately (if permitted)
git push origin HEAD:refs/for/trunk%submit

# Fetch and checkout a specific change for review
git fetch origin refs/changes/12/CHANGEID/1
git checkout FETCH_HEAD
```

## Working with Virtual Branches

### Fetching Changes

Fetch specific changes using their Change-Id and patch set:

```bash
# Fetch a specific change (patch set 1)
git fetch origin refs/changes/12/CHANGEID/1

# Fetch a different patch set of the same change
git fetch origin refs/changes/12/CHANGEID/2

# Fetch all patch sets for a change
git fetch origin refs/changes/12/CHANGEID/*

# Fetch all changes (for browsing)
git fetch origin refs/changes/*

# Fetch changes targeting trunk
git fetch origin refs/changes/*/trunk/*
```

### Pulling Changes

Pull changes directly into your current branch:

```bash
# Pull a specific change into current branch
git pull origin refs/changes/12/CHANGEID/1

# Pull latest patch set of a change
git pull origin refs/changes/12/CHANGEID/HEAD

# Pull and merge a change
git pull origin refs/changes/12/CHANGEID/1 --no-ff
```

### Merging Changes

Merge virtual branches into your working branch:

```bash
# Merge a specific change into current branch
git merge FETCH_HEAD

# Merge after fetching
git fetch origin refs/changes/12/CHANGEID/1
git merge FETCH_HEAD

# Merge with custom merge message
git merge FETCH_HEAD -m "Merge change 12/CHANGEID/1: Add new feature"

# Merge multiple changes
git fetch origin refs/changes/12/CHANGEID/1 refs/changes/13/CHANGEID/1
git merge FETCH_HEAD^ FETCH_HEAD
```

### Rebasing Changes

Rebase your work on top of changes or rebase changes themselves:

```bash
# Rebase your branch on top of a change
git rebase FETCH_HEAD

# Rebase after fetching
git fetch origin refs/changes/12/CHANGEID/1
git rebase FETCH_HEAD

# Rebase a change on top of trunk
git fetch origin refs/changes/12/CHANGEID/1
git checkout FETCH_HEAD
git rebase origin/trunk

# Interactive rebase for complex changes
git rebase -i FETCH_HEAD
```

### Checking Out Changes

Check out changes for review or development:

```bash
# Check out a specific change (detached HEAD)
git checkout FETCH_HEAD

# Check out and create a local branch
git checkout -b review-change-12 FETCH_HEAD

# Check out latest patch set of a change
git fetch origin refs/changes/12/CHANGEID/HEAD
git checkout FETCH_HEAD

# Check out multiple changes
git fetch origin refs/changes/12/CHANGEID/1 refs/changes/13/CHANGEID/1
git checkout FETCH_HEAD^  # First change
git checkout FETCH_HEAD   # Second change
```

### Working with Multiple Changes

Handle multiple changes simultaneously:

```bash
# Fetch multiple changes
git fetch origin refs/changes/12/CHANGEID/1 refs/changes/13/CHANGEID/1 refs/changes/14/CHANGEID/1

# Create a branch with multiple changes
git checkout -b feature-with-changes FETCH_HEAD^
git merge FETCH_HEAD
git merge FETCH_HEAD^

# Cherry-pick specific changes
git cherry-pick FETCH_HEAD^
git cherry-pick FETCH_HEAD
```

### Reviewing Changes

Common workflow for reviewing changes:

```bash
# 1. Fetch the change you want to review
git fetch origin refs/changes/12/CHANGEID/1

# 2. Check out the change
git checkout FETCH_HEAD

# 3. Review the code, run tests, etc.
make test
./run-linting.sh

# 4. If you need to make changes, create a new patch set
git commit --amend
git push origin HEAD:refs/for/trunk

# 5. Or if you want to add comments without changing code
# (This would be done through the web UI or API)
```

### Updating Your Changes

Update your own changes after feedback:

```bash
# 1. Fetch your latest change
git fetch origin refs/changes/12/CHANGEID/1

# 2. Check out your change
git checkout FETCH_HEAD

# 3. Make improvements based on review feedback
# ... edit files ...

# 4. Amend the commit
git add .
git commit --amend

# 5. Push as a new patch set
git push origin HEAD:refs/for/trunk
# This creates refs/changes/12/CHANGEID/2
```

### Finding Changes

Discover and list available changes:

```bash
# List all changes
git ls-remote origin refs/changes/*

# List changes targeting trunk
git ls-remote origin refs/changes/*/trunk/*

# List changes by author
git ls-remote origin refs/changes/* | grep "author-name"

# List recent changes
git ls-remote origin refs/changes/* | head -20
```

### Working with Change-Ids Locally

The Change-Id is stored in your commit message, so you can always find it locally:

```bash
# View Change-Id in current commit
git log --oneline -1
git show --format=fuller

# View Change-Id in specific commit
git show --format=fuller <commit-hash>

# Extract Change-Id from current commit
git log --format=%B -1 | grep "^Change-Id:"

# Extract Change-Id from specific commit
git log --format=%B -1 <commit-hash> | grep "^Change-Id:"

# List all Change-Ids in your branch
git log --format=%B | grep "^Change-Id:" | sort | uniq

# Find commits by Change-Id
git log --grep="Change-Id: I1234567890abcdef1234567890abcdef12345678"

# Show commit with Change-Id highlighted
git log --oneline --grep="Change-Id:" --color=always
```

### Discovering Your Changes

Find your own changes and their virtual branch references:

```bash
# Get Change-Id from your last commit
CHANGE_ID=$(git log --format=%B -1 | grep "^Change-Id:" | cut -d' ' -f2)
echo "Your Change-Id: $CHANGE_ID"

# Fetch your change using the Change-Id
git fetch origin refs/changes/*/$CHANGE_ID/*

# List all patch sets for your change
git ls-remote origin refs/changes/*/$CHANGE_ID/*

# Check out your latest patch set
git fetch origin refs/changes/*/$CHANGE_ID/HEAD
git checkout FETCH_HEAD
```

### Managing Multiple Changes

Work with multiple changes you've created:

```bash
# List all your Change-Ids
git log --format=%B | grep "^Change-Id:" | sort | uniq

# Fetch all your changes
for change_id in $(git log --format=%B | grep "^Change-Id:" | cut -d' ' -f2 | sort | uniq); do
    echo "Fetching change: $change_id"
    git fetch origin refs/changes/*/$change_id/*
done

# Create a script to fetch your changes
cat > fetch-my-changes.sh << 'EOF'
#!/bin/bash
# Fetch all changes created by the current user
for change_id in $(git log --format=%B | grep "^Change-Id:" | cut -d' ' -f2 | sort | uniq); do
    echo "Fetching change: $change_id"
    git fetch origin refs/changes/*/$change_id/*
done
EOF
chmod +x fetch-my-changes.sh
```

### Change-Id in Commit Messages

The Change-Id is automatically added to your commit message:

```bash
# Example commit message with Change-Id
git log --format=fuller -1

# Output:
# commit abc123def456...
# Author: Your Name <your.email@example.com>
# Date:   Mon Jan 1 12:00:00 2024 +0000
# 
#     Add new feature
# 
#     This commit adds a new feature that does something useful.
# 
#     Change-Id: I1234567890abcdef1234567890abcdef12345678
#     Reviewed-by: Reviewer Name <reviewer@example.com>
#     Tested-by: Tester Name <tester@example.com>
```

### Using Change-Ids for References

Once you have a Change-Id, you can reference it in various ways:

```bash
# Store Change-Id in a variable
CHANGE_ID="I1234567890abcdef1234567890abcdef12345678"

# Fetch the change
git fetch origin refs/changes/*/$CHANGE_ID/*

# Check out the latest patch set
git checkout FETCH_HEAD

# Or fetch a specific patch set
git fetch origin refs/changes/*/$CHANGE_ID/1
git checkout FETCH_HEAD

# Create a branch for this change
git checkout -b review-$CHANGE_ID FETCH_HEAD
```

## Implementation Details

### Git HTTP Servlet Configuration

The main workflow logic is implemented through JGit hooks in `GitHttpServletConfig.kt`:

1. **Magic Branch Processing** (`GerritPreReceiveHook.processMagicBranch`):
   - Handles pushes to `refs/for/*`
   - Extracts or generates Change-Ids
   - Creates/updates changes via `ChangeService`
   - Creates virtual branches under `refs/changes/*` for review

2. **Regular Ref Processing** (`GerritPreReceiveHook.processRegularRef`):
   - Enforces Change-Id requirement for trunk pushes
   - Auto-redirects direct pushes to other branches to code review workflow
   - Provides helpful error messages

3. **Post-Processing** (`GerritPostReceiveHook`):
   - Handles notifications and cleanup after pushes
   - Triggers CI/CD pipelines
   - Updates change status

### Change-Id Utilities

The `ChangeIdUtil.kt` provides:

- **Change-Id Generation**: Creates unique identifiers based on commit content
- **Change-Id Validation**: Ensures proper format (`I` + 40 hex chars)
- **Change-Id Extraction**: Parses Change-Ids from commit messages
- **Message Manipulation**: Adds/removes Change-Ids from commit messages

### Hook Classes

The workflow is implemented through JGit hooks:

- **`GerritPreReceiveHook.kt`**: Main workflow logic for magic branches and branch protection
- **`GerritPostReceiveHook.kt`**: Post-processing for notifications and cleanup
- **`GerritReceiveAdvertiseRefsHook.kt`**: Virtual branch advertisement

### Setup Service

The `ChangeIdSetupService.kt` helps users:

- **Install commit-msg hooks** automatically
- **Generate Change-Ids** for existing commits
- **Provide setup instructions** and troubleshooting guides

## Error Messages

The system provides helpful error messages that explain:

### Missing Change-Id Error
```
Direct push to trunk branch requires a valid Change-Id in the commit message.

To fix this:
1. Add a Change-Id to your commit message:
   git commit --amend
   # Add this line to your commit message:
   Change-Id: I$(git rev-parse HEAD | cut -c1-40)

2. Or use the Gerrit workflow for code review:
   git push origin HEAD:refs/for/trunk

The Change-Id helps track your changes across rebases and ensures proper code review workflow.
```

### Branch Rejection Message
```
Direct push to branch 'feature-branch' is not allowed.
This project is configured for trunk-based development only.
All changes must be made on trunk and require a Change-Id.

To create a change for review:
git push origin HEAD:refs/for/trunk

To push directly to trunk (requires Change-Id):
git push origin trunk
```

## Change-Id Storage and Discovery

### How Change-Ids Are Stored

Change-Ids are stored in the commit message itself, making them always available locally:

```bash
# The Change-Id is part of your commit message
git log --format=fuller -1

# Output shows the Change-Id in the commit body:
# commit abc123def456...
# Author: Your Name <your.email@example.com>
# Date:   Mon Jan 1 12:00:00 2024 +0000
# 
#     Add new feature
# 
#     This commit adds a new feature that does something useful.
# 
#     Change-Id: I1234567890abcdef1234567890abcdef12345678
```

### Automatic Change-Id Generation

The `commit-msg` hook automatically adds Change-Ids to your commits:

```bash
# When you commit, the hook automatically adds Change-Id
git commit -m "Add new feature"
# The hook modifies your commit message to include:
# 
# Change-Id: I1234567890abcdef1234567890abcdef12345678

# You can see this in your commit history
git log --oneline -1
git show --format=fuller
```

### Finding Your Change-Ids

Since Change-Ids are in your commit messages, you can always find them:

```bash
# Get Change-Id from your last commit
git log --format=%B -1 | grep "^Change-Id:"

# Get Change-Id from a specific commit
git log --format=%B -1 <commit-hash> | grep "^Change-Id:"

# List all Change-Ids in your current branch
git log --format=%B | grep "^Change-Id:" | sort | uniq

# Find commits by Change-Id
git log --grep="Change-Id: I1234567890abcdef1234567890abcdef12345678"
```

### Using Change-Ids to Access Virtual Branches

Once you have a Change-Id, you can access your virtual branches:

```bash
# Extract Change-Id from your commit
CHANGE_ID=$(git log --format=%B -1 | grep "^Change-Id:" | cut -d' ' -f2)

# Fetch your virtual branch
git fetch origin refs/changes/*/$CHANGE_ID/*

# Check out your change
git checkout FETCH_HEAD

# List all patch sets for your change
git ls-remote origin refs/changes/*/$CHANGE_ID/*
```

### Managing Multiple Changes

Work with multiple changes you've created:

```bash
# List all your Change-Ids
git log --format=%B | grep "^Change-Id:" | sort | uniq

# Fetch all your changes
for change_id in $(git log --format=%B | grep "^Change-Id:" | cut -d' ' -f2 | sort | uniq); do
    echo "Fetching change: $change_id"
    git fetch origin refs/changes/*/$change_id/*
done

# Create a script to fetch your changes
cat > fetch-my-changes.sh << 'EOF'
#!/bin/bash
# Fetch all changes created by the current user
for change_id in $(git log --format=%B | grep "^Change-Id:" | cut -d' ' -f2 | sort | uniq); do
    echo "Fetching change: $change_id"
    git fetch origin refs/changes/*/$change_id/*
done
EOF
chmod +x fetch-my-changes.sh
```

## REST API Endpoints

The `GerritWorkflowController.kt` provides helpful endpoints:

### Workflow Information
```bash
GET /api/gerrit/workflow/info
# Returns workflow description and key features

GET /api/gerrit/workflow/commands
# Returns common Git commands for the workflow

GET /api/gerrit/workflow/errors
# Returns error message templates
```

### Setup and Troubleshooting
```bash
GET /api/gerrit/workflow/setup
# Returns setup instructions

GET /api/gerrit/workflow/troubleshooting
# Returns troubleshooting guide

GET /api/gerrit/workflow/hook/status?repositoryPath=/path/to/repo
# Checks if commit-msg hook is installed

POST /api/gerrit/workflow/hook/install?repositoryPath=/path/to/repo
# Installs commit-msg hook
```

### Change-Id Utilities
```bash
POST /api/gerrit/workflow/change-id/generate?commitHash=abc123
# Generates a Change-Id for a commit

POST /api/gerrit/workflow/change-id/add?commitMessage=...&changeId=I123...
# Adds Change-Id to a commit message
```

## Working with Change Ids
```bash
# Add to user's git config or provide as scripts
git config --global alias.fetch-change '!f() { git fetch origin refs/changes/$1/$2/$3; }; f'
git config --global alias.checkout-change '!f() { git fetch origin refs/changes/$1/$2/$3 && git checkout FETCH_HEAD; }; f'

# Usage:
git fetch-change 12 CHANGEID 1
git checkout-change 12 CHANGEID 1
```

## Testing

The `GerritWorkflowTest.kt` includes comprehensive tests for:

- Change-Id validation and generation
- Commit message manipulation
- Error message formatting
- Workflow rule enforcement

## Configuration

The workflow is configured through:

1. **Git Configuration** (`GitConfiguration.kt`):
   - Repository base path
   - HTTP/SSH protocol settings
   - Permission settings

2. **Security Configuration** (`SecurityConfig.kt`):
   - Authentication requirements
   - Authorization rules

3. **Application Properties** (`application.yml`):
   - Server settings
   - Database configuration
   - Logging levels

## Migration from Branch-Based Workflow

For teams transitioning from branch-based development:

1. **Install commit-msg hooks** to automatically add Change-Ids
2. **Use `refs/for/*`** instead of direct branch pushes
3. **Learn the change-based workflow** through the provided error messages
4. **Leverage the REST API** for setup and troubleshooting

## Benefits

The implemented workflow provides:

- **Code Review**: All changes go through review before merging
- **Change Tracking**: Change-Ids persist across rebases and cherry-picks
- **Integration**: Works with CI/CD pipelines and automation
- **Collaboration**: Better visibility and discussion of changes
- **Quality**: Enforces review process and documentation standards

## Future Enhancements

Planned improvements include:

1. **Web UI**: Browser-based change management interface
2. **Advanced Permissions**: Granular access control per branch/change
3. **Automated Testing**: Integration with CI/CD systems
4. **Change Templates**: Predefined change descriptions and reviewers
5. **Metrics and Analytics**: Change review statistics and performance metrics 