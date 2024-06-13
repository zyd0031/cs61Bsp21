package gitlet;

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
            System.out.println(NO_COMMAND_ENTERED_MESSAGE);
        }
        String firstArg = args[0];
        Repository repo = new Repository();
        switch(firstArg) {
            case "init":
                if (args.length == 1) {
                    repo.init();
                }
                else{
                    System.out.println(INCORRECT_OPERANDS_MESSAGE);
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
                    System.out.println(NO_COMMIT_MESSAGE);
                    System.exit(0);
                }else if (args.length == 2) {
                    repo.commit(args[1]);
                }else{
                    System.out.println(INCORRECT_OPERANDS_MESSAGE);
                }
                break;
            case "rm":
                repo.rm(Arrays.stream(args).skip(1).toArray(String[]::new));
                break;
            case "log":
                if (args.length == 1) {
                    repo.log();
                } else {
                    System.out.println(INCORRECT_OPERANDS_MESSAGE);
                }
                break;
            case "global-log":
                if (args.length == 1) {
                    repo.global_log();
                } else {
                    System.out.println(INCORRECT_OPERANDS_MESSAGE);
                }
                break;
            case "find":
                if (args.length == 2) {
                    repo.find(args[1]);
                } else {
                    System.out.println(INCORRECT_OPERANDS_MESSAGE);
                }
                break;
            case "status":
                if (args.length == 1) {
                    repo.status();
                }else{
                    System.out.println(INCORRECT_OPERANDS_MESSAGE);
                }
                break;
            default:
                System.out.println(INVALID_COMMAND_MESSAGE);


        }
    }
}
