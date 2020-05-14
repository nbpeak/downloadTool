package org.nbpeak.net.download;

import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class DownloadInfo {
    private String fileName;
    private final long fileSize;
    private Status status = Status.WAITING;
    private Path localPath;
    private final URL location;
    private String description;
    private URL refLocation;
    private LocalDateTime lastConnectTime;

    public DownloadInfo(URL location, long fileSize) {
        this(location, fileSize, "");
    }

    public DownloadInfo(URL location, long fileSize, String fileName) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.location = location;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Path getLocalPath() {
        return localPath;
    }

    public void setLocalPath(Path localPath) {
        this.localPath = localPath;
    }

    public URL getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public URL getRefLocation() {
        return refLocation;
    }

    public void setRefLocation(URL refLocation) {
        this.refLocation = refLocation;
    }

    public LocalDateTime getLastConnectTime() {
        return lastConnectTime;
    }

    public void setLastConnectTime(LocalDateTime lastConnectTime) {
        this.lastConnectTime = lastConnectTime;
    }

    enum Status {
        WAITING("等待"), RUNNING("运行中"), STOPPED("停止"), FINISHED("完成");
        private String value;

        Status(String value) {
            this.value = value;
        }
    }

}
