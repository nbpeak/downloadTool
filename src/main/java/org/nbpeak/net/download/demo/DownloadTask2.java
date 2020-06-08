package org.nbpeak.net.download.demo;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nbpeak.net.download.Utils;
import org.nbpeak.net.download.demo.pojo.DownloadInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.spi.FileSystemProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * 方案二：根据固定的大小将任务分割成诺干分，由固定的线程数去处理，每个线程绑定一个输出文件，所有任务结束后合并文件。
 */
@Slf4j
public class DownloadTask2 {
    private boolean chunked;
    private boolean supportBreakpoint;
    private DownloadInfo downloadInfo;
    private String eTag;
    private OkHttpClient client = new OkHttpClient();
    private static int COUNTER = 0;
    private final static int THREAD_COUNT = 8;
    private SpeedStatistician speedStatistician = new SpeedStatistician(speed -> {
        log.info("速度：" + Utils.byteToUnit(speed) + "/秒");
    });

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

    /**
     * 下载任务
     */
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
            String rangeStr = "bytes=";
            if (endPos <= 0) {
                rangeStr = rangeStr + startPos + "-";
            } else {
                rangeStr = rangeStr + startPos + "-" + endPos;
            }
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
                InputStream inputStream = response.body().byteStream();
                Path tmpPath = getTempPath();
                if (Files.notExists(tmpPath)) {
                    log.info("创建临时目录");
                    Files.createDirectories(tmpPath);
                }
                int fileNo = serialNum % THREAD_COUNT;
                Path filePath = tmpPath.resolve(fileNo + ".dt");
                log.info("开始写入：" + filePath);
                OutputStream outputStream = filePath.getFileSystem().provider().newOutputStream(filePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                byte[] buf = new byte[8192];
                int n;
                long nread = 0L;
                while ((n = inputStream.read(buf)) > 0) {
                    speedStatistician.add(n);
                    outputStream.write(buf, 0, n);
                    nread += n;
                }
                log.info("任务：" + serialNum + "，结束写入，共：" + nread);
                outputStream.close();
                return new Result(fileNo, filePath);
            } catch (IOException e) {
                log.error("下载出错了：", e);
                throw e;
            }
        }
    }

    public DownloadTask2(String url) throws IOException {
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

        long threshold = 1024 * 1024 * 2;// 每个任务的阈值
        List<TaskInfo> taskInfoList = new ArrayList<>();
        // 根据阈值将下载任务拆分成诺干分
        if (isSupportBreakpoint() && downloadInfo.getFileSize() > threshold) {
            // 只有支持断点续传，并且获取到了文件大小才能将文件分成多个任务运行。
            // 下面是按阈值分解任务，线程数固定，但任务数不固定，每个任务大小都差不多
            long startPos = 0, endPos = 0;
            long count = downloadInfo.getFileSize() / threshold;
            for (long i = 0; i < count; i++) {
                startPos = i * threshold;
                endPos = startPos + threshold - 1;
                taskInfoList.add(new TaskInfo(startPos, endPos));
            }
            if (endPos < downloadInfo.getFileSize() - 1) {
                taskInfoList.add(new TaskInfo(endPos + 1, -1));
            }
        } else {
            // 不支持断点续传，或者没获取到文件大小，就只有一个任务
            taskInfoList.add(new TaskInfo(0, downloadInfo.getFileSize()));
        }

        speedStatistician.start();
        Instant start = Instant.now();

        try {
            // 控制并发流的线程数（这是全局的设定，不太灵活）
            System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", String.valueOf(THREAD_COUNT));
            List<Result> resultList = taskInfoList.parallelStream().map(taskInfo -> {
                try {
                    return taskInfo.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
            Instant end = Instant.now();
            Duration time = Duration.between(start, end);
            log.info("下载结束，耗时：" + time.getSeconds() + " 秒");
            merge(Optional.of(resultList.stream().distinct().collect(Collectors.toList())));
            Files.delete(getTempPath());
        } catch (RuntimeException e) {
            log.error("出现异常：", e);
        } finally {
            speedStatistician.stop();
        }
        // 以下是线程池的实现
//        ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_COUNT);
//        List<Future<Result>> futures = threadPool.invokeAll(taskInfoList);
//        try {
//            List<Result> resultList = new ArrayList<>();
//            for (Future<Result> future : futures) {
//                resultList.add(future.get());
//            }
//            Instant end = Instant.now();
//            Duration time = Duration.between(start, end);
//            log.info("下载结束，耗时：" + time.getSeconds() + " 秒");
//            threadPool.shutdown();
//            merge(Optional.of(resultList.stream().distinct().collect(Collectors.toList())));
//            Files.delete(getTempPath());
//        } catch (ExecutionException e) {
//            log.error("出现异常：", e);
//        } finally {
//            speedStatistician.stop();
//        }
    }

    private void merge(Optional<List<Result>> resultList) throws IOException {
        log.info("开始合并文件");
        try (OutputStream outputStream = getProvider(Optional.of(downloadInfo.getLocalPath())).newOutputStream(downloadInfo.getLocalPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            resultList.get().stream().sorted(Comparator.comparingInt(Result::getNum)).forEach(result -> {
                try (InputStream inputStream = getProvider(Optional.of(result.getPath())).newInputStream(result.getPath(), StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE)) {
                    log.info("开始读" + result.getPath());
                    byte[] buf = new byte[1048576];
                    int len;
                    while ((len = inputStream.read(buf)) > 0) {
                        outputStream.write(buf, 0, len);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            log.info("文件合并结束：" + downloadInfo.getLocalPath() + "，文件大小：" + Files.size(downloadInfo.getLocalPath()));
        }
    }

    /**
     * 根据Path获取FileSystemProvider，NIO的Files.copy里面就是这样用的，很高大上的感觉
     *
     * @param filePath
     * @return
     */
    private FileSystemProvider getProvider(Optional<Path> filePath) {
        return filePath.get().getFileSystem().provider();
    }

    /**
     * 获取下载文件保存的临时目录
     *
     * @return
     */
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
            //有的Content-Disposition里面的filename后面是*=，是*=的文件名后面一般都带了编码名称，按它提供的编码进行解码可以避免文件名乱码
            int p2 = contentDisposition.indexOf("*=", p1);
            if (p2 >= 0) {
                //有的Content-Disposition里面会在文件名后面带上文件名的字符编码
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
