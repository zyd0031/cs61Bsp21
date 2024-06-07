package gitlet;

import jdk.jfr.Percentage;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

import static gitlet.Repository.findRepositoryRoot;

public class Blob implements Persistable {

    private static final long SerialVersionUID = 3L;
    private String filePath;

    public Blob(String filePath){
        this.filePath = filePath;
    }

    public String getType(){
        return "blob";
    }

    @Override
    public String getSha1(){
        File file = new File(filePath);
        byte[] content = Utils.readContents(file);
        String header = "blob " + content.length + "\0";
        return Utils.sha1(header, content);
    }

    public String getFileName(){
        File file = new File(filePath);
        return file.getName();
    }

}
