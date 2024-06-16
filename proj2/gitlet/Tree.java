package gitlet;

import java.io.File;
import java.io.Serializable;
import java.sql.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Tree implements Persistable {
    private static final long SerialVersionUID = 2L;
    // <filePath, Sha1Hash>
    private Map<String, String> files;

    public Tree(Tree tree) {
        this.files = new HashMap<>(tree.files);
    }

    public Tree(HashMap<String, String> files) {
        this.files = new HashMap<>(files);
    }

    public Tree() {
        this.files = new HashMap<>();
    }

    public String getType(){
        return "Tree";
    }

    public void addFile(String path, String hash){
        files.put(path, hash);
    }

    public void removeFile(String path){
        files.remove(path);
    }

    public Map<String, String> getFiles(){
        return files;
    }

    public boolean containsFile(String path){
        return files.containsKey(path);
    }

    public List<String> getFilesList(){
        return new ArrayList<>(files.keySet());
    }

    public String getFileSha1(String path){
        return files.get(path);
    }

    @Override
    public String getSha1(){
        List<String> list = new ArrayList<>();
        for (String filePath : files.keySet()) {
            Blob blob = new Blob(filePath);
            StringBuffer sb = new StringBuffer();
            sb.append("100644 blob ").append(blob.getSha1()).append("\0").append(filePath);
            list.add(sb.toString());
        }
        return Utils.sha1(list);
    }
}
