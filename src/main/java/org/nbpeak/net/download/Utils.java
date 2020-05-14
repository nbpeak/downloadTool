package org.nbpeak.net.download;

public class Utils {
    /**
     * 字节单位自动转换
     *
     * @param byteSize
     * @return
     */
    public static String byteToUnit(long byteSize) {
        long size = 1;
        String[] unit = new String[]{"B", "KB", "MB", "GB", "TB"};
        String str = null;
        for (int i = 0; i < unit.length; i++) {
            long nextSize = size << 10;
            if (byteSize < nextSize) {
                str = String.format("%.2f%s", byteSize / (size + 0.0f), unit[i]);
                break;
            }
            size = nextSize;
        }
        return str == null ? String.format("%.2f%s", byteSize / (1 << 10 * (unit.length - 1)) + 0.0f, unit[unit.length - 1]) : str;
    }

    public static String yesOrNo(boolean yes) {
        return yes ? "是" : "否";
    }
}
