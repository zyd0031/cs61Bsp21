package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static gitlet.constant.FileModeConstant.DIRECTORY;
import static gitlet.constant.FileModeConstant.NORMAL_FILE;

public class Tree extends GitletObject implements Serializable {
    private final List<GitletObject> children = new ArrayList<>();
    private static final long SerialVersionUID = 2L;

    public Tree(String filePath) {
        super(filePath);
    }

    public void addChild(GitletObject child){
        children.add(child);
    }

    @Override
    public String getType(){
        return "Tree";
    }

    @Override
    public String getSha1Hash(){
        List<Object> entries = new ArrayList<>();
        for (GitletObject child : children) {
            String mode = child instanceof Blob ? NORMAL_FILE : DIRECTORY;
            String entry = mode + " " + child.getFileName()+ "\0" + child.getSha1Hash();
            entries.add(entry);
        }
        return Utils.sha1(entries);
    }

}
