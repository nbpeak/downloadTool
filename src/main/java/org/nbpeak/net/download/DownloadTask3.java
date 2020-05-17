package org.nbpeak.net.download;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * TODO: 方案三：类似方案二，但每个下载线程会将数据写入缓存队列，由另一组线程写到文件中
 */
@Slf4j
public class DownloadTask3 {
    private boolean chunked;
    private boolean supportBreakpoint;
    private DownloadInfo downloadInfo;
    private String eTag;
    private OkHttpClient client = new OkHttpClient();
    private static int COUNTER = 0;
    private final static int THREAD_COUNT = 8;
    private SpeedStatistician speedStatistician = new SpeedStatistician(speed -> {
        log.info("下载速度：" + Utils.byteToUnit(speed) + "/秒");
    });

    private BlockingQueue<BuffData> dataQueue = new PriorityBlockingQueue<>();

    class BuffData {
        private long startPos;
        private long endPos;
        private ByteBuffer buffer;

        public BuffData(long startPos, long endPos) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.buffer = ByteBuffer.allocate((int) (endPos - startPos + 1));
        }

        public void write(byte[] src) {
            write(src, 0, src.length);
        }

        public void write(byte[] src, int offset, int len) {
            buffer.put(src, offset, len);
        }

        public byte[] array() {
            return buffer.array();
        }

        public long getStartPos() {
            return startPos;
        }

