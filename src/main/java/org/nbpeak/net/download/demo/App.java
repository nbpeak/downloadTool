package org.nbpeak.net.download.demo;

import java.io.IOException;

public class App {

    public static void main(String[] args) {
//        String uri = "http://cn.download.nvidia.com/Windows/Quadro_Certified/442.92/442.92-quadro-desktop-notebook-win10-64bit-international-dch-whql.exe";
//        String uri = "http://mirrors.njupt.edu.cn/centos/8.1.1911/isos/x86_64/CentOS-8.1.1911-x86_64-dvd1.iso";
        String uri = "http://newdriverdl.lenovo.com.cn/newlenovo/alldriversupload/69597/Nvidia-n2nnv16w.exe";
//        String uri = "https://github.com/square/okhttp/archive/parent-4.6.0.zip";
//        String uri = "https://dl.hdslb.com/winclient/uploader/2.0.0.1054/bilibiliuploader-release-20170901-1054.exe";
//        String uri = "http://www.baidu.com";
        try {
            final DownloadTask3 downloadTask = new DownloadTask3(uri);
            downloadTask.start("D:/temp");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
