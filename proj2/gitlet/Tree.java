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


    public Tree() {
        this.files = new HashMap<>();
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
        if (files.isEmpty()){
            return "0".repeat(40);
        }

        StringBuffer sb = new StringBuffer();
        for(Map.Entry<String, String> entry : files.entrySet()){
            String path = entry.getKey();
            String hash = entry.getValue();
            sb.append("100644 blob ").append(hash).append("\0").append(path);
        }
        return Utils.sha1(sb.toString());
    }
}
