package gitlet;

import java.io.File;
import java.io.Serializable;

/**
 * The Git Object, map to bolb/tree
 */
public abstract class GitletObject implements Serializable {

    private static final long serialVersionUID = 14324132L;
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
    public abstract String getSha1();
}
