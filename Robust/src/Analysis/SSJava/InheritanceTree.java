package Analysis.SSJava;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InheritanceTree<T> {

  private Node<T> root;
  private Map<T, Node<T>> nodeMap;

  public InheritanceTree(T rootData) {
    root = new Node<T>(rootData);
    root.children = new ArrayList<Node<T>>();
    nodeMap = new HashMap<T, Node<T>>();
    nodeMap.put(rootData, root);
  }

  public void addParentChild(T parent, T child) {
    Node<T> parentNode = getTreeNode(parent);
    Node<T> childNode = getTreeNode(child);
    parentNode.children.add(childNode);
    System.out.println("childNode=" + childNode);
    childNode.parent = parentNode;
  }

  public Node<T> getTreeNode(T in) {
    if (!nodeMap.containsKey(in)) {
      Node<T> node = new Node(in);
      nodeMap.put(in, node);
    }
    return nodeMap.get(in);
  }

  public Node<T> getRootNode() {
    return root;
  }

}

class Node<T> {
  public T data;
  public Node<T> parent;
  public List<Node<T>> children;

  public Node(T in) {
    this.data = in;
    children = new ArrayList<Node<T>>();
  }

  public Node<T> getParent() {
    return parent;
  }

  public T getData() {
    return data;
  }

  public List<Node<T>> getChildren() {
    return children;
  }
}