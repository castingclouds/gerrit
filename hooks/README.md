# Gerrit Git Hooks

This directory contains Git hooks for use with the Gerrit Code Review system.

## Available Hooks

- `commit-msg`: Automatically adds a Change-Id to commit messages (client-side)
- `pre-receive`: Validates that all incoming commits have valid Change-Ids (server-side)

## Installation Instructions

### commit-msg Hook

The `commit-msg` hook automatically adds a Change-Id to your commit messages, which is required for the Gerrit workflow. This ensures that changes can be tracked across revisions, even when rebased or amended.

#### Installation Steps

1. Make the hook executable:
   ```bash
   chmod +x hooks/commit-msg
   ```

2. Install the hook in your local Git repository:
   ```bash
   cp hooks/commit-msg /path/to/your/repo/.git/hooks/
   ```

   Or, for a more convenient approach that works across all your repositories:

   ```bash
   # Create a global Git template directory if you don't have one
   mkdir -p ~/.git-templates/hooks
   
   # Copy the hook to the template directory
   cp hooks/commit-msg ~/.git-templates/hooks/
   
   # Make it executable
   chmod +x ~/.git-templates/hooks/commit-msg
   
   # Configure Git to use this template directory
   git config --global init.templateDir ~/.git-templates
   ```

   With the global template approach, the hook will be automatically installed in any new repositories you create or clone.

3. For existing repositories, you'll need to reinitialize the hooks:
   ```bash
   git init
   ```
   This won't affect your existing repository data, it just refreshes the hooks.

#### Usage

Once installed, the hook will automatically add a Change-Id to your commit messages when you create a new commit. You don't need to do anything special - just commit as usual:

```bash
git commit -m "Your commit message"
```

The hook will add a Change-Id line at the end of your commit message:

```
Your commit message

Change-Id: I1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t
```

If you amend a commit, the hook will preserve the existing Change-Id, which is essential for the Gerrit workflow.

#### Troubleshooting

If the hook isn't adding Change-Ids to your commits:

1. Make sure the hook is executable:
   ```bash
   ls -l .git/hooks/commit-msg
   ```
   It should have execute permissions (`-rwxr-xr-x` or similar).

2. Check if the hook is being called:
   ```bash
   GIT_TRACE=1 git commit -m "Test commit"
   ```
   You should see the hook being executed in the trace output.

3. If you're using a GUI Git client, make sure it's configured to use Git hooks.

### pre-receive Hook

The `pre-receive` hook validates that all incoming commits have valid Change-Ids. This server-side hook provides an additional layer of enforcement for the Gerrit workflow, ensuring that even if developers forget to install the client-side hook, their commits will be rejected if they don't have valid Change-Ids.

#### Installation Steps

1. Make the hook executable:
   ```bash
   chmod +x hooks/pre-receive
   ```

2. Install the hook in your Git repository on the server:
   ```bash
   cp hooks/pre-receive /path/to/your/repo.git/hooks/
   ```

   For repositories managed by Gerrit, you'll need to place this in the appropriate hooks directory for your Gerrit installation.

#### Behavior

The pre-receive hook:

1. Checks each incoming commit to verify it has a valid Change-ID
2. Skips validation for special commits:
   - Merge commits
   - Commits with prefixes like "Revert", "Automated:", or "CI:"
3. Handles different types of Git references:
   - For pushes to `refs/for/` (virtual branches), it allows the Gerrit workflow to handle them
   - For pushes to `refs/heads/` (regular branches), it enforces Change-ID validation
   - For other refs (tags, notes, etc.), it skips validation

#### Customization

You can customize the hook to fit your specific workflow:

- Modify the special prefixes that are exempted from Change-ID validation
- Adjust the validation rules for different types of references
- Change the error messages to provide more specific guidance for your team

## Workflow Integration

These hooks are part of the Gerrit workflow implementation. For more details on the complete workflow, see the [Gerrit Workflow Implementation Guide](../docs/gerrit-workflow-implementation-guide.md).