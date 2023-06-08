package softnet;

public class MemorySize {
	public static int fromK(int kilobytes) {
		return kilobytes * 1024;
	}
	public static int fromM(int megabytes) {
		return megabytes * 1048576;
	}
	public static long fromG(int gigabytes) {
		return gigabytes * 1073741824L;
	}
	public static int fromMK(int megabytes, int kilobytes) {
		return megabytes * 1048576 + kilobytes * 1024;
	}
	public static long fromMK_L(int megabytes, int kilobytes) {
		return megabytes * 1048576L + kilobytes * 1024L;
	}
	public static int fromGMK(int gigabytes, int megabytes, int kilobytes) {
		return gigabytes * 1073741824 +  megabytes * 1048576 + kilobytes * 1024;
	}
	public static long fromGMK_L(int gigabytes, int megabytes, int kilobytes) {
		return gigabytes * 1073741824L +  megabytes * 1048576L + kilobytes * 1024L;
	}
}
