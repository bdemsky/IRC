/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.interfaces;

/**
 *
 * @author navid
 */
public enum OffsetDependency {
    NO_ACCESS, NO_DEPENDENCY, WRITE_DEPENDENCY_1, WRITE_DEPENDENCY_2, READ_DEPENDENCY

}
