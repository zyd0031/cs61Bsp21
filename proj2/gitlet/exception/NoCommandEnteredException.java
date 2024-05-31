package gitlet.exception;

public class NoCommandEnteredException extends GitletException {
    public NoCommandEnteredException(String meg){
        super(meg);
    }
}
