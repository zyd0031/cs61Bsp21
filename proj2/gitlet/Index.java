package gitlet;

import java.io.Serializable;
import java.util.*;

public class Index implements Serializable {
    private static final long serialVersionUID = 2341433L;
    private Map<String, String> stagedFilesForAddition; // Map<FilePath, BlobSha1Hash>
    private List<String> stagedFilesForRemoval;

    public Index() {
        stagedFilesForAddition = new HashMap<>();
        stagedFilesForRemoval = new ArrayList<>();
    }


    /**
     * add files to the staging area
     * @param file
     * @param sha1
     */
    public void addFileForAddition(String file, String sha1){
        stagedFilesForAddition.put(file, sha1);
    }

    /**
     * get the staged files
     * @return
     */
    public Map<String, String> getStagedFilesForAddition(){
        return stagedFilesForAddition;
    }

    public List<String> getStagedFilesForAdditionList(){
        List<String> files = new ArrayList<>(stagedFilesForAddition.keySet());
        Collections.sort(files);
        return files;
    }

    /**
     * if the staged files contain file
     * @param file
     * @return
     */
    public boolean stagedFilesForAdditionContainsFile(String file){
        return stagedFilesForAddition.containsKey(file);
    }

    public boolean stagedFilesForRemovalContainsFile(String file){
        return stagedFilesForRemoval.contains(file);
    }

    public boolean containsFile(String file){
        if (stagedFilesForAddition.containsKey(file)){
            return true;
        }
        if (stagedFilesForRemoval.contains(file)){
            return true;
        }
        return false;
    }

    /**
     * get sha1Hash of file
     * @param file
     * @return
     */
    public String getSha1(String file){
        return stagedFilesForAddition.get(file);
    }

    /**
     * clear inndex
     */
    public void clear(){
        stagedFilesForAddition.clear();
        stagedFilesForRemoval.clear();
    }

    /**
     * add file to stagedFilesForRemoval
     * @param file
     */
    public void addFileForRemoval(String file){
        stagedFilesForRemoval.add(file);
    }

    public void removeFileForRemoval(String file){
        stagedFilesForRemoval.remove(file);
    }

    public void removeFileforAddition(String file){
        stagedFilesForAddition.remove(file);
    }

    public List<String> getStagedFilesForRemoval(){
        Collections.sort(stagedFilesForRemoval);
        return stagedFilesForRemoval;
    }

    public boolean isClean(){
        if(!stagedFilesForAddition.isEmpty()){
            return false;
        }
        if (!stagedFilesForRemoval.isEmpty()){
            return false;
        }
        return true;
    }


}
