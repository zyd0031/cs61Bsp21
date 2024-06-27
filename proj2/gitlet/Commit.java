package gitlet;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static gitlet.Utils.toCommitDate;

/** Represents a gitlet commit object.
 *  does at a high level.
 *
 *  @author Yudie Zheng
 */
public class Commit implements Persistable {
    /**
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    private static final long serialVersionUID = 1L;

    /** The message of this Commit. */
    private String message;
    private LocalDateTime commitTime;
    private final String sha1HashCode;
    private List<String> parentCommits;
    private Index index;
    private Tree tree;


    /**
     * regular commit
     * @param time
     * @param message
     * @param parentCommits
     * @param index
     * @param tree
     */
    public Commit(LocalDateTime time, String message, List<String> parentCommits, Index index, Tree tree) {
        this.message = message;
        this.parentCommits = parentCommits;
        this.commitTime = time;
        this.index = index;
        this.tree = tree;
        this.sha1HashCode = sha1();
    }


    public String toString(){
        String commitTime_ = toCommitDate(commitTime);
        return "===\n" +
                "commit " + sha1HashCode + "\n" +
                "Data: " + commitTime_ + "\n" +
                message + ".\n";
    }

    private String sha1(){
        StringBuilder content = new StringBuilder();
        content.append("tree").append(tree.getSha1()).append("\n");
        if (parentCommits != null && !parentCommits.isEmpty()){
            for (String parentCommit : parentCommits) {
                content.append("parent ").append(parentCommit).append("\n");
            }
        }
        content.append(commitTime.toEpochSecond(java.time.ZoneOffset.UTC)).append(" +0000\n");
        content.append(message).append("\n");
        String string = content.toString();
        int size = string.getBytes().length;
        String header = "commit " + size + "\0";
        return Utils.sha1(header, string);
    }

    @Override
    public String getSha1(){
        return sha1HashCode;
    }


    public Tree getTree(){
        return tree;
    }

    public Map<String, String> getstagedFiles(){
        return index.getStagedFilesForAddition();
    }

    public List<String> getParentCommitID(){
        return parentCommits;
    }

    public LocalDateTime getCommitTime(){
        return commitTime;
    }


    /**
     * check whether the tree contains file
     * @param file
     * @return
     */
    public boolean treeContainsFile(String file){
        return tree.containsFile(file);
    }

    public String treeFileSha1(String file){
        return tree.getFileSha1(file);
    }

    public boolean stagedFilesContainsFile(String file){
        return index.stagedFilesContainsFile(file);
    }

    public String getSha1ofStagedFile(String file){
        return index.getSha1(file);
    }
    
    public Map<String, String> getTreeFiles(){
        Map<String, String> files = tree.getFiles();
        return files;

    }

}
