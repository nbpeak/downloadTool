package org.nbpeak.net.download.demo.pojo;

import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DownloadInfo {
    private String fileName;
    private long fileSize;
    private long saveSize;
    private Status status = Status.WAITING;
    private Path localPath;
    private URL location;
    private String description;
    private URL refLocation;
    private long lastConnectTime;

    public DownloadInfo() {
    }

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

    public long getLastConnectTime() {
        return lastConnectTime;
    }

    public void setLastConnectTime(long lastConnectTime) {
        this.lastConnectTime = lastConnectTime;
    }

    public long getSaveSize() {
        return saveSize;
    }

    public void setSaveSize(long saveSize) {
        this.saveSize = saveSize;
    }

    public void setLocation(URL location) {
        this.location = location;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Status getStatus() {
        return this.status;
    }

    public String toString() {
        String time = String.valueOf(lastConnectTime);
        try {
            time = Instant.ofEpochMilli(lastConnectTime).atZone(ZoneId.systemDefault()).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
        }
        return "DownloadInfo{" +
                "fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", saveSize=" + saveSize +
                ", status=" + status +
                ", localPath=" + localPath +
                ", location=" + location +
                ", description='" + description + '\'' +
                ", refLocation=" + refLocation +
                ", lastConnectTime=" + time +
                '}';
    }

    public enum Status {
        WAITING("等待"), RUNNING("运行中"), STOPPED("停止"), FINISHED("完成");
        private String value;

        Status(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
