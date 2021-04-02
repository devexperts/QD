/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.model.market;

import com.dxfeed.model.IndexedEventModel;

import java.util.AbstractList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Filtered Sorted List implemented on Red-Black Tree.
 * Each node of the tree can be "checked". List contains only hose checked tree nodes.
 * CheckedTreeList exposes public List API for clients and Tree API for service providers.
 */
class CheckedTreeList<E> extends AbstractList<E> {
    private static final byte REMOVED = 0; // node that is not in tree
    private static final byte RED = 1;
    private static final byte BLACK = 2;

    public static class Node<E> extends IndexedEventModel.Entry<E> {
        // Note: field are property initialized in CheckedTreeList.insertNode method

        Node<E> left;
        Node<E> right;
        Node<E> parent;

        int childCount;
        boolean checked;
        byte color;

        Node() {}

        // It is used in tests only (in actual usage existing nodes are reinserted)
        Node(E value) {
            super(value);
        }

        int nodeCount() {
            return checked ? 1 : 0;
        }

        boolean isRemoved() {
            return color == REMOVED;
        }

        @Override
        public String toString() {
            if (left == this && right == this && parent == this && color == BLACK)
                return "nil";
            return getValue().toString();
        }
    }

    private final Comparator<? super E> comparator;

    // Represents the sentinel (terminator) node of the tree
    // Its color is BLACK and childCount is always 0
    private final Node<E> nil = new Node<>();

    {  // Init nil pointers to itself
        nil.parent = nil;
        nil.left = nil;
        nil.right = nil;
        nil.color = BLACK;
    }

    // =========================== instance fields ===========================
    // Tree root
    private Node<E> root = nil;

    // Number of checked nodes in the tree
    private int size = 0;

    // Total number of all nodes in the tree
    private int treeSize = 0;

    // Number of structural modifications to the tree
    private int modCount = 0;

    // =========================== constructor ===========================

    CheckedTreeList(Comparator<? super E> c) {
        this.comparator = c;
    }

    // =========================== methods ===========================

    // Public List API

