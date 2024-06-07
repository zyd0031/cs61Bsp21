package gitlet;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Index implements Serializable {
    private static final long serialVersionUID = 2341433L;
    private HashMap<String, String> stagedFiles; // Map<FilePath, BlobSha1Hash>

    public Index() {
        this.stagedFiles = new HashMap<>();
    }

    public Index(HashMap<String, String> stagedFiles) {
        this.stagedFiles = stagedFiles;
    }

    public void addFile(String file, String sha1){
        stagedFiles.put(file, sha1);
    }

    public HashMap<String, String> getStagedFiles(){
        return stagedFiles;
    }

    public boolean containsFile(String file){
        return stagedFiles.containsKey(file);
    }

    public String getSha1(String file){
        return stagedFiles.get(file);
    }

    public void clearStagedFiles(){
        stagedFiles.clear();
    }

    public void removeFile(String file){
        stagedFiles.remove(file);
    }


}
