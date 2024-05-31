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
import static gitlet.constant.FailureCaseConstant.ALREADY_INITIALIZED;

// TODO: any imports you need here

/** Represents a gitlet repository.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
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

    /** content of the 。/getlet/index file -----> HashMap<AbsoluateFilePath, sha1Hash>*/
    // private HashMap<String, String> stagedFiles;
    /** last commit info */
    private HashMap<String, String> lastCommitFiles;

    /* TODO: fill in the rest of this class. */

    public Repository() {
    }

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
        Tree emptyTree = buildTree();

        // handle commit
        Instant epoch0 = Instant.EPOCH;
        LocalDateTime initialCommitTime = LocalDateTime.ofInstant(epoch0, ZoneOffset.UTC);
        String msg = "initial commit";
        Commit initCommit = new Commit(initialCommitTime, msg, null, null, emptyTree);

        String commitUid = initCommit.getsha1();
        File commitDir = join(OBJECT_DIR, commitUid.substring(0, 2));
        commitDir.mkdir();
        Utils.writeObject(new File(commitDir, commitUid.substring(2)), initCommit);

        // create a master branch and point to the initial commit
        File masterHead = join(BRANCH_HEAD_DIR, "master");
        Utils.writeContents(masterHead, commitUid);

        // set the current branch to master
        Utils.writeContents(HEAD_FILE, "ref: refs/heads/master");
    }

    /**
     * gitlet add a.txt b.txt
     */
    public void add(String[] filePaths){
        // check if it is inside a repository
        Path repoRoot = isInitialized();

        // chech whether the files indeed exist
        isFilesExists(filePaths);

        // check whether the files are within the current repository
        isFilesInsideCurrentRepo(filePaths, repoRoot);

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
        Tree tree = buildTree();

        LocalDateTime time = LocalDateTime.now();
        List<String> parentCommits = Arrays.asList(getParentCommitID());
        HashMap<String, String> stagedFiles = Utils.readObject(INDEX_FILE, Index.class).getStagedFiles();
        Commit commit = new Commit(time, msg, parentCommits, stagedFiles, tree);



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
            HashMap<String, String> stagedFiles = Utils.readObject(indexPath.toFile(), HashMap.class);
            index = new Index(stagedFiles);
        }

        /**
         * 2. add files to the staging area
         * must meet the conditions
         * a. not exists in the staging area
         * b. exists in the staging area, but the content changed
         * c. content different from last commit
         */
        for (String filePath : filePaths){
            Blob blob = new Blob(filePath);
            String sha1 = blob.getSha1Hash();
            HashMap<String, String> lastCommitFilesContents = getLastCommitFilesContents();
            if (index.containsFile(filePath)){
                // file in the stagde area
                String stagedsha1 = index.getSha1(filePath);
                if (! sha1.equals(stagedsha1)){
                    index.addFile(filePath, sha1);
                }
            }else{
                // file not in the staged area
                // check if it is same as last commit
                if (lastCommitFiles.containsKey(filePath)){
                    String lastCommitsha1 = lastCommitFiles.get(filePath);
                    if (! sha1.equals(lastCommitsha1)){
                        index.addFile(filePath, sha1);
                    }
                }else{
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
            System.out.println("fatal: not a gitlet repository (or any of the parent directories): .gitlet");
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
     * build tree and store tree
     */
    private static Tree buildTree(){
        Tree tree = GitletTreeBuilder.buildTree(CWD);
        String treeUid = tree.getSha1Hash();
        File treeDir = join(OBJECT_DIR, treeUid.substring(0,2));
        treeDir.mkdir();
        Utils.writeObject(new File(treeDir, treeUid.substring(2)), tree);
        return tree;
    }

}
