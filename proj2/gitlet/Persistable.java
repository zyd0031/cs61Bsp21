package gitlet;

import java.io.Serializable;

public interface Persistable extends Serializable {
    String getSha1();
}
