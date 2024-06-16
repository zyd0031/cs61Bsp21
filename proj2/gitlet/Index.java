package gitlet;

import java.io.Serializable;
import java.util.*;

public class Index implements Serializable {
    private static final long serialVersionUID = 2341433L;
    private Map<String, String> stagedFiles = new HashMap<>(); // Map<FilePath, BlobSha1Hash>
    private List<String> stagedFilesForRemoval = new ArrayList<>();

    public Index() {
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
    public Map<String, String> getStagedFilesMap(){
        return stagedFiles;
    }

    public List<String> getStagedFiles(){
        List<String> files = new ArrayList<>(stagedFiles.keySet());
        Collections.sort(files);
        return files;
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

    public void removeFile(String file){
        stagedFiles.remove(file);
    }

    public List<String> getStagedFilesForRemoval(){
        Collections.sort(stagedFilesForRemoval);
        return stagedFilesForRemoval;
    }


}
