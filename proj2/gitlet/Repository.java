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

        Instant epoch0 = Instant.EPOCH;
        LocalDateTime initialCommitTime = LocalDateTime.ofInstant(epoch0, ZoneOffset.UTC);
        String msg = "initial commit";
        Commit initCommit = new Commit(initialCommitTime, msg, null, new HashMap<>(), new Tree());
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

        Index index = getIndex();
        Map<String, String> stagedFiles = index.getStagedFilesMap();
        if (stagedFiles.isEmpty()){
            System.out.println(NO_STAGEDFILES_MESSAGE);
        }

        String parentCommitId = getParentCommitID();
        Commit parentCommit = Utils.readObject(new File(join(OBJECT_DIR, parentCommitId.substring(0, 2)), parentCommitId.substring(2)), Commit.class);
        Tree parentTree = parentCommit.getTree();

        Tree tree = buildTree(index, parentTree);

        LocalDateTime time = LocalDateTime.now();
        Commit commit = new Commit(time, msg, parentCommitId, stagedFiles, tree);

        persistObject(commit);
        persistObject(tree);
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
        Index index = getIndex();

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
        branches.sort(String::compareTo);
        String head = readContentsAsString(HEAD_FILE).replace("ref: refs/heads/", "");
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
        List<String> stagedFiles = index.getStagedFiles();
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


        // ——————————————————————————————————————————————————————————
        String parentCommitID = getParentCommitID();
        Commit parentCommit = getCommitbyId(parentCommitID);
        Map<String, String> treefiles = parentCommit.getTreeFiles();
        Map<String, String> filetoSha1 = listFiletoSha1(CWD);
        Map<String, String> stagedFilesMap = index.getStagedFilesMap();

        // Modifications Not Staged For Commit
        System.out.println("=== Modifications Not Staged For Commit ===");
        List<String> modifiedNotStaged = new ArrayList<>();
        List<String> deletedNotStaged = new ArrayList<>();
        // Tracked in the current commit, changed in the working directory, but not staged;
        // Not staged for removal, but tracked in the current commit and deleted from the working directory.
        for(Map.Entry<String, String> entry : treefiles.entrySet()){
            String file = entry.getKey();
            String sha1 = entry.getValue();
            if (!filetoSha1.getOrDefault(file, "").equals(sha1) && !index.containsFile(file)){
                modifiedNotStaged.add(file);
            }
            File file1 = new File(file);
            if (!stagedFilesForRemoval.contains(file) && !file1.exists()){
                deletedNotStaged.add(file);
            }
        }

        for(Map.Entry<String, String> entry : stagedFilesMap.entrySet()){
            String file = entry.getKey();
            String sha1 = entry.getValue();
            String workSha1 = filetoSha1.get(file);
            if (workSha1 == null){
                // Staged for addition, but deleted in the working directory
                modifiedNotStaged.add(file);
            } else if (!workSha1.equals(sha1)){
                // Staged for addition, but with different contents than in the working directory;
                modifiedNotStaged.add(file);
            }
        }
        modifiedNotStaged.sort(String::compareTo);
        deletedNotStaged.sort(String::compareTo);
        deletedNotStaged.forEach(file -> System.out.println(file + ("deleted")));
        modifiedNotStaged.forEach(file -> System.out.println(file + ("modified")));
        System.out.println();

        // Untracked Files
        System.out.println("=== Untracked Files ===");
        List<String> untrackedFiles = new ArrayList<>();
        // files present in the working directory but neither staged for addition nor tracked.
        // This includes files that have been staged for removal, but then re-created without Gitlet’s knowledge.
        for(Map.Entry<String, String> entry : filetoSha1.entrySet()){
            String file = entry.getKey();
            if (!index.containsFile(file) && !parentCommit.getTreeFiles().containsKey(file)){
                untrackedFiles.add(file);
            }
        }

        for (String file : stagedFilesForRemoval) {
            File file1 = new File(file);
            if (file1.exists()){
                untrackedFiles.add(file);
            }
        }
        untrackedFiles.sort(String::compareTo);
        untrackedFiles.forEach(file -> System.out.println(file);
        System.out.println();
        
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
         * 2. get last commit
         */
        String parentCommitID = getParentCommitID();
        Commit parentcommit = getCommitbyId(parentCommitID);

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
                if (parentcommit.stagedFilesContainsFile(filePath)){
                    String lastCommitsha1 = parentcommit.getSha1ofStagedFile(filePath);
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
    private Map<String, String> getLastCommitFilesContents(){
        String parentCommitId = getParentCommitID();
        File parentCommitFile = join(OBJECT_DIR, parentCommitId.substring(0, 2), parentCommitId.substring(2));
        Commit parentCommit = Utils.readObject(parentCommitFile, Commit.class);
        Map<String, String> files = parentCommit.getstagedFiles();
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
        if (file.isDirectory() && !file.getName().equals(".gitlet")){
            File[] files = file.listFiles();
            if (files != null){
                for (File subFile : files) {
                    fileList.addAll(listAllFiles(subFile));
                }
            }
        }else if (file.isFile()){
            fileList.add(file.toString());
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
        if (file.isDirectory() && !file.getName().equals(".gitlet")){
            File[] files = file.listFiles();
            if (files != null){
                for (File subFile : files) {
                    filetoSha1.putAll(listFiletoSha1(subFile));
                }
            }
        }else if (file.isFile()){
            String sha1 = Utils.getSha1(file);
            String filePath = file.toString();
            filetoSha1.put(filePath, sha1);
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


    private Tree buildTree(Index index, Tree parentTree){
        Tree tree = new Tree(parentTree);
        List<String> stagedFilesForRemoval = index.getStagedFilesForRemoval();
        if (!stagedFilesForRemoval.isEmpty()){
            for (String file : stagedFilesForRemoval) {
                tree.removeFile(file);
            }
        }
        Map<String, String> stagedFiles = index.getStagedFilesMap();
        for(Map.Entry<String, String> entry : stagedFiles.entrySet()){
            tree.addFile(entry.getKey(), entry.getValue());
        }
        return tree;
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
            index = Utils.readObject(indexPath.toFile(), Index.class);
        }
        return index;
    }

}
