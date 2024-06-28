# Gitlet
A version-control system that mimics some of the basic features of the Git.
## Complie
Simply type `make`
## Run
### Init:
`java gitlet.Main init`

### Add:
`java gitlet.Main add [file name]`


### Commit:
`java gitlet.Main commit [message]`

### Rm:
`java gitlet.Main rm [file name]`

### Log:
`java gitlet.Main log`

### Global-log:
`java gitlet.Main global-log`

### Find:
Prints out the ids of all commits that have the given commit message

`java gitlet.Main find [commit message]`

### Status:
`java gitlet.Main status`

### Checkout file
`java gitlet.Main checkout -- [file name]`

### Checkout commit:
`java gitlet.Main checkout [commit id] -- [file name]`

### Checkout branch:
`java gitlet.Main checkout [commit id] -- [file name]`

### Branch:
`java gitlet.Main branch [branch name]`

### Rm-branch:
`java gitlet.Main rm-branch [branch name]`

### Reset:
`java gitlet.Main reset [commit id]`

### Merge:
`java gitlet.Main merge [branch name]`

