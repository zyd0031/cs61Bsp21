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
    public static final File BRANCH_HEAD_DIR = join(CWD, "refs", "heads");
    public static final File[] DIRS = {GITLET_DIR, HEAD_FILE, OBJECT_DIR, BRANCH_HEAD_DIR};


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
        for (File dir : DIRS) {
            if (dir.exists()){
                dir.delete();
            }
            dir.mkdir();
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
        Path repoRoot = basicCheck(filePaths);
        // then, do the add command
        add(repoRoot, filePaths);
    }

    /**
     * gitlet add *
     */
    public void addAll(){
        // before add, check if the repo is initialized
        Path repoRoot = isInitialized();
        // list all files under this repo
        List<String> files = listAllFiles(CWD);
        String[] filesArray = files.toArray(new String[0]);
        add(repoRoot, filesArray);
    }

    /**
     * gitlet commit mes
     * @param msg
     */
    public void commit(String msg){

        // check if it is inside a repository
        Path repoRoot = isInitialized();

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
    }

    /**
     * gitlet rm
     */
    public void rm(String[] filePaths){
        // do some basic check and return the repopath
        Path repoRoot = basicCheck(filePaths);

        // the main part of rm
        // 1. read the index file
        Path indexPath = repoRoot.resolve(".getlet/index");
        Index index;
        if (! Files.exists(indexPath)){
            index = null;
        }else{
            index = Utils.readObject(indexPath.toFile(), Index.class);
        }

        // 2. read the current commit
        String parentCommitId = getParentCommitID();
        Commit parentCommit = Utils.readObject(new File(join(OBJECT_DIR, parentCommitId.substring(0, 2)), parentCommitId.substring(2)), Commit.class);
        Commit commit = new Commit(parentCommit);
        Tree tree = commit.getTree();

        // 3. the main part of rm
        for (String filePath : filePaths) {
            boolean staged = false;
            boolean committed = false;
            if (index != null){
                if (index.containsFile(filePath)){
                    staged = true;
                    index.removeFile(filePath);
                }
            }
            if (tree.containsFile(filePath)){
                committed = true;
                tree.removeFile(filePath);
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
        persistObject(commit);

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

    /**
     * the main part of add
     */
    private void add(Path repoRoot, String[] filePaths){
        /** 1. read the index file if it exists else create one */
        Path indexPath = repoRoot.resolve(".getlet/index");
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
    private static Path isInitialized(){
        Path repoRoot = findRepositoryRoot(CWD);
        if (repoRoot == null){
            System.out.println(NOT_IN_GITLET_DIR_MESSAGE);
            System.exit(0);
        }
        return repoRoot;
    }

    /** get the repo root path */
    public static Path findRepositoryRoot(File file){
        Path current = file.toPath().toAbsolutePath();
        while (current != null){
            if (Files.exists(current.resolve(".getlet"))){
                return current;
            }
            current = current.getParent();
        }
        return null;
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

    private static void isFilesInsideCurrentRepo(String[] filePaths, Path repoRootPath){
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
     * clear staged files from index
     */
    private void clearStagedFiles(Index index){
        index.clearStagedFiles();
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
    private Path basicCheck(String[] filePaths){
        // check if it is inside a repository
        Path repoRoot = isInitialized();

        // chech whether the files indeed exist
        isFilesExists(filePaths);

        // check whether the files are within the current repository
        isFilesInsideCurrentRepo(filePaths, repoRoot);

        return repoRoot;
    }

}
