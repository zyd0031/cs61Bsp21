package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

import static gitlet.Repository.findRepositoryRoot;

public class Blob extends GitletObject implements Serializable {

    private static final long SerialVersionUID = 3L;

    public Blob(String filePath){
        super(filePath);
    }

    @Override
    public String getType(){
        return "blob";
    }

    @Override
    public String getSha1Hash(){
        File file = getFile();
        byte[] content = Utils.readContents(file);
        String header = "blob " + content.length + "\0";
        return Utils.sha1(header, content);
    }



}
