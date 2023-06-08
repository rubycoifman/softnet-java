package softnet;

public class TimeSpan {
	public static int fromMS(int minutes, int seconds) {
		return minutes * 60 + seconds;
	}
	public static int fromHMS(int hours, int minutes, int seconds) {
		return hours * 3600 + minutes * 60 + seconds;
	}
	public static int fromDHMS(int days, int hours, int minutes, int seconds) {
		return days * 86400 + hours * 3600 + minutes * 60 + seconds;
	}
	public static int fromMinutes(int minutes) {
		return  minutes * 60;
	}
	public static int fromHours(int hours) {
		return  hours * 3600;
	}
	public static int fromDays(int days) {
		return  days * 86400;
	}
}
