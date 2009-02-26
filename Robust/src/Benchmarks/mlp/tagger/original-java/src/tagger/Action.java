/**
 * Action class
 * Represents an action performed in response to a token
 * Instance of command pattern
 *
 * @author  Daniel Jackson
 * @version 0, 07/06/01
 */

package tagger;
import java.util.*;

public abstract class Action {
	/**
	* requires: iter is an iterator that just yielded this
	* ensures: performs action for token, and may remove itself from iter
	* default behaviour is equivalent to perform
	*/
	public void perform (Token token, Iterator iter) {
		perform (token);
		}

	public  void perform (Token token) {
		;
		}
	}


