package gitlet;

import gitlet.exception.GitletException;

import java.util.Arrays;

import static gitlet.constant.MessageConstant.*;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Yudie Zheng
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new GitletException(NO_COMMAND_ENTERED_MESSAGE);
        }
        String firstArg = args[0];
        Repository repo = new Repository();
        switch(firstArg) {
            case "init":
                if (args.length == 1) {
                    repo.init();
                }
                else{
                    throw new GitletException(INCORRECT_OPERANDS_MESSAGE);
                }
                break;
            case "add":
                if (args[1].equals("*")) {
                    repo.addAll();
                }else{
                    repo.add(Arrays.stream(args).skip(1).toArray(String[]::new));
                }
                break;
            case "commit":
                if (args.length == 1) {
                    throw new GitletException(NO_COMMIT_MESSAGE);
                }else if (args.length == 2) {
                    repo.commit(args[1]);
                }else{
                    throw new GitletException(INCORRECT_OPERANDS_MESSAGE);
                }
                break;
            case "rm":
                repo.rm(Arrays.stream(args).skip(1).toArray(String[]::new));
                break;
            case "log":
                if (args.length == 1) {
                    repo.log();
                } else {
                    throw new GitletException(INCORRECT_OPERANDS_MESSAGE);
                }
                break;
            case "global-log":
                if (args.length == 1) {
                    repo.global_log();
                } else {
                    throw new GitletException(INCORRECT_OPERANDS_MESSAGE);
                }
                break;
            case "find":
                if (args.length == 2) {
                    repo.find(args[1]);
                } else {
                    throw new GitletException(INCORRECT_OPERANDS_MESSAGE);
                }
                break;
            case "status":
                if (args.length == 1) {
                    repo.status();
                }else{
                    throw new GitletException(INCORRECT_OPERANDS_MESSAGE);
                }
                break;
            case "checkout":
                if (args.length == 3) {
                    if (args[1].equals("--")) {
                        repo.checkFile(args[2]);
                    }else{
                        throw new GitletException(INCORRECT_OPERANDS_MESSAGE);
                    }
                }else if (args.length == 4) {
                    if (args[2].equals("--")) {
                        repo.checkoutCommitFile(args[1], args[3]);
                    }else{
                        throw new GitletException(INCORRECT_OPERANDS_MESSAGE);
                    }
                }else if (args.length == 2) {
                    repo.checkoutBranch(args[1]);
                }else{
                    throw new GitletException(INCORRECT_OPERANDS_MESSAGE);
                }
                break;
            case "branch":
                if (args.length == 2) {
                    repo.branch(args[1]);
                }else{
                    throw new GitletException(INCORRECT_OPERANDS_MESSAGE);
                }
                break;
            default:
                throw new GitletException(INCORRECT_OPERANDS_MESSAGE);


        }
    }
}
