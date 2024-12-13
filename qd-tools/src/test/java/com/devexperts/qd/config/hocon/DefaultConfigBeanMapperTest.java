/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.config.hocon;

import com.devexperts.util.TimePeriod;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class DefaultConfigBeanMapperTest {

    HoconConfigProvider configProvider;

    @Before
    public void setUp() {
        configProvider = new HoconConfigProvider();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testSimpleProps() {

        SimpleBean expected =
            new SimpleBean(true, 2, 3.0, 4, "STRING", Duration.ofSeconds(10), ConfigMemorySize.ofBytes(10 * 1024),
                TimePeriod.valueOf("20s"));
        expected.setConfig(ConfigFactory.parseString("{ a: 5}"));
        expected.setConfigList(ConfigValueFactory.fromIterable(Arrays.asList(1, 2, 3)));
        expected.setConfigValue(ConfigValueFactory.fromAnyRef("ConfigString"));
        expected.setConfigObject(ConfigValueFactory.fromMap(Collections.singletonMap("key", "value")));

        String cfg = "{bool=true, i=2, d=3.0, l=4, s=STRING, duration=10s, memorySize=10k, timePeriod=20s," +
            "config:{a=5}, configList:[1, 2, 3], configValue:ConfigString, configObject:{key=value} }";

        Config config = ConfigFactory.parseString(cfg);
        SimpleBean bean = configProvider.getConfigBean(config, SimpleBean.class);
        assertEquals(expected, bean);

    }

    @Test
    public void testListProps() {
        ListsBean expected = new ListsBean();
        expected.setIntegers(Arrays.asList(1, 2, 3));
        expected.setStrings(Arrays.asList("A", "B", "C"));
        expected.setDurations(Arrays.asList(Duration.ofSeconds(10), Duration.ofSeconds(20), Duration.ofSeconds(60)));
        expected.setPeriods(Arrays.asList(TimePeriod.valueOf("1s"), TimePeriod.valueOf("2m"), TimePeriod.valueOf("3h")));
        expected.setSimpleBeans(Arrays.asList(new SimpleBean("A"), new SimpleBean("B"), new SimpleBean("C")));
        expected.setConfigLists(Arrays.asList(
            ConfigValueFactory.fromIterable(Arrays.asList(1, 2)),
            ConfigValueFactory.fromIterable(Arrays.asList(3, 4)),
            ConfigValueFactory.fromIterable(Arrays.asList(5, 6))
        ));

        String cfg = "{\n" +
            "integers:[1,2,3],\n" +
            "strings:[A,B,C],\n" +
            "durations:[10s,20s,1m],\n" +
            "periods:[1s,2m,3h],\n" +
            "simpleBeans:[{s=A},{s=B},{s=C}],\n" +
            "configLists:[[1, 2], [3, 4], [5, 6]]\n" +
            "}";
        Config config = ConfigFactory.parseString(cfg);
        ListsBean bean = configProvider.getConfigBean(config, ListsBean.class);
        assertEquals(expected, bean);
    }

    @Test
    public void testMapProps() {
        MapsBean expected = new MapsBean();
        expected.setIntMap(mapOf("A", 1, "B", 2, "C", 3));
        expected.setStringMap(mapOf("A", "X", "B", "Y", "C", "Z"));
        expected.setBeanMap(mapOf(
            "X", new SimpleBean("A"),
            "Y", new SimpleBean("B"),
            "Z", new SimpleBean("C"))
        );

        String cfg = "{intMap:{A=1,B=2,C=3}, stringMap:{A=X,B=Y,C=Z}, beanMap:{X:{s=A},Y:{s=B},Z:{s=C}}}";
        Config config = ConfigFactory.parseString(cfg);
        MapsBean bean = configProvider.getConfigBean(config, MapsBean.class);
        assertEquals(expected, bean);
    }

    @Test
    public void testValueOfSimple() {
        SimpleBean expected = new SimpleBean();
        expected.setValueOfBean(ValueOfBean.valueOf("simple"));
        String cfg = "{valueOfBean = simple}";
        Config config = ConfigFactory.parseString(cfg);
        SimpleBean bean = configProvider.getConfigBean(config, SimpleBean.class);
        assertEquals(expected, bean);
    }

    @Test
    public void testValueOfGeneral() {
        SimpleBean expected = new SimpleBean();
        expected.setValueOfBean(new ValueOfBean("general", "opt"));
        String cfg = "{valueOfBean: { name = general, option = opt }}";
        Config config = ConfigFactory.parseString(cfg);
        SimpleBean bean = configProvider.getConfigBean(config, SimpleBean.class);
        assertEquals(expected, bean);
    }

    @Test
    public void testValueOfList() {
        ListsBean expected = new ListsBean();
        expected.setValueOfBeans(Arrays.asList(
            new ValueOfBean("n1", "opt1"),
            new ValueOfBean("n2"),
            new ValueOfBean("n3", "opt3")
        ));

        String cfg = "{valueOfBeans:[\n" +
            "{name = n1, option = opt1},\n" +
            "n2,\n" +
            "{name = n3, option = opt3},\n" +
            "]}";
        Config config = ConfigFactory.parseString(cfg);
        ListsBean bean = configProvider.getConfigBean(config, ListsBean.class);
        assertEquals(expected, bean);
    }

    @Test
    public void testValueOfMap() {
        MapsBean expected = new MapsBean();
        expected.setValueOfBeanMap(mapOf(
            "A", new ValueOfBean("X", "opt1"),
            "B", new ValueOfBean("Y"),
            "C", new ValueOfBean("Z", "opt3"))
        );

        String cfg = "{valueOfBeanMap:{\n" +
            "A: { name = X, option = opt1 }\n" +
            "B: Y\n" +
            "C: { name = Z, option = opt3 }\n" +
            "}}";
        Config config = ConfigFactory.parseString(cfg);
        MapsBean bean = configProvider.getConfigBean(config, MapsBean.class);
        assertEquals(expected, bean);
    }

    static <K, V> Map<K, V> mapOf(Object... keyValues) {
        assert keyValues.length % 2 == 0;
        Map<K, V> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            //noinspection unchecked
            map.put((K) keyValues[i], (V) keyValues[i + 1]);
        }
        return map;
    }

    public static class SimpleBean {

        // FIXME: Real config beans will not use @com.typesafe.config.Optional but @Required
        @Optional private boolean bool;
        @Optional private int i;
        @Optional private double d;
        @Optional private long l;
        @Optional private String s;
        @Optional private Duration duration;
        @Optional private ConfigMemorySize memorySize;
        @Optional private TimePeriod timePeriod;

        @Optional private Object object;
        @Optional private Config config;
        @Optional private ConfigList configList;
        @Optional private ConfigValue configValue;
        @Optional private ConfigObject configObject;

        @Optional private ValueOfBean valueOfBean;

        @SuppressWarnings("unused")
        public SimpleBean() {}

        public SimpleBean(String s) {
            this.s = s;
        }

        public SimpleBean(boolean bool, int i, double d, long l, String s, Duration duration,
            ConfigMemorySize memorySize, TimePeriod timePeriod)
        {
            this.bool = bool;
            this.i = i;
            this.d = d;
            this.l = l;
            this.s = s;
            this.duration = duration;
            this.memorySize = memorySize;
            this.timePeriod = timePeriod;
        }

        public Object getObject() {
            return object;
        }

        public void setObject(Object object) {
            this.object = object;
        }

        public Config getConfig() {
            return config;
        }

        public void setConfig(Config config) {
            this.config = config;
        }

        public ConfigList getConfigList() {
            return configList;
        }

        public void setConfigList(ConfigList configList) {
            this.configList = configList;
        }

        public ConfigValue getConfigValue() {
            return configValue;
        }

        public void setConfigValue(ConfigValue configValue) {
            this.configValue = configValue;
        }

        public ConfigObject getConfigObject() {
            return configObject;
        }

        public void setConfigObject(ConfigObject configObject) {
            this.configObject = configObject;
        }

        public boolean isBool() {
            return bool;
        }

        public void setBool(boolean bool) {
            this.bool = bool;
        }

        public int getI() {
            return i;
        }

        public void setI(int i) { this.i = i; }

        public double getD() {
            return d;
        }

        public void setD(double d) { this.d = d; }

        public long getL() {
            return l;
        }

        public void setL(long l) { this.l = l; }

        public String getS() {
            return s;
        }

        public void setS(String s) { this.s = s; }

        public Duration getDuration() {
            return duration;
        }

        public void setDuration(Duration duration) { this.duration = duration; }

        public ConfigMemorySize getMemorySize() {
            return memorySize;
        }

        public void setMemorySize(ConfigMemorySize memorySize) { this.memorySize = memorySize; }

        public TimePeriod getTimePeriod() {
            return timePeriod;
        }

        public void setTimePeriod(TimePeriod timePeriod) { this.timePeriod = timePeriod; }

        public ValueOfBean getValueOfBean() {
            return valueOfBean;
        }

        public void setValueOfBean(ValueOfBean valueOfBean) {
            this.valueOfBean = valueOfBean;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            SimpleBean that = (SimpleBean) o;
            return bool == that.bool &&
                i == that.i &&
                Double.compare(that.d, d) == 0 &&
                l == that.l &&
                Objects.equals(s, that.s) &&
                Objects.equals(duration, that.duration) &&
                Objects.equals(memorySize, that.memorySize) &&
                Objects.equals(timePeriod, that.timePeriod) &&
                Objects.equals(object, that.object) &&
                Objects.equals(config, that.config) &&
                Objects.equals(configList, that.configList) &&
                Objects.equals(configValue, that.configValue) &&
                Objects.equals(configObject, that.configObject) &&
                Objects.equals(valueOfBean, that.valueOfBean);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bool, i, d, l, s, duration, memorySize, timePeriod,
                object, config, configList, configValue, configObject, valueOfBean);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("SimpleBean{");
            sb.append("bool=").append(bool);
            sb.append(", i=").append(i);
            sb.append(", d=").append(d);
            sb.append(", l=").append(l);
            sb.append(", s='").append(s).append('\'');
            sb.append(", duration=").append(duration);
            sb.append(", memorySize=").append(memorySize);
            sb.append(", timePeriod=").append(timePeriod);
            sb.append(", object=").append(object);
            sb.append(", config=").append(config);
            sb.append(", configList=").append(configList);
            sb.append(", configValue=").append(configValue);
            sb.append(", configObject=").append(configObject);
            sb.append(", valueOfBean=").append(valueOfBean);
            sb.append('}');
            return sb.toString();
        }

    }

    public static class ListsBean {
        @Optional List<Integer> integers;
        @Optional List<String> strings;
        @Optional List<Duration> durations;
        @Optional List<TimePeriod> periods;
        @Optional List<SimpleBean> simpleBeans;
        @Optional List<ConfigList> configLists;
        @Optional List<ValueOfBean> valueOfBeans;

        public List<TimePeriod> getPeriods() {
            return periods;
        }

        public void setPeriods(List<TimePeriod> periods) {
            this.periods = periods;
        }

        public List<ConfigList> getConfigLists() {
            return configLists;
        }

        public void setConfigLists(List<ConfigList> configLists) {
            this.configLists = configLists;
        }

        public List<Integer> getIntegers() {
            return integers;
        }

        public void setIntegers(List<Integer> integers) {
            this.integers = integers;
        }

        public List<ValueOfBean> getValueOfBeans() {
            return valueOfBeans;
        }

        public void setValueOfBeans(
            List<ValueOfBean> valueOfBeans)
        {
            this.valueOfBeans = valueOfBeans;
        }

        public List<String> getStrings() {
            return strings;
        }

        public void setStrings(List<String> strings) {
            this.strings = strings;
        }

        public List<Duration> getDurations() {
            return durations;
        }

        public void setDurations(List<Duration> durations) {
            this.durations = durations;
        }

        public List<SimpleBean> getSimpleBeans() {
            return simpleBeans;
        }

        public void setSimpleBeans(List<SimpleBean> simpleBeans) {
            this.simpleBeans = simpleBeans;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            ListsBean listsBean = (ListsBean) o;
            return Objects.equals(integers, listsBean.integers) &&
                Objects.equals(strings, listsBean.strings) &&
                Objects.equals(durations, listsBean.durations) &&
                Objects.equals(periods, listsBean.periods) &&
                Objects.equals(simpleBeans, listsBean.simpleBeans) &&
                Objects.equals(configLists, listsBean.configLists) &&
                Objects.equals(valueOfBeans, listsBean.valueOfBeans);
        }

        @Override
        public int hashCode() {
            return Objects.hash(integers, strings, durations, periods, simpleBeans, configLists, valueOfBeans);
        }
    }

    public static class MapsBean {
        @Optional Map<String, Integer> intMap;
        @Optional Map<String, String> stringMap;
        @Optional Map<String, SimpleBean> beanMap;
        @Optional Map<String, ValueOfBean> valueOfBeanMap;

        public Map<String, Integer> getIntMap() {
            return intMap;
        }

        public void setIntMap(Map<String, Integer> intMap) {
            this.intMap = intMap;
        }

        public Map<String, String> getStringMap() {
            return stringMap;
        }

        public void setStringMap(Map<String, String> stringMap) {
            this.stringMap = stringMap;
        }

        public Map<String, SimpleBean> getBeanMap() {
            return beanMap;
        }

        public void setBeanMap(Map<String, SimpleBean> beanMap) {
            this.beanMap = beanMap;
        }

        public Map<String, ValueOfBean> getValueOfBeanMap() {
            return valueOfBeanMap;
        }

        public void setValueOfBeanMap(Map<String, ValueOfBean> valueOfBeanMap) {
            this.valueOfBeanMap = valueOfBeanMap;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            MapsBean bean = (MapsBean) o;
            return Objects.equals(intMap, bean.intMap) &&
                Objects.equals(stringMap, bean.stringMap) &&
                Objects.equals(beanMap, bean.beanMap) &&
                Objects.equals(valueOfBeanMap, bean.valueOfBeanMap);
        }

        @Override
        public int hashCode() {
            return Objects.hash(intMap, stringMap, beanMap, valueOfBeanMap);
        }
    }

    public static class ValueOfBean {

        String name;
        @Optional String option;

        @SuppressWarnings("unused")
        public ValueOfBean() {
        }

        public ValueOfBean(String name) {
            this.name = name;
        }

        public ValueOfBean(String name, String option) {
            this.name = name;
            this.option = option;
        }

        public static ValueOfBean valueOf(String s) {
            return new ValueOfBean(s);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getOption() {
            return option;
        }

        public void setOption(String option) {
            this.option = option;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            ValueOfBean that = (ValueOfBean) o;
            return Objects.equals(name, that.name) &&
                Objects.equals(option, that.option);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, option);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("ValueOfBean{");
            sb.append("name='").append(name).append('\'');
            sb.append(", option='").append(option).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }
}

