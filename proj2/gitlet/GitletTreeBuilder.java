package gitlet;

import java.io.File;

public class GitletTreeBuilder {
    public static Tree buildTree(File directory) {
        Tree tree = new Tree(directory.getAbsolutePath());
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && !file.getName().equals(".gitlet")) {
                    tree.addChild(buildTree(file));
                }else{
                    tree.addChild(new Blob(file.toString()));
                }
            }
        }
        return tree;
    }
}