        public long getEndPos() {
            return endPos;
        }
    }

    class Result {
        private int num;
        private Path path;

        public Result(int num, Path path) {
            this.num = num;
            this.path = path;
        }

        public int getNum() {
            return num;
        }

        public Path getPath() {
            return path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Result result = (Result) o;
            return num == result.num;
        }

        @Override
        public int hashCode() {
            return num;
        }
    }

    class TaskInfo implements Callable<Result> {
        private long startPos;
        private long endPos;
        private final int serialNum;

        public TaskInfo(long startPos, long endPos) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.serialNum = COUNTER++;
        }

        @Override
        public Result call() throws Exception {
            String rangeStr = "bytes=" + startPos + "-" + endPos;
            Request.Builder builder = new Request.Builder()
                    .get()
                    .header("Range", rangeStr)
                    .url(downloadInfo.getLocation());
            if (StringUtils.isNotEmpty(eTag)) {
                builder.header("ETag", eTag);
            }
            Request getRequest = builder.build();
            Call call = client.newCall(getRequest);
            log.info("任务：" + serialNum + "，开始下载：" + rangeStr);
            try (Response response = call.execute()) {
                log.info("任务：" + serialNum + "，获得响应，内容长度：" + response.body().contentLength());
                BuffData buffData = new BuffData(startPos, endPos);
                byte[] data = new byte[1024 * 8];
                int len;
                InputStream inputStream = response.body().byteStream();
                while ((len = inputStream.read(data)) > 0) {
                    speedStatistician.add(len);
                    buffData.write(data, 0, len);
                }
                dataQueue.offer(buffData);
                log.info("任务：" + serialNum + "，数据以写入缓存");
                return new Result(serialNum, null);
            } catch (IOException e) {
                log.error("下载出错了：", e);
                throw e;
            }
        }
    }

    public DownloadTask3(String url) throws IOException {
        initDownloadInfo(url);
    }

    public DownloadInfo getDownloadInfo() {
        return downloadInfo;
    }

    /**
     * 初始化下载信息，根据URL获取文件信息
     *
     * @param url
     * @throws IOException
     */
    private void initDownloadInfo(String url) throws IOException {
        log.info("初始化，获取下载文件信息...");
        // 创建客户端对象和请求对象，发起head请求
        Request headRequest = new Request.Builder()
                .head()
                .url(url)
                .build();

        // 发起请求，从响应头获取文件信息
        try (Response response = client.newCall(headRequest).execute()) {
            log.info("请求头================\n" + response.headers().toString());
            long length = -1;
            String fileName = getFileName(response);
            log.info("获取到文件名：" + fileName);

            // 获取分块传输标志
            String transferEncoding = response.header("Transfer-Encoding");
            this.chunked = "chunked".equals(transferEncoding);
            log.info("是否分块传输：" + Utils.yesOrNo(chunked));
            // 没有分块传输才可获取到文件长度
            if (!this.chunked) {
                String strLen = response.header("Content-Length");
                length = NumberUtils.toLong(strLen, length);
                log.info("文件大小：" + length);
            }

            // 是否支持断点续传
            String acceptRanges = response.header("Accept-Ranges");
            this.supportBreakpoint = "bytes".equalsIgnoreCase(acceptRanges);
            this.eTag = response.header("ETag");
            log.info("是否支持断点续传：" + Utils.yesOrNo(supportBreakpoint));
            log.info("ETag：" + eTag);

            // 创建下载信息
            this.downloadInfo = new DownloadInfo(new URL(url), length, fileName);
        }
    }

    /**
     * 开始下载
     *
     * @param saveTo 保存到哪
     * @throws IOException
     */
    public void start(String saveTo) throws IOException, InterruptedException {
        // 确保目录存在
        Path dirPath = Paths.get(saveTo);
        if (!Files.exists(dirPath)) {
            log.info("下载目录不存在，创建目录：" + dirPath.toAbsolutePath());
            Files.createDirectories(dirPath);
        }
        downloadInfo.setLocalPath(Paths.get(saveTo, downloadInfo.getFileName()));

        long threshold = 1024 * 1024 * 2;
        List<TaskInfo> taskInfoList = new ArrayList<>();
        if (isSupportBreakpoint() && downloadInfo.getFileSize() > threshold) {
            long startPos = 0, endPos = 0;
            long count = downloadInfo.getFileSize() / threshold;
            for (long i = 0; i < count; i++) {
                startPos = i * threshold;
                endPos = startPos + threshold - 1;
                taskInfoList.add(new TaskInfo(startPos, endPos));
            }
            if (endPos < downloadInfo.getFileSize() - 1) {
                taskInfoList.add(new TaskInfo(endPos + 1, downloadInfo.getFileSize() - 1));
            }
        } else {
            taskInfoList.add(new TaskInfo(0, downloadInfo.getFileSize()));
        }
        speedStatistician.start();

        Instant start = Instant.now();
        ExecutorService downloadPool = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<Result>> futures = taskInfoList.stream().map(downloadPool::submit).collect(Collectors.toList());
        Thread writeThread = new Thread(() -> {
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(downloadInfo.getLocalPath().toAbsolutePath().toString(), "rw")) {
                long writSize = 0;
                do {
                    BuffData buffData = dataQueue.take();
                    randomAccessFile.seek(buffData.getStartPos());
                    randomAccessFile.write(buffData.array());
                    log.info(buffData.getStartPos() + "-" + buffData.getEndPos() + " 已写入到文件，写入长度：" + buffData.array().length);
                    writSize += buffData.array().length;
                } while (writSize < downloadInfo.getFileSize());
                log.info("文件写入结束：" + downloadInfo.getLocalPath() + "，写入大小：" + writSize + "，文件总大小：" + randomAccessFile.length());
            } catch (IOException | InterruptedException e) {
                log.error("写文件出错了：", e);
            }
        }, "写文件线程");
        writeThread.start();
        try {
            for (Future<Result> future : futures) {
                if (!future.isDone()) {
                    future.get();
                }
            }
            Instant end = Instant.now();
            Duration time = Duration.between(start, end);
            log.info("下载结束，耗时：" + time.getSeconds() + " 秒");
            downloadPool.shutdown();
        } catch (ExecutionException e) {
            log.error("出现异常：", e);
        } finally {
            speedStatistician.stop();
        }
    }

    private FileSystemProvider getProvider(Optional<Path> filePath) {
        return filePath.get().getFileSystem().provider();
    }

    private Path getTempPath() {
        String tmpDirPath = System.getProperty("java.io.tmpdir");
        Path tmpPath = Paths.get(tmpDirPath, "javaDownload", downloadInfo.getFileName());
        return tmpPath;
    }

    /**
     * 根据响应头或URL获取文件名
     *
     * @param response
     * @return
     */
    private String getFileName(Response response) {
        String charset = "UTF-8";
        String uriPath = response.request().url().uri().getRawPath();
        String name = uriPath.substring(uriPath.lastIndexOf("/") + 1);

        String contentDisposition = response.header("Content-Disposition");
        if (contentDisposition != null) {
            int p1 = contentDisposition.indexOf("filename");
            int p2 = contentDisposition.indexOf("*=", p1);
            if (p2 >= 0) {
                int p3 = contentDisposition.indexOf("''", p2);
                if (p3 >= 0) {
                    charset = contentDisposition.substring(p2 + 2, p3);
                } else {
                    p3 = p2;
                }
                name = contentDisposition.substring(p3 + 2);
            } else {
                p2 = contentDisposition.indexOf("=", p1);
                if (p2 >= 0) {
                    name = contentDisposition.substring(p2 + 1);
                }
            }
        }
        try {
            name = URLDecoder.decode(name, charset);
        } catch (UnsupportedEncodingException e) {
        }
        return name;
    }

    public boolean isSupportBreakpoint() {
        return supportBreakpoint;
    }
}
