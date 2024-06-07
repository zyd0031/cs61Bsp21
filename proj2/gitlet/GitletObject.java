package gitlet;

import java.io.Serializable;

public abstract class GitletObject implements Serializable {
    private static final long serialVersionUID = 423452L;

    public abstract String getSha1();

    public abstract String getType();
}
