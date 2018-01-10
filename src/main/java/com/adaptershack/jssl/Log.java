package com.adaptershack.jssl;

import java.io.PrintStream;
import java.util.Arrays;

public class Log {
	
	private static boolean quiet;
	
	private static PrintStream stdout = System.out;
	
	public static boolean isQuiet() {
		return quiet;
	}

	public static void setQuiet(boolean _quiet) {
		quiet = _quiet;
	}	
	
	
	private static void println(String s) {
		if(!quiet) {
			stdout.println(s);
		}
	}
	
	public static void log(String s) {
		println("**** " + s);
	}
 	
	public static void hr() {
		println("------------------------------------------------------------");
	}
	
	public static void banner(String s) {
		hr();
		println(s);
		hr();
	}
	
	public static void log(String s, Object[] things) {
		log( s + Arrays.asList(things));
	}
	
}