    @Override
    public void clear() {
        modCount++;
        size = 0;
        treeSize = 0;
        root = nil;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public E get(int index) {
        if (index < 0 || index >= size())
            throw new IndexOutOfBoundsException("Index out of range: " + index);
        Node<E> node = getNodeByIndex(index);
        return node == nil ? null : node.getValue();
    }

    @Override
    public boolean contains(Object value) {
        return indexOf(value) >= 0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public int indexOf(Object value) {
        return getIndex((E) value);
    }

    @Override
    public Iterator<E> iterator() {
        return new CheckedListIterator();
    }

    // Public Tree API

    public Comparator<? super E> comparator() {
        return comparator;
    }

    public Iterator<E> treeIterator() {
        return new TreeIterator();
    }

    public int treeSize() {
        return treeSize;
    }

    public E find(E value) {
        Node<E> node = getNode(value);
        return node == null ? null : node.getValue();
    }

    // It is used in tests only (in actual usage existing nodes are reinserted)
    public void insert(E value) {
        Node<E> node = new Node<>(value);
        insertNode(node);
        check(node);
    }

    // It is used in tests only
    public E delete(E value) {
        Node<E> node = getNode(value);
        if (node == null)
            return null;
        E oldValue = node.getValue();
        deleteNode(node);
        return oldValue;
    }

    public boolean check(Node<E> node) {
        //assert (node == getNode(node.value));
        if (node.checked)
            return false;
        node.checked = true;
        incrementCount(node);
        return true;
    }

    public boolean uncheck(Node<E> node) {
        //assert (node == getNode(node.value));
        if (!node.checked)
            return false;
        node.checked = false;
        decrementCount(node);
        return true;
    }

    // Protected/Private Tree API

    /** Returns the tree node containing the specified value, or nil if not found. */
    protected Node<E> getNode(E value) {
        Node<E> node = root;
        while (node != nil) {
            int cmp = comparator.compare(value, node.getValue());
            if (cmp == 0) {
                return node;
            } else if (cmp < 0) {
                node = node.left;
            } else {
                node = node.right;
            }
        }
        return null;
    }

    // Note: New node is not checked in the tree right after insertion
    protected void insertNode(Node<E> newNode) {
        // Prepare existing node for insertion
        newNode.left = nil;
        newNode.right = nil;
        newNode.childCount = 0;
        newNode.checked = false;
        newNode.color = BLACK;
        // Start insertion from root
        Node<E> node = root;
        if (node == nil) {
            root = newNode;
            root.parent = nil; // no parent at root
            modCount++;
            treeSize++;
            return;
        }
        // Search value in tree
        E value = newNode.getValue();
        int cmp;
        Node<E> parent;
        do {
            parent = node;
            cmp = comparator.compare(value, node.getValue());
            if (cmp == 0) {
                throw new IllegalArgumentException("node for value " + value + " is already present " + node.getValue());
            } else if (cmp < 0) {
                node = node.left;
            } else {
                node = node.right;
            }
        } while (node != nil);
        // Node found -- insert new node
        node = newNode;
        assert node != nil;
        node.parent = parent;
        if (cmp < 0)
            parent.left = node;
        else
            parent.right = node;
        modCount++;
        treeSize++;
        rebalanceAfterInsertion(node);
    }

    protected void deleteNode(Node<E> p) {
        modCount++;
        treeSize--;

        // If strictly internal, copy successor's element to p and then make p point to successor.
        if (p.left != nil && p.right != nil) {
            Node<E> s = successorNode(p);

            if (p.checked) {
                decrementCount(s.checked ? s : p);
            } else if (s.checked) {
                incrementCount(p);
                decrementCount(s);
            } else {
                // both nodes unchecked - nothing to do
            }

            swapForDeletion(p, s);
        } else if (p.checked) {
            decrementCount(p);
        }

        // Start rebalance at replacement node, if it exists.
        Node<E> replacement = (p.left != nil) ? p.left : p.right;
        if (replacement != nil) {
            // Link replacement to parent
            assert replacement != nil;
            replacement.parent = p.parent;
            if (p.parent == nil)
                root = replacement;
            else if (p == p.parent.left)
                p.parent.left = replacement;
            else
                p.parent.right = replacement;

            // Null out links so they are OK to use by rebalanceAfterDeletion.
            p.left = p.right = p.parent = nil;

            // Fix replacement
            if (p.color == BLACK)
                rebalanceAfterDeletion(replacement);
        } else {
            if (p.parent == nil) { // return if we are the only node.
                root = nil;
            } else { //  No children. Use self as phantom replacement and unlink.
                if (p.color == BLACK)
                    rebalanceAfterDeletion(p);

                if (p.parent != nil) {
                    if (p == p.parent.left)
                        p.parent.left = nil;
                    else if (p == p.parent.right)
                        p.parent.right = nil;
                    assert p != nil;
                    p.parent = nil;
                }
            }
        }
        //assert (validateTree());

        // Mark node as removed and let GC do its job (not necessarily as it can be also reused now)
        p.left = null;
        p.right = null;
        p.parent = null;
        p.color = REMOVED;
    }

    /** Returns index for the specified node. */
    protected int getIndex(Node<E> node) {
        if (!node.checked)
            return -1;

        int index = node.left.childCount;
        while (node != root) {
            Node<E> parent = node.parent;
            if (parent.right == node)
                index += parent.left.childCount + parent.nodeCount();
            node = parent;
        }
        return index;
    }

    /** Returns node for the specified index, or nil if not found */
    private Node<E> getNodeByIndex(int index) {
        Node<E> node = root;
        while (node != nil) {
            int leftCount = node.left.childCount;

            if (index == leftCount && node.checked)
                return node;
            else if (index < leftCount)
                node = node.left;
            else {
                index -= leftCount + node.nodeCount();
                node = node.right;
            }
        }
        return nil;
    }

    /** Returns the index of the first occurrence of the specified value, or -1 if not found. */
    private int getIndex(E value) {
        int index = 0;
        Node<E> node = root;
        while (node != nil) {
            index += node.left.childCount;

            int cmp = comparator.compare(value, node.getValue());
            if (cmp == 0) {
                return (node.checked) ? index : -1;
            } else if (cmp < 0) {
                index -= node.left.childCount;
                node = node.left;
            } else {
                index += node.nodeCount();
                node = node.right;
            }
        }
        return -1;
    }

    private abstract class TreeListIterator implements Iterator<E> {
        int expectedModCount = CheckedTreeList.this.modCount;
        Node<E> lastReturned = nil;
        Node<E> next;

        TreeListIterator(Node<E> node) {
            next = node;
        }

        protected abstract Node<E> nextNode(Node<E> node);

        @Override
        public boolean hasNext() {
            return (next != nil);
        }

        @Override
        public E next() {
            if (next == nil)
                throw new NoSuchElementException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            lastReturned = next;
            next = nextNode(next);
            return lastReturned.getValue();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private class TreeIterator extends TreeListIterator {
        TreeIterator() {
            super(firstNode());
        }

        @Override
        protected Node<E> nextNode(Node<E> node) {
            return successorNode(node);
        }
    }

    private class CheckedListIterator extends TreeListIterator {
        CheckedListIterator() {
            super(getNodeByIndex(0));
        }

        @Override
        protected Node<E> nextNode(Node<E> node) {
            do {
                node = successorNode(node);
            } while (node != nil && !node.checked);
            return node;
        }
    }

    private Node<E> firstNode() {
        Node<E> node = root;
        if (node != nil) {
            while (node.left != nil)
                node = node.left;
        }
        return node;
    }

    private Node<E> successorNode(Node<E> node) {
        if (node == nil)
            return nil;
        else {
            if (node.right != nil) {
                Node<E> p = node.right;
                while (p.left != nil)
                    p = p.left;
                return p;
            } else {
                Node<E> p = node.parent;
                Node<E> child = node;
                while (p != nil && child == p.right) {
                    child = p;
                    p = p.parent;
                }
                return p;
            }
        }
    }

    private void incrementCount(Node<E> node) {
        modCount++;
        size++;
        while (node != nil) {
            node.childCount++;
            node = node.parent;
        }
    }

    private void decrementCount(Node<E> node) {
        modCount++;
        size--;
        while (node != nil) {
            node.childCount--;
            node = node.parent;
        }
    }

    /*
     *      p                     r
     *    /   \                 /   \
     *   X     r      ===>     p     Z
     *       /  \            /  \
     *      Y    Z          X    Y
     */
    private void rotateLeft(Node<E> p) {
        Node<E> r = p.right;
        assert r != nil;
        p.right = r.left;
        if (r.left != nil)
            r.left.parent = p;

        int rightCount = r.nodeCount() + r.right.childCount;
        int leftCount = p.nodeCount() + p.left.childCount;

        r.parent = p.parent;
        if (p.parent == nil)
            root = r;
        else if (p.parent.left == p)
            p.parent.left = r;
        else
            p.parent.right = r;

        r.left = p;
        p.parent = r;

        p.childCount -= rightCount;
        r.childCount += leftCount;
    }

    /*
     *      p                     r
     *    /   \                 /   \
     *   X     r     <===      p     Z
     *       /  \            /  \
     *      Y    Z          X    Y
     */
    private void rotateRight(Node<E> p) {
        Node<E> l = p.left;
        assert l != nil;
        p.left = l.right;
        if (l.right != nil)
            l.right.parent = p;

        int leftCount = l.nodeCount() + l.left.childCount;
        int rightCount = p.nodeCount() + p.right.childCount;

        l.parent = p.parent;
        if (p.parent == nil)
            root = l;
        else if (p.parent.right == p)
            p.parent.right = l;
        else p.parent.left = l;

        l.right = p;
        p.parent = l;

        p.childCount -= leftCount;
        l.childCount += rightCount;
    }

    private void rebalanceAfterInsertion(Node<E> x) {
        x.color = RED;
        while (x != nil && x != root && x.parent.color == RED) {
            if (x.parent == x.parent.parent.left) {
                Node<E> t = x.parent.parent.right;
                if (t.color == RED) {
                    x.parent.color = BLACK;
                    t.color = BLACK;
                    x.parent.parent.color = RED;
                    x = x.parent.parent;
                } else {
                    if (x == x.parent.right) {
                        x = x.parent;
                        rotateLeft(x);
                    }
                    x.parent.color = BLACK;
                    x.parent.parent.color = RED;
                    if (x.parent.parent != nil) {
                        rotateRight(x.parent.parent);
                    }
                }
            } else {
                Node<E> t = x.parent.parent.left;
                if (t.color == RED) {
                    x.parent.color = BLACK;
                    t.color = BLACK;
                    x.parent.parent.color = RED;
                    x = x.parent.parent;
                } else {
                    if (x == x.parent.left) {
                        x = x.parent;
                        rotateRight(x);
                    }
                    x.parent.color = BLACK;
                    x.parent.parent.color = RED;
                    if (x.parent.parent != nil) {
                        rotateLeft(x.parent.parent);
                    }
                }
            }
        }
        root.color = BLACK;
    }

    private void swapChild(Node<E> child, Node<E> oldParent, Node<E> newParent) {
        if (child != nil && child.parent == oldParent)
            child.parent = newParent;
    }

    private void swapParent(Node<E> parent, Node<E> oldChild, Node<E> newChild) {
        if (parent == nil) {
            if (root == oldChild)
                root = newChild;
            return;
        } else if (parent.left == oldChild)
            parent.left = newChild;
        else
            parent.right = newChild;
    }

    // Swap p and s nodes, where p is scheduled for deletion and s is successor of p
    private void swapForDeletion(Node<E> p, Node<E> s) {
        swapParent(p.parent, p, s);
        if (p.right != s)
            swapParent(s.parent, s, p);

        swapChild(s.left, s, p);
        swapChild(s.right, s, p);
        swapChild(p.left, p, s);
        if (p.right != s)
            swapChild(p.right, p, s);
        else {
            p.right = p;
            assert s != nil;
            s.parent = s;
        }

        Node<E> parent = s.parent;
        assert s != nil;
        s.parent = p.parent;
        assert p != nil;
        p.parent = parent;

        Node<E> left = s.left;
        s.left = p.left;
        p.left = left;

        Node<E> right = s.right;
        s.right = p.right;
        p.right = right;

        int childCount = s.childCount;
        s.childCount = p.childCount;
        p.childCount = childCount;

        byte color = s.color;
        s.color = p.color;
        p.color = color;
    }

    private void rebalanceAfterDeletion(Node<E> x) {
        while (x != root && x.color == BLACK) {
            if (x == x.parent.left) {
                Node<E> w = x.parent.right;

                // Case 1, w's color is red.
                if (w.color == RED) {
                    w.color = BLACK;
                    x.parent.color = RED;
                    rotateLeft(x.parent);
                    w = x.parent.right;
                }

                // Case 2, both of w's children are black
                if (w.left.color == BLACK && w.right.color == BLACK) {
                    w.color = RED;
                    x = x.parent;
                } else { // Case 3 / Case 4
                    // Case 3, w's right child is black
                    if (w.right.color == BLACK) {
                        w.left.color = BLACK;
                        w.color = RED;
                        rotateRight(w);
                        w = x.parent.right;
                    }
                    // Case 4, w = black, w.right = red
                    w.color = x.parent.color;
                    x.parent.color = BLACK;
                    w.right.color = BLACK;
                    rotateLeft(x.parent);
                    x = root;
                }
            } else { // symmetric
                Node<E> w = x.parent.left;

                if (w.color == RED) {
                    w.color = BLACK;
                    x.parent.color = RED;
                    rotateRight(x.parent);
                    w = x.parent.left;
                }

                if (w.right.color == BLACK && w.left.color == BLACK) {
                    w.color = RED;
                    x = x.parent;
                } else {
                    if (w.left.color == BLACK) {
                        w.right.color = BLACK;
                        w.color = RED;
                        rotateLeft(w);
                        w = x.parent.left;
                    }
                    w.color = x.parent.color;
                    x.parent.color = BLACK;
                    w.left.color = BLACK;
                    rotateRight(x.parent);
                    x = root;
                }
            }
        }
        x.color = BLACK;
    }

    // Self-validation utils

    boolean validateTree() {
        assert ((root != nil) ^ (treeSize == 0));
        assert (nil.parent == nil);
        assert (nil.left == nil);
        assert (nil.right == nil);
        assert (nil.childCount == 0);
        assert (!nil.checked);

        if (root != nil) {
            assert (root.parent == nil);
            assert (root.childCount == size);
            validateTree(root);
        }
        return true;
    }

    void validateTree(Node<E> node) {
        int childCount = node.nodeCount();
        if (node.left != nil) {
            assert (node.left.parent == node);
            assert (comparator.compare(node.left.getValue(), node.getValue()) < 0);
            childCount += node.left.childCount;
            validateTree(node.left);
        }
        if (node.right != nil) {
            assert (node.right.parent == node);
            assert (comparator.compare(node.getValue(), node.right.getValue()) < 0);
            childCount += node.right.childCount;
            validateTree(node.right);
        }
        assert (node.childCount >= 0);
        assert (node.childCount == childCount);
    }
}
