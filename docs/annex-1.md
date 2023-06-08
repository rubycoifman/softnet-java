---
layout: default
title: Annex 1
nav_order: 100
---

## Annex 1

Most of the data types provided by Softnet (Java) library are described in the guide. However, some types are not covered and described in this annex.  

* ### <span class="datatype">MemorySize</span>  

This class contains a set of static methods that take a memory size represented as a combination of different units of memory and return the memory size in bytes as a 32-bit integer value. It has the following members:

```java
public class MemorySize {
	public static int fromK(int kilobytes)
	public static int fromM(int megabytes)
	public static long fromG(int gigabytes)
	public static int fromMK(int megabytes, int kilobytes)
	public static long fromMK_L(int megabytes, int kilobytes)
	public static int fromGMK(int gigabytes, int megabytes, int kilobytes)
	public static long fromGMK_L(int gigabytes, int megabytes, int kilobytes)
}
```

The name of each method describes the method itself, so the method descriptions are not provided. To prevent the result from overflowing a 32-bit integer, call the L-version of the method. For example, <span class="method">fromGMK_L</span> returns the value of type long.  

**Use case 1**. Prints the number of bytes in 37 kilobytes:
```java
System.out.println(MemorySize.fromK(37));
```
**Use case 2**. Prints the number of bytes in 8 gigabytes:
```java
System.out.println(MemorySize.fromG(8));
```

* ### <span class="datatype">TimeSpan</span>  

This class contains a set of static methods that take a time span represented as a combination of different units of time and return the time span in seconds as a 32-bit integer value. It is convenient to use in event definitions to specify how long the event broker will keep the event. It has the following members:

```java
public class TimeSpan {
	public static int fromMS(int minutes, int seconds)
	public static int fromHMS(int hours, int minutes, int seconds)
	public static int fromDHMS(int days, int hours, int minutes, int seconds)
	public static int fromMinutes(int minutes)
	public static int fromHours(int hours)
	public static int fromDays(int days)
}
```
The name of each method describes the method itself, so the method descriptions are not provided.  

**Use case 1**. Prints the period in seconds specified as 2 hours and 30 minutes:
```java
System.out.println(TimeSpan.fromHMS(2,30,0).seconds);
```

**Use case 2**. Prints the period in seconds specified as 2 days:
```java
System.out.println(TimeSpan.fromDays(2).seconds);
```
