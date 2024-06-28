package gitlet;

import jdk.jfr.Percentage;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;


public class Blob implements Persistable {

    private static final long SerialVersionUID = 3L;
    private String filePath;
    private byte[] fileContent;
    private String sha1;

    public Blob(String filePath){
        this.filePath = filePath;
        this.fileContent = Utils.readContents(filePath);
        this.sha1 = sha1();

    }

    public byte[] getContent(){
        return fileContent;
    }

    @Override
    public String getSha1(){
        return sha1;
    }

    public String sha1(){
        String header = "blob " + fileContent.length + "\0";
        return Utils.sha1(header, fileContent);
    }


}
