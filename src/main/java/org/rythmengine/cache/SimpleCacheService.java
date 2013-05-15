/* 
 * Copyright (C) 2013 The Rythm Engine project
 * Gelin Luo <greenlaw110(at)gmail.com>
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package org.rythmengine.cache;

import org.rythmengine.extension.ICacheService;
import org.rythmengine.internal.RythmThreadFactory;
import org.rythmengine.logger.ILogger;
import org.rythmengine.logger.Logger;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A simple cache service implementation
 */
public class SimpleCacheService implements ICacheService {
    private static final ILogger logger = Logger.get(SimpleCacheService.class);

    public static final SimpleCacheService INSTANCE = new SimpleCacheService();

    private static class TimerThreadFactory extends RythmThreadFactory {
        private TimerThreadFactory() {
            super("rythm-timer");
        }
    }

    private ScheduledExecutorService scheduler = null;

    private SimpleCacheService() {
        startup();
    }

    private static class Item implements Comparable<Item> {
        String key;
        Serializable value;
        long ts;
        int ttl;

        Item(String key, Serializable value, int ttl) {
            this.key = key;
            this.value = value;
            this.ttl = ttl;
            this.ts = System.currentTimeMillis();
        }
        
        @Override
        public int compareTo(Item that) {
            return ttl - that.ttl;
        }
    }

    private ConcurrentHashMap<String, Item> cache_ = new ConcurrentHashMap<String, Item>();
    private Queue<Item> items_ = new PriorityQueue<Item>();

    @Override
    public void put(String key, Serializable value, int ttl) {
        if (null == key) throw new NullPointerException();
        if (0 >= ttl) {
            ttl = defaultTTL;
        }
        Item item = cache_.get(key);
        if (null == item) {
            Item newItem = new Item(key, value, ttl);
            item = cache_.putIfAbsent(key, newItem);
            if (null != item) {
                item.value = value;
                item.ttl = ttl;
            } else {
                items_.offer(newItem);
            }
        } else {
            item.value = value;
            item.ttl = ttl;
        }
    }

    @Override
    public void put(String key, Serializable value) {
        put(key, value, defaultTTL);
    }

    @Override
    public Serializable remove(String key) {
        Item item = cache_.remove(key);
        return null == item ? null : item.value;
    }

    @Override
    public void clear() {
        cache_.clear();
        items_.clear();
    }
    
    @Override
    public Serializable get(String key) {
        Item item = cache_.get(key);
        return null == item ? null : item.value;
    }

    @Override
    public boolean contains(String key) {
        return cache_.contains(key);
    }

    private int defaultTTL = 60;

    @Override
    public void setDefaultTTL(int ttl) {
        if (ttl == 0) throw new IllegalArgumentException("time to live value couldn't be zero");
        this.defaultTTL = ttl;
    }

    @Override
    public void shutdown() {
        clear();
        if (null != scheduler) {
            scheduler.shutdown();
            scheduler = null;
        }
    }
    
    @Override
    public void startup() {
        if (null == scheduler) {
            scheduler = new ScheduledThreadPoolExecutor(1, new TimerThreadFactory());
            scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    if (items_.size() == 0) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    if (logger.isTraceEnabled()) {
                        logger.trace(">>>>now:%s", now);
                    }
                    while(true) {
                        Item item = items_.peek();
                        if (null == item) {
                            break;
                        }
                        long ts = item.ts + item.ttl * 1000;
                        if ((ts) < now + 50) {
                            items_.poll();
                            cache_.remove(item.key);
                            if (Logger.isTraceEnabled()) {
                                logger.trace("- %s at %s", item.key, ts);
                            }
                            continue;
                        } else {
                            if (Logger.isTraceEnabled()) {
                                logger.trace(">>>>ts:  %s", ts);
                            }
                        }
                        break;
                    }
                }
            }, 0, 100, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        shutdown();
    }
}
