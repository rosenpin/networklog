/* (C) 2012 Pragmatic Software
   This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/
 */

/**
 * Implements an AA-tree.
 * @author Based upon implemention by Mark Allen Weiss
 */

// Utility class to pool Strings without expensive allocations

package com.googlecode.networklog;

public class CharArrayStringAATree
{
  /**
   * Construct the tree.
   */
  public CharArrayStringAATree( )
  {
    nullNode = new AANode( null, null, null );
    nullNode.left = nullNode.right = nullNode;
    nullNode.level = 0;
    root = nullNode;
  }

  public int size = 0;


  /**
   * Insert into the tree.
   * @param x the item to insert.
   * @return the item inserted or the value of the existing item
   */
  public String insert( CharArray x )
  {
    root = insert( x, root );
    return result;
  }



  /**
   * Make the tree logically empty.
   */
  public void clear( )
  {
    root = nullNode;
    size = 0;
  }

  /**
   * Internal method to insert into a subtree.
   * Sets {@link result} to the value of the existing or newly inserted object
   * @param x the item to insert.
   * @param t the node that roots the tree.
   * @return the new root.
   */
  private AANode insert( CharArray x, AANode t )
  {
    if( t == nullNode ) {
      size++;
      t = new AANode( x.toString(), nullNode, nullNode );
      result = t.element;
    } else {
      int compare = x.compareTo(t.element);
      if(compare < 0) {
        t.left = insert( x, t.left );
      } else if(compare > 0 ) {
        t.right = insert( x, t.right );
      } else {
        result = t.element;
        return t;
      }
    }
    t = skew( t );
    t = split( t );
    return t;
  }


  /**
   * Internal method to remove from a subtree.
   * @param x the item to remove.
   * @param t the node that roots the tree.
   * @return the new root.
   * @throws ItemNotFoundException if x is not found.
   */
  /**
   * Skew primitive for AA-trees.
   * @param t the node that roots the tree.
   * @return the new root after the rotation.
   */
  private static  AANode skew( AANode t )
  {
    if( t.left.level == t.level )
      t = rotateWithLeftChild( t );
    return t;
  }

  /**
   * Split primitive for AA-trees.
   * @param t the node that roots the tree.
   * @return the new root after the rotation.
   */
  private static  AANode split( AANode t )
  {
    if( t.right.right.level == t.level )
    {
      t = rotateWithRightChild( t );
      t.level++;
    }
    return t;
  }

  /**
   * Rotate binary tree node with left child.
   */
  private static  AANode rotateWithLeftChild( AANode k2 )
  {
    AANode k1 = k2.left;
    k2.left = k1.right;
    k1.right = k2;
    return k1;
  }

  /**
   * Rotate binary tree node with right child.
   */
  private static  AANode rotateWithRightChild( AANode k1 )
  {
    AANode k2 = k1.right;
    k1.right = k2.left;
    k2.left = k1;
    return k2;
  }

  private static class AANode
  {
    // Constructors
    AANode( String theElement, AANode lt, AANode rt )
    {
      element = theElement;
      left    = lt;
      right   = rt;
      level   = 1;
    }

    String element; // The data in the node
    AANode left;    // Left child
    AANode right;   // Right child
    int level;      // Level
  }

  private AANode root;
  private AANode nullNode;

  private String result;
}
