package gitlet;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static gitlet.Utils.toCommitDate;

/** Represents a gitlet commit object.
 *  TODO: It's a good idea to give a description here of what else this Class
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
    private String sha1HashCode;
    private String parentCommit;
    private HashMap<String, String> stagedfiles;
    private Tree tree;


    public Commit(LocalDateTime time, String message, String parentCommit, HashMap<String, String> stagedfiles, Tree tree) {
        this.message = message;
        this.parentCommit = parentCommit;
        this.commitTime = time;
        this.stagedfiles = stagedfiles;
        this.sha1HashCode = sha1();
        this.tree = tree;
    }


    public String toString(){
        String commitTime_ = toCommitDate(commitTime);
        return "commit " + sha1HashCode +
                "\n Data: " + commitTime_ +
                "\n      " + message;
    }


    private String sha1(){
        StringBuilder content = new StringBuilder();
        content.append("tree").append(tree.getSha1()).append("\n");
        if (parentCommit != null) {
            content.append("parent ").append(parentCommit).append("\n");
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

    public HashMap<String, String> getstagedFiles(){
        return stagedfiles;
    }

}
