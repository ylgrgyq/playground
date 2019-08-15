package com.github.ylgrgyq.resender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public final class DefaultBackupQueue<E extends PayloadCarrier> implements BackupQueue<E> {
    private final BlockingDeque<E> queue;
    private final BackupStorage<E> storage;
    private final PersistentBackupQueueFence persistentFence;

    public DefaultBackupQueue(BackupStorage<E> storage, PersistentBackupQueueFence persistentFence) {
        requireNonNull(storage, "storage");
        requireNonNull(persistentFence, "persistentFence");

        this.storage = storage;
        this.persistentFence = persistentFence;
        this.queue = new LinkedBlockingDeque<>();

        Collection<? extends E> snapshot = storage.read();
        if (snapshot != null) {
            snapshot.forEach(this.queue::offer);
        }
    }

    @Override
    public void persistent() {
        persistent(false);
    }

    @Override
    public void persistent(boolean force) {
        if (force || persistentFence.allowPersistent(this)) {
            ArrayList<E> snapshot = new ArrayList<>(queue);
            synchronized (this) {
                storage.store(Collections.unmodifiableList(snapshot));
            }
            persistentFence.updateFence(this);
        }
    }

    @Override
    public void addFirst(E element) {
        requireNonNull(element, "element");
        queue.addFirst(element);
    }

    @Override
    public void addLast(E element) {
        requireNonNull(element, "element");
        queue.addLast(element);
    }

    @Override
    public boolean offerFirst(E element) {
        requireNonNull(element, "element");
        return queue.offerFirst(element);
    }

    @Override
    public boolean offerLast(E element) {
        requireNonNull(element, "element");
        return queue.offerLast(element);
    }

    @Override
    public void putFirst(E element) throws InterruptedException {
        requireNonNull(element, "element");
        queue.putFirst(element);
    }

    @Override
    public void putLast(E element) throws InterruptedException {
        requireNonNull(element, "element");
        queue.putLast(element);
    }

    @Override
    public boolean offerFirst(E element, long timeout, TimeUnit unit) throws InterruptedException {

        requireNonNull(element, "element");
        requireNonNull(unit, "unit");
        return queue.offerFirst(element, timeout, unit);
    }

    @Override
    public boolean offerLast(E element, long timeout, TimeUnit unit) throws InterruptedException {
        requireNonNull(element, "element");
        requireNonNull(unit, "unit");
        return queue.offerLast(element, timeout, unit);
    }

    @Override
    public E takeFirst() throws InterruptedException {
        return queue.takeFirst();
    }

    @Override
    public E takeLast() throws InterruptedException {
        return queue.takeLast();
    }

    @Override
    public E pollFirst(long timeout, TimeUnit unit) throws InterruptedException {
        requireNonNull(unit, "unit");
        return queue.pollFirst(timeout, unit);
    }

    @Override
    public E pollLast(long timeout, TimeUnit unit) throws InterruptedException {
        requireNonNull(unit, "unit");
        return queue.pollLast(timeout, unit);
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        requireNonNull(o, "o");
        return queue.removeFirstOccurrence(o);
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        requireNonNull(o, "o");
        return queue.removeLastOccurrence(o);
    }

    @Override
    public boolean add(E element) {
        requireNonNull(element, "element");
        return queue.add(element);
    }

    @Override
    public boolean offer(E element) {
        requireNonNull(element, "element");
        return queue.offer(element);
    }

    @Override
    public void put(E element) throws InterruptedException {
        requireNonNull(element, "element");
        queue.put(element);
    }

    @Override
    public boolean offer(E element, long timeout, TimeUnit unit) throws InterruptedException {
        requireNonNull(element, "element");
        requireNonNull(unit, "unit");
        return queue.offer(element, timeout, unit);
    }

    @Override
    public E remove() {
        return queue.remove();
    }

    @Override
    public E poll() {
        return queue.poll();
    }

    @Override
    public E take() throws InterruptedException {
        return queue.take();
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        requireNonNull(unit, "unit");
        return queue.poll(timeout, unit);
    }

    @Override
    public E element() {
        return queue.element();
    }

    @Override
    public E peek() {
        return queue.peek();
    }

    @Override
    public boolean remove(Object o) {
        requireNonNull(o, "o");
        return queue.remove(o);
    }

    @Override
    public boolean contains(Object o) {
        requireNonNull(o, "o");
        return queue.contains(o);
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public Iterator<E> iterator() {
        return queue.iterator();
    }

    @Override
    public void push(E element) {
        requireNonNull(element, "element");
        queue.push(element);
    }

    @Override
    public E removeFirst() {
        return queue.removeFirst();
    }

    @Override
    public E removeLast() {
        return queue.removeLast();
    }

    @Override
    public E pollFirst() {
        return queue.pollFirst();
    }

    @Override
    public E pollLast() {
        return queue.pollLast();
    }

    @Override
    public E getFirst() {
        return queue.getFirst();
    }

    @Override
    public E getLast() {
        return queue.getLast();
    }

    @Override
    public E peekFirst() {
        return queue.peekFirst();
    }

    @Override
    public E peekLast() {
        return queue.peekLast();
    }

    @Override
    public E pop() {
        return queue.pop();
    }

    @Override
    public Iterator<E> descendingIterator() {
        return queue.descendingIterator();
    }

    @Override
    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        requireNonNull(c, "c");
        return queue.drainTo(c);
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        requireNonNull(c, "c");
        return queue.drainTo(c, maxElements);
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public Object[] toArray() {
        return queue.toArray();
    }

    @Override
    public <T> T[] toArray(T[] array) {
        requireNonNull(array, "a");
        return queue.toArray(array);
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
        requireNonNull(collection, "collection");
        return queue.containsAll(collection);
    }

    @Override
    public boolean addAll(Collection<? extends E> collection) {
        requireNonNull(collection, "collection");
        return queue.addAll(collection);
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
        requireNonNull(collection, "collection");
        return queue.removeAll(collection);
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
        requireNonNull(collection, "collection");
        return queue.retainAll(collection);
    }

    @Override
    public void clear() {
        queue.clear();
    }
}
