package com.adaptershack.jssl;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.IllegalFormatException;

public class Log {
	
	private static boolean quiet;
	
	private static PrintStream stdout = System.out;
	
	public static PrintStream getOut() {
		return stdout;
	}

	public static void setOut(PrintStream stdout) {
		Log.stdout = stdout;
	}

	public static boolean isQuiet() {
		return quiet;
	}

	public static void setQuiet(boolean _quiet) {
		quiet = _quiet;
	}	
	
	
	private static void println(String s) {
		if(enabled()) {
			if(stdout == null) {
				throw new IllegalStateException("Logging not initalized");
			}
			stdout.println(s);
		}
	}

	public static boolean enabled() {
		return !quiet;
	}
	
	public static void log(String s, Object... args ) {
		if(enabled()) {
			try {
				println(prefix() + 
						(args.length > 0 ?
						String.format(s,args) : s));
			} catch (IllegalFormatException e) {
				println(prefix() + s);
			}
		}
	}

	private static String prefix() {
		return "**** ";
	}
 	
	public static void hr() {
		println("------------------------------------------------------------");
	}
	
	public static void banner(String s) {
		hr();
		println(s);
		hr();
	}
	
	public static void logEach(String s, Object[] things) {
		log( s + Arrays.asList(things));
	}
	
	public static void startProgress(String s) {
		if(enabled()) {
			stdout.print(s);
			stdout.flush();
		}
	}
	
	public static void spin() {
		//if(System.console() != null) {
			stdout.print("|");
			stdout.flush();
		//}
	}
	
}
