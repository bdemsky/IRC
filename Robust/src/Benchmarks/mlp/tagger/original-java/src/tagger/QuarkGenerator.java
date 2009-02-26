/**
 * QuarkGenerator interface
 * Backend tagged text generator for QuarkXpress
 *
 * @author  Daniel Jackson
 * @version 0, 07/08/01
 */

package tagger;
import java.io.*;
import java.util.*;

public class QuarkGenerator implements Generator {
	PrintStream output_stream;
	Stack format_stack;
	private boolean on = true;

	public QuarkGenerator (PrintStream s) {
		output_stream = s;

		// stack holds strings used to terminate formats
		format_stack = new Stack ();
		}

	public void suppress_on () {
		on = false;
		}

	public void suppress_off () {
		on = true;
		}

	private void print (String s) {
		if (on) output_stream.print (s);
		}

	public void linefeed () {
		if (on) output_stream.println ();
		}

	// print "@style:"
	public void new_para (String style) {
		print ("@" + style + ":");
		}

	// print "<\n>"
	public void new_line () {
		print ("<\\n>");
		}

	public void special_char (String font, String index) {
		print ("<f\"" + font + "\"><\\#" + index + "><f$>");
		}

	public void special_char (String index) {
		print ("<\\#" + index + ">");
		}

	public void plaintext (String text) {
		print (text);
		}

	public void push_format (int format) {
		switch (format) {
			case Generator.ROMAN:
			case Generator.ITALICS:
				print ("<I>");
				format_stack.push ("<I>");
				return;
			case Generator.BOLD:
				print ("<B>");
				format_stack.push ("<B>");
				return;
			case Generator.SUBSCRIPT:
				print ("<->");
				format_stack.push ("<->");
				return;
			case Generator.SUPERSCRIPT:
				print ("<+>");
				format_stack.push ("<+>");
				return;
			default:
				Assert.unreachable ();
			}
		}

	public void pop_format () {
		// for now, handle too many pops without report
		if (format_stack.isEmpty ()) return;
		print ((String) format_stack.pop ());
		}
	}
