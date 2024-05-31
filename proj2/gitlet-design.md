# Gitlet Design Document

## Git Command
### git init
```angular2html
.gitlet/<br>
├── HEAD  ref to HEAD (ref: refs/heads/feature-D)<br>
├── objects/XX/XXXXXXXXXXXXXX<br>
└── refs/<br>
    ├── heads/branches/XXXXXXXXXXX<br>
    └── remotes/<br>
            └── origin/branches/XXXXXXXXX<br>
```





## Classes and Data Structures

### Class 1

#### Fields

1. Field 1
2. Field 2


### Class 2

#### Fields

1. Field 1
2. Field 2

### Blobs
The saved contents of files.<br>
```angular2html
blob <content length> content
blob 13\0Hello, World!
```

### Trees
Directory structure. Mapping names to references to blobs and other trees (subdirectories).<br>
```angular2html
file_mode     object_type       SHA-1_Hash                                  filename
100644        blob              e69de29bb2d1d6434b8b29ae775ad8c2e48c5391    README.md
100755        blob              6d6d4fe63462e3c0f2e5b08ab37633d9eac952e9    script.sh
040000        tree              3a2c8e7fc43121ba1b9e73f7b8aa4d7f4f18c3d6    src

```

### Index
manage the files in the staging area<br>
store the filepath, last modified time, file size, sha1hash

### commits
A commit will consist of a log message, timestamp, a mapping of file names to blob references, a parent reference, and (for merges) a second parent reference.<br>
The repository also maintains a mapping from branch heads to references to commits, so that certain important commits have symbolic names.<br>
Including all metadata and references when hashing a commit.
## Algorithms

## Persistence

