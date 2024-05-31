package gitlet;

import java.util.Arrays;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author TODO
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        // TODO: what if args is empty?
        if (args.length == 0) {
            System.out.println("please enter the command");
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
                repo.commit(args[1]);
        }
    }
}
