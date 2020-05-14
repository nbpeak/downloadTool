package org.nbpeak.net.download;

import java.io.IOException;

public class App {

    public static void main(String[] args) {
//        String uri1 = "http://cn.download.nvidia.com/Windows/Quadro_Certified/442.92/442.92-quadro-desktop-notebook-win10-64bit-international-dch-whql.exe";
//        String uri2 = "http://mirrors.njupt.edu.cn/centos/8.1.1911/isos/x86_64/CentOS-8.1.1911-x86_64-dvd1.iso";
        String uri3 = "http://newdriverdl.lenovo.com.cn/newlenovo/alldriversupload/69597/Nvidia-n2nnv16w.exe";
//        String uri3 = "https://github.com/square/okhttp/archive/parent-3.14.8.zip";
//        String uri3 = "http://www.baidu.com";
        try {
            final DownloadTask downloadTask = new DownloadTask(uri3);
            downloadTask.start("D:/temp");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
