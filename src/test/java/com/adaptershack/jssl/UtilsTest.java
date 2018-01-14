package com.adaptershack.jssl;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UtilsTest {
	
	@Test
	public void testUnescapeJava() {

		char escaped[] = {
			'\\','0','0','1',
			'\\','u','0','0','0','1',
			'\\','b',
			'\\','f',
			'\\','n',
			'\\','r',
			'\\','t',
			'\\','\\',
			'\\','"',
			'\\','\''
		};
		
		char unescaped[] = {
				'\001',
				'\u0001',
				'\b',
				'\f',
				'\n',
				'\r',
				'\t',
				'\\',
				'"',
				'\''
			};
		
		assertEquals(
				new String(unescaped),
				Utils.unescapeJavaString(new String(escaped)));
		
		
	}

}
