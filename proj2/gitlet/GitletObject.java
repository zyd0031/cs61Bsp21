package gitlet;

import java.io.File;

/**
 * The Git Object, map to bolb/tree
 */
public abstract class GitletObject {

    private String filePath;

    public GitletObject(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName(){
        File file = new File(filePath);
        return file.getName();
    }

    protected File getFile(){
        return new File(filePath);
    }

    public abstract String getType();
    public abstract String getSha1Hash();
}
