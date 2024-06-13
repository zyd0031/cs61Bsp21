package gitlet;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Index implements Serializable {
    private static final long serialVersionUID = 2341433L;
    private HashMap<String, String> stagedFiles;
    private Set<String> stagedFilesForRemoval;// Map<FilePath, BlobSha1Hash>

    public Index() {
        this.stagedFiles = new HashMap<>();
        this.stagedFilesForRemoval = new HashSet<>();
    }


    /**
     * add files to the staging area
     * @param file
     * @param sha1
     */
    public void addFile(String file, String sha1){
        stagedFiles.put(file, sha1);
    }

    /**
     * get the staged files
     * @return
     */
    public HashMap<String, String> getStagedFiles(){
        return stagedFiles;
    }

    /**
     * if the staged files contain file
     * @param file
     * @return
     */
    public boolean containsFile(String file){
        return stagedFiles.containsKey(file);
    }

    /**
     * get sha1Hash of file
     * @param file
     * @return
     */
    public String getSha1(String file){
        return stagedFiles.get(file);
    }

    /**
     * clear inndex
     */
    public void clear(){
        stagedFiles.clear();
        stagedFilesForRemoval.clear();
    }

    /**
     * add file to stagedFilesForRemoval
     * @param file
     */
    public void addFileForRemoval(String file){
        stagedFilesForRemoval.add(file);
    }

//    // if the file is currently staged, unstage it
//    public void unstageFile(String file){
//        if (stagedFiles.containsKey(file)){
//            stagedFiles.remove(file);
//        }
//    }

    public void removeFile(String file){
        stagedFiles.remove(file);
    }

}
