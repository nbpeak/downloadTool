import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;

public class Demo {
    @Test
    public void f1() {
        final Properties properties = System.getProperties();
        final Enumeration<Object> keys = properties.keys();
        while (keys.hasMoreElements()) {
            final Object key = keys.nextElement();
            System.out.println(key + ":" + properties.getProperty(key.toString()));
        }

        System.out.println(System.getProperty("java.io.tmpdir"));
    }

    @Test
    public void f2() throws IOException {
        System.out.println(System.getProperty("java.io.tmpdir"));
        final Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        System.out.println(tmpDir.resolve("abc"));
        System.out.println(tmpDir.resolveSibling("abc"));
        System.out.println(tmpDir.subpath(2, 4));
        final Path lnkPath = Paths.get("C:\\Users\\happy\\Desktop\\Word.lnk");
        System.out.println(Files.exists(lnkPath));
        System.out.println(Files.size(lnkPath));
        System.out.println(lnkPath.toAbsolutePath());
        System.out.println(lnkPath.toRealPath());
    }

    @Test
    public void f3() throws IOException {

        int threadCount = 8;
        long total = 412314, taskSize = total / threadCount;
        for (int i = 0; i < threadCount; i++) {
            long startPos = i * taskSize;
            long endPos = startPos + taskSize;
            if (i == threadCount - 1) {
                endPos = total;
            }
            System.out.println("startPos：" + startPos + ",endPos：" + endPos);
        }
    }

    @Test
    public void f6() throws IOException {

        long threshold = 1024 * 1024 * 2;
        long total = 1489445656;
        long startPos, endPos = 0;
        for (; ; ) {
            startPos = endPos;
            endPos = startPos + threshold;
            if (endPos >= total) {
                endPos = total;
                System.out.println("startPos：" + startPos + ",endPos：" + endPos);
                break;
            }
            System.out.println("startPos：" + startPos + ",endPos：" + endPos);
        }
    }

    public void f4() throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile("D:/temp/a.txt", "r");
        System.out.println("文件总长度：" + randomAccessFile.length());
        int start = 10, end = 20, len = end - start;
        ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream(len);
        randomAccessFile.seek(start);
        byte[] buf = new byte[10];
        int n = 0, t = n;
        while ((n = randomAccessFile.read(buf)) > 0) {
            t += n;
            if (t < len) {
                dataOutputStream.write(buf);
            } else {
                dataOutputStream.write(buf, 0, len - t + n);
            }
        }
        randomAccessFile.close();
        System.out.println(dataOutputStream.toString("UTF-8"));
        //总长：120
        //已读：100
        //现在读了：30

    }

    @Test
    public void f5() throws IOException, InterruptedException {
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
        scheduledThreadPoolExecutor.scheduleAtFixedRate(() -> {
            System.out.println("现在时间：" + LocalTime.now());
        }, 0, 1, TimeUnit.SECONDS);
        Thread.sleep(10000);
        scheduledThreadPoolExecutor.shutdown();
        System.out.println(scheduledThreadPoolExecutor.isShutdown());
        System.out.println(scheduledThreadPoolExecutor.isTerminated());
    }

    @Test
    public void f7() {
        List<Task1> list = new ArrayList<>();
        list.add(new Task1(0, 3, null, null));
        list.add(new Task1(7, 9, null, null));
        list.add(new Task1(14, 25, null, null));
        list.add(new Task1(4, 6, null, null));
        list.add(new Task1(10, 13, null, null));
        Collections.sort(list, (a, b) -> a.end - b.start);
        System.out.println(list);
    }

    @Test
    public void f8() {
        BuffData buffData = new BuffData(0, 100);
        byte[] b = new byte[32];
        for (int i = 0; i < 5; i++) {
            System.out.println(buffData.write(b));
        }
    }

    @Test
    public void forkJoinDemo() throws InterruptedException {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            list.add(String.valueOf(i + 1));
        }

        CountDownLatch downLatch = new CountDownLatch(list.size());
        ForkJoinPool pool = new ForkJoinPool(16);
        RecursiveAction action = new Task1(0, list.size(), list, downLatch);
        pool.submit(action);
        downLatch.await();
        pool.shutdown();
    }

    class Task1 extends RecursiveAction {
        private final int THRESHOLD = 20;

        private int start;
        private int end;
        private List<String> list;
        private CountDownLatch downLatch;

        public Task1(int start, int end, List<String> list, CountDownLatch downLatch) {
            this.start = start;
            this.end = end;
            this.list = list;
            this.downLatch = downLatch;
        }

        @Override
        protected void compute() {
            if ((end - start) <= THRESHOLD) {
                for (int i = start; i < end; i++) {
                    System.out.println(Thread.currentThread().getName() + ": " + list.get(i));
                    downLatch.countDown();
                }
            } else {
                int middle = (start + end) / 2;
                invokeAll(new Task1(start, middle, list, downLatch), new Task1(middle, end, list, downLatch));
            }

        }
    }

    class BuffData {
        private long startPos;
        private long endPos;
        private ByteBuffer buffer;

        public BuffData(long startPos, long endPos) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.buffer = ByteBuffer.allocate((int) (endPos - startPos));
        }

        public int write(byte[] src) {
            return write(src, 0, src.length);
        }

        public int write(byte[] src, int offset, int len) {
            int remaining = buffer.remaining();
            int writeLen = Math.min(remaining, len);
            buffer.put(src, offset, writeLen);
            return writeLen == 0 ? -1 : writeLen;
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
}
