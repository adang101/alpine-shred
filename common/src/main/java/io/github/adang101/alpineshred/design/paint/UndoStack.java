package io.github.adang101.alpineshred.design.paint;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo and redo (section 22: 32 steps, one step per stroke). The caller pushes the state from
 * before each stroke; undoing hands back that state and keeps the current one for redo.
 */
public final class UndoStack<T> {
    public static final int DEFAULT_CAPACITY = 32;

    private final int capacity;
    private final Deque<T> undo = new ArrayDeque<>();
    private final Deque<T> redo = new ArrayDeque<>();

    public UndoStack() {
        this(DEFAULT_CAPACITY);
    }

    public UndoStack(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** Before a stroke: {@code before} becomes the step undo goes back to; redo is forgotten. */
    public void push(T before) {
        undo.push(before);
        while (undo.size() > capacity) undo.removeLast();
        redo.clear();
    }

    /** The state to go back to, with {@code current} kept for redo; null when there is nothing to undo. */
    public T undo(T current) {
        if (undo.isEmpty()) return null;
        redo.push(current);
        return undo.pop();
    }

    public T redo(T current) {
        if (redo.isEmpty()) return null;
        undo.push(current);
        return redo.pop();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public int size() {
        return undo.size();
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }
}
