package org.nbpeak.net.download;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class DownloadTask {
    private boolean chunked;
    private boolean supportBreakpoint;
    private DownloadInfo downloadInfo;
    private String eTag;

    public DownloadTask(String url) throws IOException {
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
        OkHttpClient client = new OkHttpClient();
        // 创建客户端对象和请求对象，发起head请求
        Request headRequest = new Request.Builder()
                .head()
                .url(url)
                .build();

        // 发起请求，从响应头获取文件信息
        try (Response response = client.newCall(headRequest).execute()) {
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
                log.info("文件大小：" + Utils.byteToUnit(length));
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
    public void start(String saveTo) throws IOException {
        // 确保目录存在
        Path dirPath = Paths.get(saveTo);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        downloadInfo.setLocalPath(Paths.get(saveTo, downloadInfo.getFileName()));

        // 创建客户端对象和请求对象，发起get请求
        OkHttpClient client = new OkHttpClient();
        Request getRequest = new Request.Builder()
                .url(downloadInfo.getLocation())
                .build();

        System.out.println("下载任务开始");
        System.out.println("下载地址：" + downloadInfo.getLocation());
        System.out.println("保存地址：" + downloadInfo.getLocalPath());
        System.out.println("文件大小：" + Utils.byteToUnit(downloadInfo.getFileSize()));
        System.out.println("是否支持断点续传：" + Utils.yesOrNo(isSupportBreakpoint()));
        downloadInfo.setStatus(DownloadInfo.Status.RUNNING);
        try (Response response = client.newCall(getRequest).execute()) {
            final Path localPath = downloadInfo.getLocalPath();
            Files.deleteIfExists(localPath);
            final InputStream inputStream = response.body().byteStream();
            Files.copy(inputStream, localPath);
            downloadInfo.setStatus(DownloadInfo.Status.FINISHED);
            System.out.println("下载完成");
        }
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
