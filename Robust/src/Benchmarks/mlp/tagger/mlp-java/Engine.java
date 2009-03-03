/**
 * Engine class
 * Maps token types to actions
 *
 * @author  Daniel Jackson
 * @version 0, 07/06/01
 */

//package tagger;
//import java.util.*;

public class Engine {
  /**
   * There are some very tricky concurrent modification issues with this class.
   * Can't execute a register or unregister method during an execution of consume_token
   * if the register or unregister affects the same list of actions associated with the token.
   * This means that during a consume_token for some type, can't register or unregister for
   * that type, or for the all types.
   * Note that a version of the perform method of action takes an iterator argument to
   * allow an action to remove itself.
   */
  
  // array of Action lists indexed on token type
  private LinkedList [] actions;

  // actions performed for all token types
  private LinkedList default_actions;

  public Engine () {
    actions = new LinkedList [Token.MAXTOKEN() + 1];
    for (int i = 0; i < actions.length; i++)
      actions[i] = disjoint llActions new LinkedList ();
    default_actions = /*disjoint llDefault*/ new LinkedList ();
  }

  public void register_by_type (Action action, int type) {
    register_by_type_front (action, type);
  }

  public void register_for_all (Action action) {
    default_actions.addFirst (action);
  }

  public void unregister_for_all (Action action) {
    default_actions.remove (action);
  }

  public void register_by_type_front (Action action, int type) {
    Assert.assert_ (type >= 0);
    Assert.assert_ (type <= Token.MAXTOKEN());
    actions[type].addFirst (action);
  }

  public void register_by_type_back (Action action, int type) {
    Assert.assert_ (type >= 0);
    Assert.assert_ (type <= Token.MAXTOKEN());
    actions[type].addLast (action);
  }

  public void unregister_by_type (Action action, int type) {
    Assert.assert_ (type >= 0);
    Assert.assert_ (type <= Token.MAXTOKEN());
    actions[type].remove (action);
  }

  public void consume_token (Token token) {
    perform_actions (default_actions, token);
    perform_actions (actions[token.type], token);
  }
  
  public static void perform_actions (LinkedList actions, Token token) {
    Iterator i = actions.iterator ();
    while (i.hasNext ()) {
      Action a = (Action) i.next ();
      a.perform (token, i);
    }
  }
}
