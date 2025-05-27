package dacs1.v2;

import java.io.Serializable;
import java.sql.Timestamp;

public class FileRecord implements Serializable {
    public String filename;
    public String sender;
    public String receiver;
    public long size;
    public Timestamp uploadTime;

    public FileRecord(String filename, String sender, String receiver, long size, Timestamp uploadTime) {
        this.filename = filename;
        this.sender = sender;
        this.receiver = receiver;
        this.size = size;
        this.uploadTime = uploadTime;
    }
}