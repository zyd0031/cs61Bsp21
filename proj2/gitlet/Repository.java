package gitlet;

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
    public static final File LOGS_HEAD = join(GITLET_DIR, "logs", "HEAD");
    public static final File[] DIRS = {GITLET_DIR, OBJECT_DIR, BRANCH_HEAD_DIR};
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
            dir.mkdir();
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
        // build Tree and persistence it
        Tree emptyTree = new Tree();

        // handle commit
        Instant epoch0 = Instant.EPOCH;
        LocalDateTime initialCommitTime = LocalDateTime.ofInstant(epoch0, ZoneOffset.UTC);
        String msg = "initial commit";
        Commit initCommit = new Commit(initialCommitTime, msg, null, null, emptyTree);
        persistObject(initCommit);

        // set the current branch to master
        Utils.writeContents(HEAD_FILE, "ref: refs/heads/master");

        // create a master branch and point to the initial commit
        File masterHead = join(BRANCH_HEAD_DIR, "master");
        Utils.writeContents(masterHead, initCommit.getSha1());
    }

    /**
     * gitlet add a.txt b.txt
     */
    public void add(String[] filePaths){
        // do some basic check and return the repopath
        basicCheck(filePaths);
        // then, do the add command
        add_(filePaths);
    }

    /**
     * gitlet add *
     */
    public void addAll(){
        // before add, check if the repo is initialized
        isInitialized();
        // list all files under this repo
        List<String> files = listAllFiles(CWD);
        String[] filesArray = files.toArray(new String[0]);
        add_(filesArray);
    }

    /**
     * gitlet commit mes
     * @param msg
     */
    public void commit(String msg){

        // check if it is inside a repository
        isInitialized();

        Index index = Utils.readObject(INDEX_FILE, Index.class);
        HashMap<String, String> stagedFiles = index.getStagedFiles();
        if (stagedFiles == null || stagedFiles.isEmpty()){
            System.out.println(NO_STAGEDFILES_MESSAGE);
        }

        String parentCommitId = getParentCommitID();
        Commit parentCommit = Utils.readObject(new File(join(OBJECT_DIR, parentCommitId.substring(0, 2)), parentCommitId.substring(2)), Commit.class);
        Tree parentTree = parentCommit.getTree();

        Tree tree = buildTree(stagedFiles, parentTree);

        LocalDateTime time = LocalDateTime.now();
        Commit commit = new Commit(time, msg, parentCommitId, stagedFiles, tree);

        persistObject(commit);
        // persistObject(tree);
        clearStagedFiles(index);
        updateCurrentBranch(commit.getSha1());

        // write commit metadata to logs/HEAD
        String unixTimestamp = toUnixTimestamp(time);
        String commitData = commit.getSha1() + " " + unixTimestamp + msg + "\n";
        Utils.appendContents(LOGS_HEAD, commitData);

    }

    /**
     * gitlet rm
     */
    public void rm(String[] filePaths){
        // do some basic check and return the repopath
        basicCheck(filePaths);

        // the main part of rm
        // 1. read the index file
        Path indexPath = CWD.toPath().resolve(".getlet/index");
        Index index;
        if (! Files.exists(indexPath)){
            // create an index file
            try {
                INDEX_FILE.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            index = new Index();
        }else{
            index = Utils.readObject(indexPath.toFile(), Index.class);
        }

        // 2. read the current commit
        String parentCommitId = getParentCommitID();
        Commit parentCommit = Utils.readObject(new File(join(OBJECT_DIR, parentCommitId.substring(0, 2)), parentCommitId.substring(2)), Commit.class);

        // 3. the main part of rm
        for (String filePath : filePaths) {
            boolean staged = false;
            boolean committed = false;

            if (index.containsFile(filePath)){
                staged = true;
                index.removeFile(filePath);
            }

            if (parentCommit.treeContainsFile(filePath)){
                committed = true;
                index.addFileForRemoval(filePath);
            }

            File file = new File(filePath);
            if (file.exists()){
                file.delete();
            }

            if (!staged && !committed){
                System.out.println(NEITHER_STAGER_NOR_TRACKED + filePath);
            }
        }
        Utils.writeObject(INDEX_FILE, index);
    }

    /**
     * TODO consider merge
     * gitlet log (mimic "git log --first-parent")
     */
    public void log(){
        // check whether it is initialized
        isInitialized();

        String parentCommitId = getParentCommitID();
        Commit parentCommit = getCommitbyId(parentCommitId);
        System.out.println(parentCommit);

        String commitId = parentCommit.getParentCommitID();
        while(commitId != null){
            Commit commit = getCommitbyId(commitId);
            System.out.println(commit);
            commitId = commit.getParentCommitID();
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
      === Branches ===
      *master
      other-branch

      === Staged Files ===
      wug.txt
      wug2.txt

      === Removed Files ===
      goodbye.txt

      === Modifications Not Staged For Commit ===
      junk.txt (deleted)
      wug3.txt (modified)

      === Untracked Files ===
      random.stuff
     */
    public void status(){
        isInitialized();

        // Branches
        System.out.println("=== Branches ===");
        List<String> branches = plainFilenamesIn(BRANCH_HEAD_DIR);
        String head = readContentsAsString(HEAD_FILE).replace("ref: refs/heads/", "");
        for (String branch : branches) {
            if (branch.equals(head)){
                System.out.println("*" + branch);
            }else{
                System.out.println(branch);
            }
        }
        System.out.println();


        // Staged Files
        System.out.println("=== Staged Files ===");
        Path indexPath = INDEX_FILE.toPath();
        Index index;
        if (! Files.exists(indexPath)){
            index = null;
            System.out.println();
        }else{
            index = Utils.readObject(indexPath.toFile(), Index.class);
        }




        // Removed Files

        // Modifications Not Staged For Commit

        // Untracked Files


    }

    /**
     * the main part of add
     */
    private void add_(String[] filePaths){
        /** 1. read the index file if it exists else create one */
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
            index = Utils.readObject(indexPath.toFile(), Index.class);
        }

        /**
         * 2. get last commit staged files
         */
        HashMap<String, String> lastCommitFilesContents = getLastCommitFilesContents();

        /**
         * 3. add files to the staging area and persistence the content of the file to .gitlet/objects/XX/XXXXXXXXX
         * must meet the conditions
         * a. not exists in the staging area
         * b. exists in the staging area, but the content changed
         * c. content different from last commit
         */
        for (String filePath : filePaths){
            Blob blob = new Blob(filePath);
            String sha1 = blob.getSha1();

            if (index.containsFile(filePath)){
                // file in the stagde area
                String stagedsha1 = index.getSha1(filePath);
                if (! sha1.equals(stagedsha1)){
                    persistObject(blob);
                    index.addFile(filePath, sha1);
                }
            }else{
                // file not in the staged area
                // check if it is same as last commit
                if (lastCommitFilesContents.containsKey(filePath)){
                    String lastCommitsha1 = lastCommitFilesContents.get(filePath);
                    if (! sha1.equals(lastCommitsha1)){
                        persistObject(blob);
                        index.addFile(filePath, sha1);
                    }
                }else{
                    persistObject(blob);
                    index.addFile(filePath, sha1);
                }
            }
        }
        Utils.writeObject(INDEX_FILE, index);
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


    /** chech whether the files indeed exist */
    private static void isFilesExists(String[] filePaths){
        for (String path : filePaths) {
            File file = new File(path);
            if (!file.exists()){
                System.out.println(path + " does not exist");
                System.exit(0);
            }
        }
    }

    private static void isFilesInsideCurrentRepo(String[] filePaths){
        Path repoRootPath = CWD.toPath();
        for (String filePath : filePaths) {
            Path path = Paths.get(filePath).toAbsolutePath();
            if (!repoRootPath.startsWith(path)){
                System.out.println("fatal: " + path + " is outside repository at " + repoRootPath);
                System.exit(0);
            }
        }
    }

    /**
     * get the parent commit Id from the HEAD file (gitlet add)
     */
    private String getParentCommitID(){
        String headContents = Utils.readContentsAsString(HEAD_FILE).trim();
        String branchPath = headContents.substring(5).trim();
        File branchFile = new File(GITLET_DIR, branchPath);
        return Utils.readContentsAsString(branchFile).trim();
    }

    /**
     * Get the contents of the files in the last commit
     */
    private HashMap<String, String> getLastCommitFilesContents(){
        String parentCommitId = getParentCommitID();
        File parentCommitFile = join(OBJECT_DIR, parentCommitId.substring(0, 2), parentCommitId.substring(2));
        Commit parentCommit = Utils.readObject(parentCommitFile, Commit.class);
        HashMap<String, String> files = parentCommit.getstagedFiles();
        return files;
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
     * list all files under this repo(exclude .getlet)
     * @param file
     * @return List<filepath relative to CWD>
     */
    private List<String> listAllFiles(File file){
        List<String> fileList = new ArrayList<>();
        Path basePath = CWD.toPath();
        if (file.isDirectory() && !file.getName().equals(".gitlet")){
            File[] files = file.listFiles();
            if (files != null){
                for (File subFile : files) {
                    fileList.addAll(listAllFiles(subFile));
                }
            }
        }else if (file.isFile()){
            Path filePath = file.toPath();
            String relativePath = basePath.relativize(filePath).toString();
            fileList.add(relativePath);
        }
        return fileList;
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
    private void clearStagedFiles(Index index){
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


    private Tree buildTree(HashMap<String, String> stagedFiles, Tree parentTree){
        Tree tree = new Tree(parentTree);
        for(Map.Entry<String, String> entry : stagedFiles.entrySet()){
            tree.addFile(entry.getKey(), entry.getValue());
        }
        return tree;
    }

    private Tree getParentCommitTree(String parentCommitId){
        Commit parentCommit = Utils.readObject(new File(join(OBJECT_DIR, parentCommitId.substring(0, 2)), parentCommitId.substring(2)), Commit.class);
        Tree parentTree = parentCommit.getTree();
        return parentTree;
    }

    private Tree getParentCommitTree(){
        String parentCommitId = getParentCommitID();
        Commit parentCommit = Utils.readObject(new File(join(OBJECT_DIR, parentCommitId.substring(0, 2)), parentCommitId.substring(2)), Commit.class);
        Tree parentTree = parentCommit.getTree();
        return parentTree;
    }

    /**
     * do some basic check and return the rootPath
     * @param filePaths
     * @return
     */
    private void basicCheck(String[] filePaths){
        // check if it is inside a repository
        isInitialized();

        // chech whether the files indeed exist
        isFilesExists(filePaths);

        // check whether the files are within the current repository
        isFilesInsideCurrentRepo(filePaths);

    }

}
