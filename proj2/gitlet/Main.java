package gitlet;

import java.util.Arrays;

import static gitlet.constant.MessageConstant.NO_COMMAND_ENTERED_MESSAGE;
import static gitlet.constant.MessageConstant.NO_COMMIT_MESSAGE;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author TODO
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
                    System.out.println("please enter the right command: gitlet init");
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
                }else{
                    repo.commit(args[1]);
                }

        }
    }
}
