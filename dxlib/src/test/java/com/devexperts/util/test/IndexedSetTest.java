/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.util.test;

import com.devexperts.util.IndexedMap;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.Indexer;
import com.devexperts.util.IndexerFunction;
import com.devexperts.util.SynchronizedIndexedSet;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IndexedSetTest {
    private static final IndexerFunction<String, Integer> STRING_INTEGER_INDEXER =
        (IndexerFunction<String, Integer> & Serializable) Object::toString;
    private static final IndexerFunction.LongKey<String> LONG_STRING_INDEXER =
        (IndexerFunction.LongKey<String> & Serializable) Long::valueOf;
    private static final IndexerFunction.IdentityKey<String, String> STRING_IDENTITY_INDEXER =
        (IndexerFunction.IdentityKey<String, String> & Serializable) String::toString;

    @Test
    public void testIndexerFunction() {
        IndexedSet<Class<?>, Object> set = IndexedSet.create(Object::getClass);
        set.add("HABA");
        set.add(1);
        assertTrue(set.containsKey(String.class));
        assertTrue(set.containsKey(Integer.class));
    }

    @Test
    public void testLongIndexerFunction() {
        IndexedSet<Long, Long> set = IndexedSet.createLong(Long::longValue);
        set.add(1L);
        set.add(2L);
        assertTrue(set.containsKey(1L));
        assertTrue(set.containsKey(2L));
    }

    @Test
    public void testIntIndexerFunction() {
        IndexedSet<Integer, Integer> set = IndexedSet.createInt(Integer::intValue);
        set.add(1);
        set.add(2);
        assertTrue(set.containsKey(1));
        assertTrue(set.containsKey(2));
    }

    @Test
    public void testCollector() {
        doTestCollector(IndexedSet.class, IndexedSet.collector());
        doTestCollector(IndexedSet.class, IndexedSet.collector(Object::toString));
        doTestCollector(IndexedSet.class, IndexedSet.collectorInt(Object::hashCode));
        doTestCollector(SynchronizedIndexedSet.class, SynchronizedIndexedSet.collector());
        doTestCollector(SynchronizedIndexedSet.class, SynchronizedIndexedSet.collector(Object::toString));
        doTestCollector(SynchronizedIndexedSet.class, SynchronizedIndexedSet.collectorInt(Object::hashCode));
    }

    private void doTestCollector(Class<?> setClass, Collector<Object, ?, ? extends IndexedSet<?, Object>> collector) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < 100; i++)
            list.add(new Object());
        IndexedSet<?, Object> set = list.stream().collect(collector);
        assertSame(set.getClass(), setClass);
        assertEquals(new HashSet<>(list), new HashSet<>(set));
    }

    @Test
    public void testMapCollector() {
        doTestMapCollector(IndexedMap.class, IndexedMap.collector(), o -> o);
        doTestMapCollector(IndexedMap.class, IndexedMap.collector(Object::toString), Object::toString);
        doTestMapCollector(IndexedMap.class, IndexedMap.collectorInt(Object::hashCode), Object::hashCode);
    }

    private <K> void doTestMapCollector(Class<?> mapClass,
        Collector<Object, ?, ? extends IndexedMap<K, Object>> collector, Function<Object, K> keyFunction)
    {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            list.add(new Object());
        }
        IndexedMap<K, Object> map = list.stream().collect(collector);
        assertSame(map.getClass(), mapClass);
        assertEquals(list.stream().collect(Collectors.toMap(keyFunction, o -> o)), map);
    }

    @Test
    public void testBoxedLongs() {
        IndexedSet<Long, Long> set = new IndexedSet<>();
        set.add(1L);
        set.add(2L);
        assertTrue(set.containsKey(1L));
        assertTrue(set.containsKey(2L));
        assertTrue(set.containsValue(1L));
        assertTrue(set.containsValue(2L));
    }

    @Test
    public void testBoxedLongKeys() {
        IndexedSet<Long, Long[]> set = IndexedSet.createLong((Long[] value) -> value[0]);
        set.add(new Long[] {1L});
        set.add(new Long[] {2L});
        assertTrue(set.containsKey(1L));
        assertTrue(set.containsKey(2L));
        assertTrue(set.containsValue(new Long[]{1L}));
        assertTrue(set.containsValue(new Long[]{2L}));
    }

    @SuppressWarnings({"StringEquality"})
    @Test
    public void testIdentityFunction() {
        IndexedSet<String, String> set = IndexedSet.create((IndexerFunction.IdentityKey<String,String>) (s -> s));
        String s1 = "ONE";
        String s2 = new String(s1);
        assertTrue(s1 != s2);
        assertTrue(set.add(s1));
        assertTrue(set.add(s2));
        assertTrue(set.containsKey(s1));
        assertTrue(set.containsKey(s2));
        assertTrue(s1 == set.getByKey(s1));
        assertTrue(s2 == set.getByKey(s2));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testDefaultIndexer() {
        assertTrue("IndexerFunction.DEFAULT != null", IndexerFunction.DEFAULT != null);
        assertTrue("Indexer.DEFAULT != null", Indexer.DEFAULT != null);
        // Contract changed: [QD-1193] Deadlock: Indexer vs IndexerFunction initialization circular dependencies
        // assertTrue("Indexer.DEFAULT == IndexerFunction.DEFAULT", Indexer.DEFAULT == IndexerFunction.DEFAULT);
    }

    @Test
    public void testNull() {
        IndexedSet<Object, Object[]> set = IndexedSet.create((Object[] value) -> value[0]);

        assertFalse(set.containsKey(null));
        assertFalse(set.containsValue(new Object[] {null}));
        assertTrue(set.getByKey(null) == null);

        Object[] nul = {null};
        set.put(nul);

        assertTrue(set.size() == 1);
        assertTrue(set.keyIterator().next() == null);

        assertTrue(set.containsKey(null));
        assertTrue(set.containsValue(new Object[] {null}));
        assertTrue(set.getByKey(null) == nul);

        for (int i = 0; i < 1000; i++) { // trigger several rehashes in process
            String key = String.valueOf(i);
            Object[] value = {key};
            set.put(value);

            assertTrue(set.size() == i + 2);

            assertTrue(set.containsKey(key));
            assertTrue(set.containsValue(new Object[] {key}));
            assertTrue(set.getByKey(key) == value);

            assertTrue(set.containsKey(null));
            assertTrue(set.containsValue(new Object[] {null}));
            assertTrue(set.getByKey(null) == nul);
        }
    }

    @Test
    public void testConcurrentModification() {
        doTestConcurrentModification(IndexedSet.create(STRING_INTEGER_INDEXER));
        doTestConcurrentModification(SynchronizedIndexedSet.create(STRING_INTEGER_INDEXER));
    }

    private void doTestConcurrentModification(IndexedSet<String, Integer> is) {
        for (int i = 0; i < 100; i++) {
            // This cycle should never throw ConcurrentModificationException
            is.put(i);
            for (Integer v : is)
                // noinspection BoxingBoxedValue
                is.put(new Integer(v));
        }
    }

    @Test
    public void testPutIfAbsent() {
        doTestPutIfAbsent(IndexedSet.create(STRING_INTEGER_INDEXER));
        doTestPutIfAbsent(SynchronizedIndexedSet.create(STRING_INTEGER_INDEXER));
    }

    @SuppressWarnings({"UnnecessaryBoxing", "NumberEquality"})
    private void doTestPutIfAbsent(IndexedSet<String, Integer> is) {
        Integer v1 = new Integer(42);
        Integer v2 = new Integer(42);
        assertTrue(v1 != v2);

        Integer pv1 = is.putIfAbsentAndGet(v1);
        assertTrue(pv1 == v1);
        assertTrue(is.getByValue(v1) == v1);
        assertTrue(is.getByValue(v2) == v1);
        assertTrue(is.size() == 1);

        Integer pv2 = is.putIfAbsentAndGet(v2);
        assertTrue(pv2 == v1);
        assertTrue(is.getByValue(v1) == v1);
        assertTrue(is.getByValue(v2) == v1);
        assertTrue(is.size() == 1);
    }

    @Test
    public void testSimpleOps() {
        doTestSimpleOps(IndexedSet.create(STRING_INTEGER_INDEXER));
        doTestSimpleOps(SynchronizedIndexedSet.create(STRING_INTEGER_INDEXER));
    }

    private void doTestSimpleOps(IndexedSet<String, Integer> is) {
        assertFalse(is.containsKey("1"));
        assertFalse(is.containsKey("2"));
        assertFalse(is.containsValue(1));
        assertFalse(is.containsValue(2));

        assertTrue(is.add(1));
        assertTrue(is.add(2));
        assertTrue(is.containsKey("1"));
        assertTrue(is.containsKey("2"));
        assertTrue(is.containsValue(1));
        assertTrue(is.containsValue(2));
        assertFalse(is.add(1));
        assertFalse(is.add(2));

        assertEquals((Integer) 1, is.getByKey("1"));
        assertEquals((Integer) 2, is.getByKey("2"));
        assertEquals((Integer) 1, is.getByValue(1));
        assertEquals((Integer) 2, is.getByValue(2));

        assertTrue(checkSerial(is));

        Set<Integer> hs = new HashSet<>();
        assertFalse(hs.equals(is));
        assertFalse(is.equals(hs));
        hs.add(1);
        hs.add(2);
        assertTrue(hs.equals(is));
        assertTrue(is.equals(hs));
        assertEquals(is.hashCode(), hs.hashCode());

        IndexedMap<String, Integer> im = new IndexedMap<>(is, false);
        Map<String, Integer> hm = new HashMap<>();
        hm.put("1", 1);
        hm.put("2", 2);
        assertTrue(hm.equals(im));
        assertTrue(im.equals(hm));
        assertTrue(checkSerial(im));
        assertTrue(im.values().equals(hs));
        assertTrue(hs.equals(im.values()));
        assertTrue(im.values().equals(is));
        assertTrue(is.equals(im.values()));

        assertTrue(hm.keySet().equals(im.keySet()));
        assertTrue(im.keySet().equals(hm.keySet()));
    }

    @Test
    public void testBig() {
        doTestBig(IndexedSet.createLong(LONG_STRING_INDEXER));
        doTestBig(SynchronizedIndexedSet.createLong(LONG_STRING_INDEXER));
    }

    private void doTestBig(IndexedSet<Long, String> is) {
        int cnt = 10000;
        assertTrue(is.isEmpty());
        String[] va = new String[cnt];
        for (int k = 0; k < cnt; k++) {
            if (k == 100)
                is.ensureCapacity(600); // just test this method once...
            String v = String.valueOf(k);
            va[k] = v;
            assertFalse(is.containsKey(k));
            assertFalse(is.containsValue(v));
            assertTrue(is.add(v));
            assertTrue(is.containsKey(k));
            assertTrue(is.containsValue(v));
            assertEquals(k + 1, is.size());
            assertFalse(is.isEmpty());
        }

        String[] a = is.toArray(new String[is.size()]);
        assertEquals(cnt, a.length);
        assertTrue(Arrays.equals(scopy(a), scopy(va)));

        assertTrue(checkSerial(is));

        int cnt2 = cnt / 5;
        for (int k = 0; k < cnt - cnt2; k++) {
            String v = String.valueOf(k);
            assertTrue(is.containsKey(k));
            assertTrue(is.containsValue(v));
            switch (k % 4) {
            case 0:
                assertTrue(is.remove(v));
                break;
            case 1:
                assertEquals(v, is.removeValue(v));
                break;
            case 2:
                assertEquals(v, is.removeKey(k));
                break;
            case 3:
                assertEquals(v, is.removeKey((Long) (long) k));
                break;
            }
            assertFalse(is.containsKey(k));
            assertFalse(is.containsValue(v));
            assertEquals(cnt - k - 1, is.size());
            assertFalse(is.isEmpty());
        }

        assertTrue(checkSerial(is));

        a = is.toArray(new String[is.size()]);
        assertEquals(cnt2, a.length);
        assertTrue(Arrays.equals(scopy(a), scopy(va, cnt - cnt2, cnt2)));

        IndexedSet<Long, String> is2 = new IndexedSet<>(is);

        is.trimToSize();
        a = is.toArray(new String[is.size()]);
        assertEquals(cnt2, a.length);
        assertTrue(Arrays.equals(scopy(a), scopy(va, cnt - cnt2, cnt2)));

        is.clear();
        assertTrue(is.isEmpty());
        a = is.toArray(new String[is.size()]);
        assertEquals(0, a.length);

        // check is2 at the time it was captured
        a = is2.toArray(new String[is2.size()]);
        assertEquals(cnt2, a.length);
        assertTrue(Arrays.equals(scopy(a), scopy(va, cnt - cnt2, cnt2)));

        assertTrue(checkSerial(is2));

        // remove all by divisible by 5 entries from is2
        int cnt3 = 0;
        for (Iterator<Long> it = is2.keyIterator(); it.hasNext();) {
            if (it.next() % 5 != 0)
                it.remove();
            else
                cnt3++;
        }
        for (int i = 0; i < cnt; i++)
            assertEquals(i >= cnt - cnt2 && i % 5 == 0, is2.containsKey((Long) (long) i));
        assertEquals(cnt3, is2.size());

        assertTrue(checkSerial(is2));
    }

    // it's important that StrItem uses default (identity) equals
    static class StrItem {
        public String val;
        public StrItem(String val) { this.val = val; }
    }

    static final IndexerFunction<String, StrItem> STR_ITEM_INDEXER = t -> t.val;

    static final IndexerFunction<StrItem, StrItem> STR_ITEM_WEIRD_INDEXER = new IndexerFunction<StrItem, StrItem>() {
        @Override
        public StrItem getObjectKey(StrItem t) {
            return t;
        }

        @Override
        public int hashCodeByKey(StrItem key) {
            return (key == null || key.val == null) ? 0 : key.val.hashCode();
        }

        @Override
        public boolean matchesByKey(StrItem key, StrItem value) {
            return (key == null || key.val == null) ?
                getObjectKey(value).val == null :
                key.val.equals(getObjectKey(value).val);
        }
    };

    @Test
    public void testRemoveAllShortParam() {
        // removeAll with parameter collection shorter than set
        IndexedSet<String, StrItem> set = IndexedSet.create(STR_ITEM_INDEXER);
        set.add(new StrItem(null));
        set.add(new StrItem("a"));
        set.add(new StrItem("b"));
        set.add(new StrItem("c"));
        assertTrue(set.removeAll(Arrays.asList(new StrItem(null), new StrItem("a"), new StrItem("b"))));
        assertEquals(1, set.size());
        assertTrue(set.containsKey("c"));
    }

    @Test
    public void testRemoveAllLongParam() {
        // removeAll with parameter collection longer than set
        IndexedSet<String, StrItem> set = IndexedSet.create(STR_ITEM_INDEXER);
        set.add(new StrItem(null));
        set.add(new StrItem("a"));
        set.add(new StrItem("d"));
        assertTrue(set.removeAll(
            Arrays.asList(new StrItem(null), new StrItem("a"), new StrItem("b"), new StrItem("c"))));
        assertEquals(1, set.size());
        assertTrue(set.containsKey("d"));
    }

    @Test
    public void testRemoveAllLongSetParam() {
        // removeAll with long IndexedSet as parameter
        IndexedSet<String, StrItem> set = IndexedSet.create(STR_ITEM_INDEXER);
        set.add(new StrItem(null));
        set.add(new StrItem("a"));
        set.add(new StrItem("d"));
        List<StrItem> c = Arrays.asList(new StrItem(null), new StrItem("a"), new StrItem("b"), new StrItem("c"));
        assertTrue(set.removeAll(IndexedSet.create(STR_ITEM_INDEXER).withElements(c)));
        assertEquals(1, set.size());
        assertTrue(set.containsKey("d"));
    }

    @Test
    public void testRetainAll() {
        IndexedSet<String, StrItem> set = IndexedSet.create(STR_ITEM_INDEXER);
        set.add(new StrItem(null));
        set.add(new StrItem("a"));
        set.add(new StrItem("b"));
        set.add(new StrItem("c"));
        assertTrue(set.retainAll(Arrays.asList(new StrItem("b"), new StrItem("d"), new StrItem("e"))));
        assertEquals(1, set.size());
        assertTrue(set.containsKey("b"));
    }

    @Test
    public void testRetainAllWithSet() {
        IndexedSet<String, StrItem> set = IndexedSet.create(STR_ITEM_INDEXER);
        set.add(new StrItem(null));
        set.add(new StrItem("a"));
        set.add(new StrItem("b"));
        set.add(new StrItem("c"));
        List<StrItem> c = Arrays.asList(new StrItem("b"), new StrItem("d"), new StrItem("e"));
        assertTrue(set.retainAll(IndexedSet.create(STR_ITEM_INDEXER).withElements(c)));
        assertEquals(1, set.size());
        assertTrue(set.containsKey("b"));
    }

    @Test
    public void testEntrySetRemoveAllShortParam() {
        // removeAll with parameter collection shorter than set
        IndexedMap<String, StrItem> set = IndexedMap.create(STR_ITEM_INDEXER);
        set.put(new StrItem(null));
        set.put(new StrItem("a"));
        set.put(new StrItem("b"));
        set.put(new StrItem("c"));
        set.put(new StrItem("d"));
        Map<String, StrItem> itemMap =
            Stream.of(new StrItem(null), new StrItem("a"), new StrItem("b"), new StrItem("c"))
                .collect(Collectors.toMap(STR_ITEM_INDEXER::getObjectKey, i -> i));
        assertTrue(set.entrySet().removeAll(itemMap.entrySet()));
        assertEquals(1, set.size());
        assertTrue(set.containsKey("d"));
    }

    @Test
    public void testEntrySetRemoveAllLongParam() {
        // removeAll with parameter collection longer than set
        IndexedMap<String, StrItem> set = IndexedMap.create(STR_ITEM_INDEXER);
        set.put(new StrItem(null));
        set.put(new StrItem("a"));
        set.put(new StrItem("d"));
        Map<String, StrItem> itemMap =
            Stream.of(new StrItem(null), new StrItem("a"), new StrItem("b"), new StrItem("c"))
                .collect(Collectors.toMap(STR_ITEM_INDEXER::getObjectKey, i -> i));
        assertTrue(set.entrySet().removeAll(itemMap.entrySet()));
        assertEquals(1, set.size());
        assertTrue(set.containsKey("d"));
    }

    @Test
    public void testEntrySetRetainAll() {
        IndexedMap<String, StrItem> set = IndexedMap.create(STR_ITEM_INDEXER);
        set.put(new StrItem(null));
        set.put(new StrItem("a"));
        set.put(new StrItem("b"));
        set.put(new StrItem("c"));
        Map<String, StrItem> itemMap =
            Stream.of(new StrItem("b"), new StrItem("d"), new StrItem("e"))
                .collect(Collectors.toMap(STR_ITEM_INDEXER::getObjectKey, i -> i));
        assertTrue(set.entrySet().retainAll(itemMap.entrySet()));
        assertEquals(1, set.size());
        assertTrue(set.containsKey("b"));
    }

    @Test
    public void testEntrySetRemoveIf() {
        IndexedMap<String, StrItem> set = IndexedMap.create(STR_ITEM_INDEXER);
        set.put(new StrItem(null));
        set.put(new StrItem("a"));
        set.put(new StrItem("b"));
        set.put(new StrItem("c"));
        assertTrue(set.entrySet().removeIf(entry -> !("b".equals(entry.getKey()))));
        assertEquals(1, set.size());
        assertTrue(set.containsKey("b"));
    }

    @Test
    public void testKeySetRemoveAllShortParam() {
        // removeAll with parameter collection shorter than set
        IndexedMap<StrItem, StrItem> set = IndexedMap.create(STR_ITEM_WEIRD_INDEXER);
        set.put(new StrItem(null));
        set.put(new StrItem("a"));
        set.put(new StrItem("b"));
        set.put(new StrItem("c"));
        set.put(new StrItem("d"));
        assertTrue(set.keySet().removeAll(
            Arrays.asList(new StrItem(null), new StrItem("a"), new StrItem("b"), new StrItem("c"))));
        assertEquals(1, set.size());
        assertTrue(set.containsKey(new StrItem("d")));
    }

    @Test
    public void testKeySetRemoveAllLongParam() {
        // removeAll with parameter collection longer than set
        IndexedMap<StrItem, StrItem> set = IndexedMap.create(STR_ITEM_WEIRD_INDEXER);
        set.put(new StrItem(null));
        set.put(new StrItem("a"));
        set.put(new StrItem("d"));
        assertTrue(set.keySet().removeAll(
            Arrays.asList(new StrItem(null), new StrItem("a"), new StrItem("b"), new StrItem("c"))));
        assertEquals(1, set.size());
        assertTrue(set.containsKey(new StrItem("d")));
    }

    @Test
    public void testKeySetRetainAll() {
        IndexedMap<StrItem, StrItem> set = IndexedMap.create(STR_ITEM_WEIRD_INDEXER);
        set.put(new StrItem(null));
        set.put(new StrItem("a"));
        set.put(new StrItem("b"));
        set.put(new StrItem("c"));
        assertTrue(set.keySet().retainAll(Arrays.asList(new StrItem("b"), new StrItem("d"), new StrItem("e"))));
        assertEquals(1, set.size());
        assertTrue(set.containsKey(new StrItem("b")));
    }

    @Test
    public void testKeySetRemoveIf() {
        IndexedMap<StrItem, StrItem> set = IndexedMap.create(STR_ITEM_WEIRD_INDEXER);
        set.put(new StrItem(null));
        set.put(new StrItem("a"));
        set.put(new StrItem("b"));
        set.put(new StrItem("c"));
        assertTrue(set.keySet().removeIf(key -> key == null || !("b".equals(key.val))));
        assertEquals(1, set.size());
        assertTrue(set.containsKey(new StrItem("b")));
    }

    @Test
    public void testIdentitySet() {
        doTestIdentitySet(IndexedSet.create(STRING_IDENTITY_INDEXER));
        doTestIdentitySet(SynchronizedIndexedSet.create(STRING_IDENTITY_INDEXER));
    }

    @SuppressWarnings({"StringEquality"})
    private void doTestIdentitySet(IndexedSet<String, String> is) {
        String s1 = new String("HABA");
        String s2 = new String("HABA");
        assertTrue(is.add(s1));
        assertTrue(is.add(s2));
        assertEquals(2, is.size());
        assertTrue(s1 == is.getByKey(s1));
        assertTrue(s2 == is.getByKey(s2));
        assertTrue(s1 == is.getByValue(s1));
        assertTrue(s2 == is.getByValue(s2));

        // identity will be lost after serialization
        assertFalse(checkSerial(is));
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    private boolean checkSerial(Object o) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(o);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            Object p = ois.readObject();
            boolean eq1 = p.equals(o);
            boolean eq2 = o.equals(p);
            assertTrue(eq1 == eq2);
            return eq1;
        } catch (Exception e) {
            fail(e.toString());
            return false; // should never execute
        }
    }

    private String[] scopy(String[] a) {
        return scopy(a, 0, a.length);
    }

    private String[] scopy(String[] a, int ofs, int len) {
        String[] r = new String[len];
        System.arraycopy(a, ofs, r, 0, len);
        Arrays.sort(r);
        return r;
    }
}
