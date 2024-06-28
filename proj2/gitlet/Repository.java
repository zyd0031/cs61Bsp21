package gitlet;

import gitlet.exception.GitletException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.time.Instant;


import static gitlet.Utils.*;
import static gitlet.constant.MessageConstant.*;


/** Represents a gitlet repository.
 *  does at a high level.
 *  @author Yudie Zheng
 */
public class Repository {

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    // store ref to HEAD
    public static final File HEAD_FILE = join(GITLET_DIR, "HEAD");
    public static final File INDEX_FILE = join(GITLET_DIR, "index");
    public static final File OBJECT_DIR = join(GITLET_DIR, "objects");
    // store the head of each branch
    public static final File BRANCH_HEAD_DIR = join(GITLET_DIR, "refs", "heads");
    public static final File LOGS = join(GITLET_DIR, "logs");
    public static final File LOGS_HEAD = join(GITLET_DIR, "logs", "HEAD");
    public static final File[] DIRS = {GITLET_DIR, OBJECT_DIR, BRANCH_HEAD_DIR, LOGS};
    public static final File[] FILES = {HEAD_FILE, INDEX_FILE, LOGS_HEAD};


    /**
     * initialize a repository, creating the necessary directories/fiiles
     */
    public void init(){
        // if already initialized, do nothing
        if (GITLET_DIR.exists()){
            System.out.println(ALREADY_INITIALIZED);
            System.exit(0);
        }
        // else initialize a repository
        // create the dirs
        for (File dir : DIRS) {
            if (dir.exists()){
                dir.delete();
            }
            dir.mkdirs();
        }
        // crete the files
        for (File file : FILES) {
            try {
                if (file.exists()){
                    file.delete();
                }
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        //make the first commit
        makeFirstCommit();
    }

    /**
     * make the first commit
     */
    private void makeFirstCommit(){

        Instant epoch0 = Instant.EPOCH;
        LocalDateTime initialCommitTime = LocalDateTime.ofInstant(epoch0, ZoneOffset.UTC);
        String msg = "initial commit";
        Commit initCommit = new Commit(initialCommitTime, msg, null, new Index(), new Tree());
        persistObject(initCommit);

        // set the current branch to master
        Utils.writeContents(HEAD_FILE, "ref: refs/heads/master");

        // create a master branch and point to the initial commit
        File masterHead = join(BRANCH_HEAD_DIR, "master");
        Utils.writeContents(masterHead, initCommit.getSha1());

        String unixTimestamp = toUnixTimestamp(initialCommitTime);
        String commitData = initCommit.getSha1() + " " + unixTimestamp + " " + msg + "\n";
        Utils.appendContents(LOGS_HEAD, commitData);

    }

    /**
     * gitlet add a.txt b.txt
     */
    public void add(String[] filePaths){
        isInitialized();
        Index index = getIndex();
        List<String> files = basciCheckFiles(filePaths);
        // get all the files
        List<String> validFiles = getValidFiles(files);
        // then, do the add command

        Commit parentcommit = getHeadCommit();
        Map<String, String> treeFiles = parentcommit.getTreeFiles();

        /**
         * 3. add files to the staging area and persistence the content of the file to .gitlet/objects/XX/XXXXXXXXX
         * must meet the conditions
         * a. not exists in the staging area
         * b. exists in the staging area, but the content changed
         * c. content different from last commit
         */
        for (String filePath : validFiles){
            String relativePath = getRelativePathtoCWD(filePath);
            Blob blob = new Blob(relativePath);
            String sha1 = blob.getSha1();

            if (index.stagedFilesForAdditionContainsFile(relativePath)){
                // file in the stagde area
                String stagedsha1 = index.getSha1(relativePath);
                if (!sha1.equals(stagedsha1)){
                    if (!treeFiles.containsKey(relativePath)){
                        persistObject(blob);
                        index.addFileForAddition(relativePath, sha1);
                    }else{
                        String treeSha1 = treeFiles.get(relativePath);
                        if (!treeSha1.equals(sha1)){
                            persistObject(blob);
                            index.addFileForAddition(relativePath, sha1);
                        }else{
                            index.removeFileforAddition(relativePath);
                        }
                    }
                }
            }else if (!index.stagedFilesForAdditionContainsFile(relativePath)){
                // file not in the staged area
                // check if it is same as last commit
                if (treeFiles.containsKey(relativePath)){
                    String lastCommitsha1 = treeFiles.get(relativePath);
                    if (! sha1.equals(lastCommitsha1)){
                        persistObject(blob);
                        index.addFileForAddition(relativePath, sha1);
                    }
                }else{
                    persistObject(blob);
                    index.addFileForAddition(relativePath, sha1);
                }
            }
        }
        Utils.writeObject(INDEX_FILE, index);
    }

    /**
     * gitlet commit mes
     * @param msg
     */
    public void commit(String msg){

        // check if it is inside a repository
        isInitialized();

        Index index = getIndex();
        if (index.isClean()){
            throw new GitletException(NO_INDEX_CHAGED_MESSAGE);
        }

        String parentCommitId = getHeadCommitID();
        List<String> parentCommits = Arrays.asList(parentCommitId);
        Commit parentCommit = getCommitbyId(parentCommitId);
        Tree parentTree = parentCommit.getTree();

        Tree tree = buildTree(index, parentTree);

        LocalDateTime time = LocalDateTime.now();
        Commit commit = new Commit(time, msg, parentCommits, index, tree);

        persistObject(commit);
        persistObject(tree);
        clearIndex(index);
        updateCurrentBranch(commit.getSha1());

        // write commit metadata to logs/HEAD
        String unixTimestamp = toUnixTimestamp(time);
        String commitData = commit.getSha1() + " " + unixTimestamp + " " + msg + "\n";
        Utils.appendContents(LOGS_HEAD, commitData);

    }

    /**
     * gitlet rm
     */
    public void rm(String[] filePaths){
        isInitialized();
        // 1. read the index file
        Index index = getIndex();

        // 2. read the current commit
        Commit parentCommit = getHeadCommit();
        List<String> files = basciCheckFilesForRm(filePaths, parentCommit);
        // get all the files
        List<String> validFiles = getValidFilesForRm(files, parentCommit);

        // 3. the main part of rm
        for (String filePath : validFiles) {
            boolean staged = false;
            boolean committed = false;
            String relativePath = getRelativePathtoCWD(filePath);

            if (index.stagedFilesForAdditionContainsFile(relativePath)){
                staged = true;
                index.removeFileforAddition(relativePath);
            }else if (parentCommit.treeContainsFile(relativePath)){
                committed = true;
                index.addFileForRemoval(relativePath);
                File file = new File(relativePath);
                if (file.exists()){
                    file.delete();
                }
            }

            if (!staged && !committed){
                System.out.println(NEITHER_STAGER_NOR_TRACKED + filePath);
            }
        }
        Utils.writeObject(INDEX_FILE, index);
    }

    /**
     * gitlet log (mimic "git log --first-parent")
     */
    public void log(){
        // check whether it is initialized
        isInitialized();

        Commit commit = getHeadCommit();
        System.out.println(commit);

        while (commit.getParentCommitID() != null){
            String commitId = commit.getParentCommitID().get(0);
            commit = getCommitbyId(commitId);
            System.out.println(commit);
        }

    }

    public void global_log() {
        // check whether it is initialized
        isInitialized();

        String contents = readContentsAsString(LOGS_HEAD);
        String[] commits = contents.split("\n");
        for (String commit : commits) {
            String[] s = commit.split(" ", 3);
            String commitId = s[0];
            String commitUnixTime = s[1];
            String commitTime = toCommitDate(commitUnixTime);
            String commitMessage = s[2];
            System.out.println(formatCommit(commitId, commitTime, commitMessage));
        }
        
    }

    /**
     * Prints out the ids of all commits that have the given commit message.
     * @param message
     */
    public void find(String message){
        // check whether it is initialized
        isInitialized();
        String contents = readContentsAsString(LOGS_HEAD);
        String[] commits = contents.split("\n");
        boolean found = false;
        for (String commit : commits) {
            String[] s = commit.split(" ", 3);
            String commitId = s[0];
            String commitMessage = s[2];
            if (commitMessage.equals(message)){
                System.out.println(commitId);
                found = true;
            }
        }
        if (!found){
            System.out.println(NO_COMMIT_WITH_THAT_MESSAGE);
        }
    }

    /**
     * Displays what branches currently exist, and marks the current branch with a *.
     * Also displays what files have been staged for addition or removal.
     */
    public void status(){
        isInitialized();

        // Branches
        System.out.println("=== Branches ===");
        List<String> branches = plainFilenamesIn(BRANCH_HEAD_DIR);
        branches.sort(String::compareTo);
        String head = getHead();
        for (String branch : branches) {
            if (branch.equals(head)){
                System.out.println("*" + branch);
            }else{
                System.out.println(branch);
            }
        }
        System.out.println();


        // Staged Files and Removed Files
        Index index = getIndex();
        System.out.println("=== Staged Files ===");
        List<String> stagedFiles = index.getStagedFilesForAdditionList();
        stagedFiles.sort(String::compareTo);
        if (!stagedFiles.isEmpty()){
            stagedFiles.forEach(file -> System.out.println(file));
        }
        System.out.println();
        System.out.println("=== Removed Files ===");
        List<String> stagedFilesForRemoval = index.getStagedFilesForRemoval();
        stagedFilesForRemoval.sort(String::compareTo);
        if (!stagedFilesForRemoval.isEmpty()){
            stagedFilesForRemoval.forEach(file -> System.out.println(file));
        }
        System.out.println();
        
        Commit parentCommit = getHeadCommit();
        Map<String, String> treefiles = parentCommit.getTreeFiles();
        Map<String, String> filetoSha1 = listFiletoSha1(CWD);
        Map<String, String> stagedFilesMap = index.getStagedFilesForAddition();

        // Modifications Not Staged For Commit
        System.out.println("=== Modifications Not Staged For Commit ===");
        List<String> modifiedNotStaged = new ArrayList<>();
        List<String> deletedNotStaged = new ArrayList<>();
        // Tracked in the current commit, changed in the working directory, but not staged;
        // Not staged for removal, but tracked in the current commit and deleted from the working directory.
        for(Map.Entry<String, String> entry : treefiles.entrySet()){
            String file = entry.getKey();
            String sha1 = entry.getValue();
            File file1 = new File(file);
            if (!stagedFilesForRemoval.contains(file) && !file1.exists()){
                deletedNotStaged.add(file);
                continue;
            }
            if (!filetoSha1.getOrDefault(file, "").equals(sha1) && !index.containsFile(file)){
                modifiedNotStaged.add(file);
            }
        }

        for(Map.Entry<String, String> entry : stagedFilesMap.entrySet()){
            String file = entry.getKey();
            String sha1 = entry.getValue();
            String workSha1 = filetoSha1.get(file);
            if (workSha1 == null){
                // Staged for addition, but deleted in the working directory
                deletedNotStaged.add(file);
            } else if (!workSha1.equals(sha1)){
                // Staged for addition, but with different contents than in the working directory;
                modifiedNotStaged.add(file);
            }
        }
        modifiedNotStaged.sort(String::compareTo);
        Set<String> deletedNotStagedFiles = new TreeSet<>(deletedNotStaged);
        deletedNotStagedFiles.forEach(file -> System.out.println(file + " (deleted)"));
        modifiedNotStaged.forEach(file -> System.out.println(file + " (modified)"));
        System.out.println();

        // Untracked Files
        System.out.println("=== Untracked Files ===");
        List<String> untrackedFiles = getUncheckedFiles(parentCommit);
        untrackedFiles.sort(String::compareTo);
        untrackedFiles.forEach(file -> System.out.println(file));
        System.out.println();

    }

    /**
     * Takes the version of the file as it exists in the head commit and puts it in the working directory,
     * overwriting the version of the file that’s already there if there is one.
     * The new version of the file is not staged.
     * @param file
     */
    public void checkFile(String file){
        isInitialized();
        String relativePath = getRelativePathtoCWD(file);
        Commit headCommit = getHeadCommit();
        if (headCommit.treeContainsFile(relativePath)){
            String sha1 = headCommit.treeFileSha1(relativePath);
            byte[] fileContent = getBlobContentFromSha1(sha1);
            Utils.writeContents(relativePath, fileContent);
        }else{
            throw new GitletException(FILE_NOT_EXIST_IN_THAT_COMMIT_MESSAGE);
        }
    }

    /**
     * Takes the version of the file as it exists in the commit with the given id,
     * and puts it in the working directory,
     * overwriting the version of the file that’s already there if there is one.
     * The new version of the file is not staged.
     * @param commitId
     * @param file
     */
    public void checkoutCommitFile(String commitId, String file){
        isInitialized();
        String relativePath = getRelativePathtoCWD(file);
        File file1 = join(OBJECT_DIR, commitId.substring(0, 2), commitId.substring(2));
        if (!file1.exists()){
            throw new GitletException(NO_COMMIT_WITH_THAT_ID_EXIST_MESSAGE);
        }
        Commit commit = getCommitbyAbbrID(commitId);

        if (!commit.treeContainsFile(relativePath)){
            throw new GitletException(FILE_NOT_EXIST_IN_THAT_COMMIT_MESSAGE);
        }else{
            String sha1 = commit.treeFileSha1(relativePath);
            byte[] fileContent = getBlobContentFromSha1(sha1);
            Utils.writeContents(relativePath, fileContent);
        }
    }

    /**
     * Takes all files in the commit at the head of the given branch,
     * and puts them in the working directory,
     * overwriting the versions of the files that are already there if they exist.
     * Any files that are tracked in the current branch but are not present in the checked-out branch are deleted.
     * Also, at the end of this command, the given branch will now be considered the current branch (HEAD).
     * The staging area is cleared, unless the checked-out branch is the current branch
     * @param branch
     */
    public void checkoutBranch(String branch){
        isInitialized();

        // get checkout branch head commit
        String head = getHead();
        if (head.equals(branch)){
            throw new GitletException(NO_NEED_TO_CHECKOUT_THE_CURRENT_BRANCH_MESSAGE);
        }
        Commit checkoutCommit = getBranchCommit(branch);
        // get current commit
        Commit currentCommit = getHeadCommit();

        // If a working file is untracked in the current branch and would be overwritten by the checkout,
        // print "There is an untracked file in the way; delete it, or add and commit it first." and exit;
        checkFileConsistenceBetweenCommits(currentCommit, checkoutCommit);

        // delete all files and add the checkout branch files
        deleteAllFiles(CWD);
        addCheckoutCommitFiles(checkoutCommit);

        // update HEAD
        Utils.writeContents(HEAD_FILE, "ref: refs/heads/" + branch);

        // clear the staging area
        Index index = getIndex();
        clearIndex(index);
    }

    /**
     * Creates a new branch with the given name, and points it at the current head commit.
     * A branch is nothing more than a name for a reference (a SHA-1 identifier) to a commit node.
     * This command does NOT immediately switch to the newly created branch (just as in real Git).
     * Before you ever call branch, your code should be running with a default branch called "master".
     * @param branch
     */
    public void branch(String branch){
        isInitialized();

        File file = join(BRANCH_HEAD_DIR, branch);
        if (file.exists()){
            throw new GitletException(BRANCH_ALREADT_EXISTS_MESSAGE);
        }else{
            try {
                file.createNewFile();
                String parentCommitID = getHeadCommitID();
                Utils.writeContents(file, parentCommitID);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Deletes the branch with the given name.
     * This only means to delete the pointer associated with the branch;
     * @param branch
     */
    public void rmBranch(String branch){
        isInitialized();

        String head = getHead();
        if (head.equals(branch)){
            throw new GitletException(CONNOT_REMOVE_THE_CURRENT_BRANCH_MESSAGE);
        }
        File file = join(BRANCH_HEAD_DIR, branch);
        if (!file.exists()){
            throw new GitletException(BRANCH_DOES_NOT_EXIST_MESSAGE);
        }else{
            file.delete();
        }
    }

    /**
     * Checks out all the files tracked by the given commit.
     * Removes tracked files that are not present in that commit.
     * Also moves the current branch’s head to that commit node.
     * See the intro for an example of what happens to the head pointer after using reset.
     * The [commit id] may be abbreviated as for checkout.
     * The staging area is cleared.
     * The command is essentially checkout of an arbitrary commit that also changes the current branch head.
     * @param commitID
     */
    public void reset(String commitID){
        isInitialized();

        Commit commit = getCommitbyAbbrID(commitID);
        String resetSha1 = commit.getSha1();
        String currentCommitID = getHeadCommitID();
        Commit currentCommit = getCommitbyId(currentCommitID);

        // If a working file is untracked in the current branch and would be overwritten by the checkout,
        // print "There is an untracked file in the way; delete it, or add and commit it first." and exit;
        checkFileConsistenceBetweenCommits(currentCommit, commit);

        // delete all files and add the checkout branch files
        deleteAllFiles(CWD);
        addCheckoutCommitFiles(commit);

        // update the HEAD(moves the current branch’s head to that commit node)
        updateCurrentBranch(resetSha1);

        // clear the staging area
        Index index = getIndex();
        clearIndex(index);

    }

    /**
     * Merges files from the given branch into the current branch
     * @param branch
     */
    public void merge(String branch){
        isInitialized();
        // check if the branch == head branch
        String head = getHead();
        if (head.equals(branch)){
            throw new GitletException(CANNOT_MERGE_WITH_ITSELF_MESSAGE);
        }

        // get the branch commit
        Commit branchCommit = getBranchCommit(branch);
        String branchCommitSha1 = branchCommit.getSha1();

        // get head commit
        Commit headCommit = getHeadCommit();
        String headCommitSha1 = headCommit.getSha1();

        // get the split point
        String splitPoint = getSplitPoint(headCommit, branchCommit);
        Commit splitPointCommit = getCommitbyId(splitPoint);
        // If the split point is the same commit as the given branch, then we do nothing;
        //A---B---C---D---E (master)
        //     \
        //      F---G---B (feature)
        if (splitPoint.equals(branchCommitSha1)){
            System.out.println(GIVEN_BRANCH_IS_ANCESTOR_MESSAGE);
            System.exit(0);
        }
        //If the split point is the current branch, then the effect is to check out the given branch
        //A---B---C (current-branch)
        //         \
        //          E---F---G (given-branch)
        // A---B---C---E---F---G (current-branch, given-branch)
        if (splitPoint.equals(headCommitSha1)){
            checkoutBranch(branch);
            System.out.println(BRANCH_FAST_FORWARDED_MESSAGE);
            System.exit(0);
        }
        Map<String, String> branchCommitFiles = branchCommit.getTreeFiles();
        Map<String, String> headCommitFiles = headCommit.getTreeFiles();
        Map<String, String> splitPointCommitFiles = splitPointCommit.getTreeFiles();

        Set<String> files = new HashSet<>();
        files.addAll(branchCommitFiles.keySet());
        files.addAll(headCommitFiles.keySet());
        files.addAll(splitPointCommitFiles.keySet());
        // get index
        Index index = getIndex();
        // check if the staged area is clea
        boolean isClean = index.isClean();
        if (!isClean){
            throw new GitletException(UNCOMMITTED_CHANGES_MESSAGE);
        }
        // check file consistence
        checkFileConsistenceBetweenCommits(headCommit, branchCommit);

        // staged for addition(compare head and result)
        // perform the 8 merge conditions

        /**
         *      splitPoint   head     branch    result
         * 1.    A           A         !A        !A      staged for addition
         * 2.    A          !A          A        !A      do nothing
         * 3.a   A          B           B                do nothing
         * 3.b   A          X           X                do nothing
         * 4.    X          A           X       A        do nothing
         * 5.    X          X           A       A        staged for addition
         * 6.    A          A           X       X       staged for remove
         * 7.    A          X           A       X       do nothing
         * 8.a   A          B           C       conflict
         * 8.b   A          B/X         X/B     conflict
         * 8.c   X          B           C       conflictt
         */
        boolean conflict = false;
        for (String file : files) {
            String branchContent = getContent(branchCommitFiles, file);
            String headContent = getContent(headCommitFiles, file);
            String splitPointContent = getContent(splitPointCommitFiles, file);
            if (branchContent != null && headContent != null && splitPointContent != null
            && splitPointContent.equals(headContent) && !splitPointContent.equals(branchContent)){
                byte[] fileContents = getBlobContentFromSha1(branchContent);
                Utils.writeContents(file, fileContents);
                index.addFileForAddition(file, branchContent);
            }/*else if (branchContent != null && headContent != null && splitPointContent != null
            && !splitPointContent.equals(headContent) && splitPointContent.equals(branchContent)){
                // do nothing
            } else if (branchContent == null && headContent == null && splitPointContent != null) {
                // do nothing
            }else if (branchContent != null && headContent != null && splitPointContent != null
            && !splitPointContent.equals(headContent) && headContent.equals(branchContent)){
                // do nothing
            }else if (splitPointContent == null && headContent != null && branchContent == null){
                // do nothing
            }*/else if (splitPointContent == null && headContent == null && branchContent != null){
                byte[] fileContents = getBlobContentFromSha1(branchContent);
                Utils.writeContents(file, fileContents);
                index.addFileForAddition(file, branchContent);
            }else if (splitPointContent != null && headContent != null && branchContent == null
            && splitPointContent.equals(headContent)){
                File file1 = new File(file);
                file1.delete();
                index.addFileForRemoval(file);
            }/*else if (splitPointContent != null && headContent == null && branchContent != null
                    && splitPointContent.equals(branchContent)){
                // do nothing
            }*/else if (branchContent != null && headContent != null && splitPointContent != null
            && !splitPointContent.equals(headContent) && !splitPointContent.equals(branchContent) && !headContent.equals(branchContent)){
                handleMergeConflict(file, headContent, branchContent);
                conflict = true;
            }else if (splitPointContent != null && headContent != null && branchContent == null && !splitPointContent.equals(headContent)){
                handleMergeConflict(file, headContent, branchContent);
                conflict = true;
            }else if (splitPointContent != null && headContent == null && branchContent != null && !splitPointContent.equals(branchContent)){
                handleMergeConflict(file, headContent, branchContent);
                conflict = true;
            }else if (splitPointContent == null && headContent != null && branchContent != null && !branchContent.equals(headContent)){
                handleMergeConflict(file, headContent, branchContent);
                conflict = true;
            }
        }

        // make commit
        String msg = "Merged " + branch + " into" + head;
        commitForMerge(msg, index, headCommit, branchCommit);

        if (conflict){
            System.out.println(MERFE_CONFLICT_MESSAGE);
        }
        
    }

    private void commitForMerge(String msg, Index index, Commit headCommit, Commit branchCommit) {

        if (index.isClean()){
            System.out.println(NO_INDEX_CHAGED_MESSAGE);
            return;
        }

        Tree parentTree = headCommit.getTree();
        Tree tree = buildTree(index, parentTree);
        LocalDateTime time = LocalDateTime.now();
        List<String> parentCommits = Arrays.asList(headCommit.getSha1(), branchCommit.getSha1());
        Commit commit = new Commit(time, msg, parentCommits, index, tree);

        persistObject(commit);
        persistObject(tree);
        clearIndex(index);
        updateCurrentBranch(commit.getSha1());

        // write commit metadata to logs/HEAD
        String unixTimestamp = toUnixTimestamp(time);
        String commitData = commit.getSha1() + " " + unixTimestamp + msg + "\n";
        Utils.appendContents(LOGS_HEAD, commitData);
    }



    /**
     * check whether thi repo is initialized
     */
    private void isInitialized(){
        if (!GITLET_DIR.exists()){
            System.out.println(NOT_IN_GITLET_DIR_MESSAGE);
            System.exit(0);
        }
    }



    private List<String> basciCheckFiles(String[] filePaths){
        List<String> validPaths = new ArrayList<>();
        Path repoRootPath = CWD.toPath();
        for (String filePath : filePaths) {
            // check existence
            File file = new File(filePath);
            if (!file.exists()){
                System.out.println(filePath + " does not exist");
                System.exit(0);
            }
            // check inside this repo?
            Path path = Paths.get(filePath).toAbsolutePath();
            if (!path.startsWith(repoRootPath)){
                System.out.println("fatal: " + path + " is outside repository at " + repoRootPath);
                System.exit(0);
            }
            if (!filePath.equals("gitlet")){
                validPaths.add(filePath);
            }
        }
        return validPaths;
    }

    private List<String> basciCheckFilesForRm(String[] filePaths, Commit parentCommit){
        List<String> validPaths = new ArrayList<>();
        Path repoRootPath = CWD.toPath();
        for (String filePath : filePaths) {
            String relativePath = getRelativePathtoCWD(filePath);
            if (parentCommit.treeContainsFile(relativePath)){
                validPaths.add(relativePath);
                continue;
            }
            // check existence
            File file = new File(relativePath);
            if (!file.exists()){
                System.out.println(relativePath + " does not exist");
                System.exit(0);
            }
            // check inside this repo?
            Path path = Paths.get(filePath).toAbsolutePath();
            if (!path.startsWith(repoRootPath)){
                System.out.println("fatal: " + path + " is outside repository at " + repoRootPath);
                System.exit(0);
            }
            if (!filePath.equals("gitlet")){
                validPaths.add(filePath);
            }
        }
        return validPaths;
    }

    /**
     * get the parent commit Id from the HEAD file (gitlet add)
     */
    private String getHeadCommitID(){
        String headContents = Utils.readContentsAsString(HEAD_FILE).trim();
        String branchPath = headContents.substring(5).trim();
        File branchFile = new File(GITLET_DIR, branchPath);
        return Utils.readContentsAsString(branchFile).trim();
    }


    /**
     * get parent comit
     */
    private Commit getCommitbyId(String CommitId){
        File parentCommitFile = join(OBJECT_DIR, CommitId.substring(0, 2), CommitId.substring(2));
        Commit Commit = Utils.readObject(parentCommitFile, Commit.class);
        return Commit;
    }

    /**
     * get head commit
     * @return
     */
    private Commit getHeadCommit(){
        String headCommitID = getHeadCommitID();
        Commit headCommit = getCommitbyId(headCommitID);
        return headCommit;
    }


    /**
     * list all files under this repo(exclude .getlet)
     * @param file
     * @return List<filepath relative to CWD>
     */
    public List<String> listAllFiles(File file){
        List<String> fileList = new ArrayList<>();
        if (file.isDirectory() && !file.getName().equals(".gitlet") && !file.getName().equals("gitlet")){
            File[] files = file.listFiles();
            if (files != null){
                for (File subFile : files) {
                    fileList.addAll(listAllFiles(subFile));
                }
            }
        }else if (file.isFile()){
            fileList.add(getRelativePathtoCWD(file));
        }
        return fileList;
    }


    /**
     * get the Map<file, sha1> of all files in this repo
     * @param file
     * @return
     */
    private Map<String, String> listFiletoSha1(File file){
        Map<String, String> filetoSha1 = new HashMap<>();
        if (file.isDirectory() && !file.getName().equals(".gitlet") && !file.getName().equals("gitlet")){
            File[] files = file.listFiles();
            if (files != null){
                for (File subFile : files) {
                    filetoSha1.putAll(listFiletoSha1(subFile));
                }
            }
        }else if (file.isFile()){
            Blob blob = new Blob(file.toString());
            String sha1 = blob.getSha1();
            String relativePath = getRelativePathtoCWD(file);
            filetoSha1.put(relativePath, sha1);
        }
        return filetoSha1;
    }


    /**
     * persistent object
     */
    private <T extends Persistable> void persistObject(T object) {
        String sha1 = object.getSha1();
        File objectFile = join(OBJECT_DIR, sha1.substring(0, 2));
        objectFile.mkdir();
        Utils.writeObject(new File(objectFile, sha1.substring(2)), object);
    }

    /**
     * clear index
     */
    private void clearIndex(Index index){
        index.clear();
        Utils.writeObject(INDEX_FILE, index);
    }

    /**
     * update the current branch reference to point to the new commit
     */
    private void updateCurrentBranch(String commitId){
        String headContent = Utils.readContentsAsString(HEAD_FILE).trim();
        String currentBranch = headContent.substring(5).trim();
        File branchFile = new File(GITLET_DIR, currentBranch);
        Utils.writeContents(branchFile, commitId);
    }


    private Tree buildTree(Index index, Tree parentTree){
        Tree tree = new Tree(parentTree);
        List<String> stagedFilesForRemoval = index.getStagedFilesForRemoval();
        if (!stagedFilesForRemoval.isEmpty()){
            for (String file : stagedFilesForRemoval) {
                tree.removeFile(file);
            }
        }
        Map<String, String> stagedFiles = index.getStagedFilesForAddition();
        for(Map.Entry<String, String> entry : stagedFiles.entrySet()){
            tree.addFile(entry.getKey(), entry.getValue());
        }
        return tree;
    }

    private Index getIndex(){
        Path indexPath = INDEX_FILE.toPath();
        Index index;
        if (! Files.exists(indexPath)){
            try {
                Files.createFile(indexPath);
                index = new Index();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }else{
            if (INDEX_FILE.length() == 0){
                index = new Index();
            }else{
                index = Utils.readObject(indexPath.toFile(), Index.class);
            }
        }
        return index;
    }

    private List<String> getUncheckedFiles(Commit currentCommit){
        Map<String, String> filetoSha1 = listFiletoSha1(CWD);
        Index index = getIndex();
        List<String> stagedFilesForRemoval = index.getStagedFilesForRemoval();
        List<String> untrackedFiles = new ArrayList<>();
        // files present in the working directory but neither staged for addition nor tracked.
        // This includes files that have been staged for removal, but then re-created without Gitlet’s knowledge.
        for(Map.Entry<String, String> entry : filetoSha1.entrySet()){
            String file = entry.getKey();
            if (!index.stagedFilesForAdditionContainsFile(file) && !currentCommit.getTreeFiles().containsKey(file)){
                untrackedFiles.add(file);
            }
        }

        for (String file : stagedFilesForRemoval) {
            File file1 = new File(file);
            if (file1.exists()){
                untrackedFiles.add(file);
            }
        }
        return untrackedFiles;
    }

    /**
     * delete all files under this repo
     * @param file
     */
    private void deleteAllFiles(File file){
        if (file.isDirectory() && !file.getName().equals(".gitlet") && !file.getName().equals("gitlet")){
            File[] files = file.listFiles();
            if (files != null){
                for (File subFile : files) {
                    deleteAllFiles(subFile);
                }
            }
        }
        file.delete();
    }

    /**
     * get the name of the head branch
     * @return
     */
    private String getHead(){
        String head = readContentsAsString(HEAD_FILE).replace("ref: refs/heads/", "");
        return head;
    }

    /**
     * git Commit by the abbr of CommitID
     * @param commitId
     * @return
     */
    private Commit getCommitbyAbbrID(String commitId){
        Commit commit = null;
        if (commitId.length() == 40){
            commit = getCommitbyId(commitId);
        }else{
            File[] files = OBJECT_DIR.listFiles();
            if (commitId.length() <= 2){
                List<String> candidateDir = new ArrayList<>();
                for (File f : files) {
                    String fileName = f.getName();
                    if (fileName.startsWith(commitId)){
                        candidateDir.add(fileName);
                    }
                }
                if (candidateDir.size() == 0){
                    throw new GitletException(NO_COMMIT_WITH_THAT_ID_EXIST_MESSAGE);
                }else if (candidateDir.size() == 1){
                    String first2Id = candidateDir.get(0);
                    File[] files1 = join(OBJECT_DIR, first2Id).listFiles();
                    if (files1.length  > 1){
                        throw new GitletException(ENTER_MORE_DIGITS_MESSAGE);
                    }else if (files1.length  == 0){
                        throw new GitletException(SOME_FILES_ARE_DELETED_ACCIDENTLY_MESSAGE);
                    }else{
                        File f = files1[0];
                        commit = Utils.readObject(f, Commit.class);
                    }
                }else{
                    throw new GitletException(NO_COMMIT_WITH_THAT_ID_EXIST_MESSAGE);
                }
            } else{
                // commitId.length > 2
                String firs2Id = commitId.substring(0, 2);
                boolean found = false;
                for (File f : files) {
                    String fileName = f.getName();
                    if (fileName.equals(firs2Id)){
                        found = true;
                        break;
                    }
                }
                if (found == false){
                    throw new GitletException(NO_COMMIT_WITH_THAT_ID_EXIST_MESSAGE);
                }else{
                    List<File> candidateCommits = new ArrayList<>();
                    File[] files1 = join(OBJECT_DIR, firs2Id).listFiles();
                    String last = commitId.substring(2);
                    for (File file2 : files1) {
                        String name = file2.getName();
                        if (name.startsWith(last)){
                            candidateCommits.add(file2);
                        }
                    }
                    if (candidateCommits.size() == 0){
                        throw new GitletException(NO_COMMIT_WITH_THAT_ID_EXIST_MESSAGE);
                    }else if (candidateCommits.size() == 1){
                        commit = Utils.readObject(candidateCommits.get(0), Commit.class);
                    }else{
                        throw new GitletException(ENTER_MORE_DIGITS_MESSAGE);
                    }
                }
            }
        }
        return commit;
    }

    private void checkFileConsistenceBetweenCommits(Commit currentCommit, Commit checkoutCommit){
        List<String> uncheckedFiles = getUncheckedFiles(currentCommit);
        for (String uncheckedFile : uncheckedFiles) {
            if (checkoutCommit.treeContainsFile(uncheckedFile)){
                System.out.println(UNCHECKED_FILE_MESSAGE);
                System.exit(0);
            }
        }
    }

    private void addCheckoutCommitFiles(Commit checkoutCommit){
        for(Map.Entry<String, String> entry : checkoutCommit.getTreeFiles().entrySet()){
            String file1 = entry.getKey();
            String sha1 = entry.getValue();
            byte[] fileContent = getBlobContentFromSha1(sha1);
            Utils.writeContents(file1, fileContent);
        }
    }

    /**
     * get the branch head commit and do some basic check
     * @param branch
     * @return
     */
    private Commit getBranchCommit(String branch){
        File file = join(BRANCH_HEAD_DIR, branch);
        if (!file.exists()){
            throw new GitletException(NO_SUCH_BRANCH_EXISTS_MESSAGE);
        }
        String commitID = readContentsAsString(file);
        Commit commit = getCommitbyId(commitID);
        return commit;
    }


    /**
     * get the parentCommits of this commit(including itself)
     * @param headCommit
     *        A---B---C---D---E
     * return A---B---C---D---E
     * @return
     */
    private List<String> getParentCommits(Commit headCommit){
        List<String> parentCommits = new ArrayList<>();
        parentCommits.add(headCommit.getSha1());
        while(headCommit.getParentCommitID() != null){
            String parentCommit = headCommit.getParentCommitID().get(0);
            parentCommits.add(parentCommit);
            headCommit = getCommitbyId(parentCommit);
        }
        Collections.reverse(parentCommits);
        return parentCommits;
    }

    /**
     * get the split point for merge command
     * @param headCommit
     * @param branchCommit
     * @return
     */
    private String getSplitPoint(Commit headCommit, Commit branchCommit){
        List<String> parentCommitsforHeadCommit = getParentCommits(headCommit);
        List<String> parentCommitsforBranchCommit = getParentCommits(branchCommit);
        String splitPoint = null;
        int minSize = Math.min(parentCommitsforHeadCommit.size(), parentCommitsforBranchCommit.size());
        for (int i = 0; i < minSize; i++) {
            if (parentCommitsforHeadCommit.get(i).equals(parentCommitsforBranchCommit.get(i))){
                splitPoint = parentCommitsforHeadCommit.get(i);
            }else{
                break;
            }
        }
        return splitPoint;
    }

    /**
     * if the file exists in the tree, return the content, else return null
     * @param commitTree
     * @param file
     * @return
     */
    private String getContent(Map<String, String> commitTree, String file){
        if (commitTree.containsKey(file)){
            return commitTree.get(file);
        }else{
            return null;
        }
    }

    private byte[] getBlobContentFromSha1(String sha1){
        Blob blob = readObject(join(OBJECT_DIR, sha1.substring(0, 2), sha1.substring(2)), Blob.class);
        byte[] content = blob.getContent();
        return content;
    }

    private void handleMergeConflict(String file, String headContentSha1, String branchContentSha1){
        String head = "<<<<<<< HEAD\n";
        byte[] headContent = getBlobContentFromSha1(headContentSha1);
        String separateLine = "=======\n";
        byte[] branchContent = getBlobContentFromSha1(branchContentSha1);
        String end = ">>>>>>>";
        writeContents(file, head, headContent, separateLine, branchContent, end);
    }

    /**
     * get the valid file for add/rm *
     * @param files
     * @return
     */
    private List<String> getValidFiles(List<String> files){
        List<String> validFiles = new ArrayList<>();
        for (String validFile : files) {
            File file = new File(validFile);
            if (file.isFile()){
                validFiles.add(validFile);
            }else if (file.isDirectory()){
                validFiles.addAll(listAllFiles(file));
            }
        }
        return validFiles;
    }

    private List<String> getValidFilesForRm(List<String> files, Commit parentCommit){
        List<String> validFiles = new ArrayList<>();
        for (String validFile : files) {
            if (parentCommit.treeContainsFile(validFile)){
                validFiles.add(validFile);
                continue;
            }
            File file = new File(validFile);
            if (file.isFile()){
                validFiles.add(validFile);
            }else if (file.isDirectory()){
                validFiles.addAll(listAllFiles(file));
            }
        }
        return validFiles;
    }

    private String getRelativePathtoCWD(File file){
        Path cwdPath = CWD.toPath().toAbsolutePath().normalize();
        Path filePath = file.toPath().toAbsolutePath().normalize();
        String relativePath = cwdPath.relativize(filePath).toString();
        return relativePath;
    }

    private String getRelativePathtoCWD(String file){
        return getRelativePathtoCWD(new File(file));
    }



        




}
