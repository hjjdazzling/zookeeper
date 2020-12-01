/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zookeeper.server;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.SessionExpiredException;
import org.apache.zookeeper.common.Time;

/**
 * This is a full featured SessionTracker. It tracks session in grouped by tick
 * interval. It always rounds up the tick interval to provide a sort of grace
 * period. Sessions are thus expired in batches made up of sessions that expire
 * in a given interval.
 * session追踪器实现类
 * 这是一个拥有全部特性的session追踪器，根据心跳间隔分组追踪session， 它总是对心跳间隔取整，来提供一组优雅的时间间隔分类
 * 因此一批过期的session会由在一个给定的过期时间间隔内的session组成。
 */
public class SessionTrackerImpl extends ZooKeeperCriticalThread implements SessionTracker {

    private static final Logger LOG = LoggerFactory.getLogger(SessionTrackerImpl.class);
    private static Integer addCountTotal = 1;
    HashMap<Long, SessionImpl> sessionsById = new HashMap<Long, SessionImpl>();

    HashMap<Long, SessionSet> sessionSets = new HashMap<Long, SessionSet>();

    ConcurrentHashMap<Long, Integer> sessionsWithTimeout;
    long nextSessionId = 0;
    long nextExpirationTime;

    int expirationInterval;

    /**
     * 静态内部类
     */
    public static class SessionImpl implements Session {
        SessionImpl(long sessionId, int timeout, long expireTime) {
            this.sessionId = sessionId;
            this.timeout = timeout;
            this.tickTime = expireTime;
            isClosing = false;
        }

        final long sessionId;
        final int timeout;
        long tickTime;
        boolean isClosing;

        Object owner;
        @Override
        public long getSessionId() { return sessionId; }
        @Override
        public int getTimeout() { return timeout; }
        @Override
        public boolean isClosing() { return isClosing; }
    }

    public static long initializeNextSession(long id) {
        long nextSid = 0;
        nextSid = (Time.currentElapsedTime() << 24) >>> 8;
        nextSid =  nextSid | (id <<56);
        return nextSid;
    }

    static class SessionSet {
        HashSet<SessionImpl> sessions = new HashSet<SessionImpl>();
    }

    SessionExpirer expirer;

    private long roundToInterval(long time) {
        // We give a one interval grace period
        return (time / expirationInterval + 1) * expirationInterval;
    }

    public SessionTrackerImpl(SessionExpirer expirer,
            ConcurrentHashMap<Long, Integer> sessionsWithTimeout, int tickTime,
            long sid, ZooKeeperServerListener listener)
    {
        super("SessionTracker", listener);
        this.expirer = expirer;
        this.expirationInterval = tickTime;
        this.sessionsWithTimeout = sessionsWithTimeout;
        nextExpirationTime = roundToInterval(Time.currentElapsedTime());
        this.nextSessionId = initializeNextSession(sid);
        for (Entry<Long, Integer> e : sessionsWithTimeout.entrySet()) {
            addSession(e.getKey(), e.getValue());
        }
    }

    volatile boolean running = true;

    volatile long currentTime;

    /**
     *
     * @param pwriter the output writer
     */
    @Override
    synchronized public void dumpSessions(PrintWriter pwriter) {
        pwriter.print("Session Sets (");
        pwriter.print(sessionSets.size());
        pwriter.println("):");
        ArrayList<Long> keys = new ArrayList<Long>(sessionSets.keySet());
        Collections.sort(keys);
        for (long time : keys) {
            pwriter.print(sessionSets.get(time).sessions.size());
            pwriter.print(" expire at ");
            pwriter.print(new Date(time));
            pwriter.println(":");
            for (SessionImpl s : sessionSets.get(time).sessions) {
                pwriter.print("\t0x");
                pwriter.println(Long.toHexString(s.sessionId));
            }
        }
    }

    @Override
    synchronized public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter pwriter = new PrintWriter(sw);
        dumpSessions(pwriter);
        pwriter.flush();
        pwriter.close();
        return sw.toString();
    }

    @Override
    synchronized public void run() {
        try {
            while (running) {
                currentTime = Time.currentElapsedTime();
                if (nextExpirationTime > currentTime) {
                    this.wait(nextExpirationTime - currentTime);
                    continue;
                }
                SessionSet set;
                set = sessionSets.remove(nextExpirationTime);
                if (set != null) {
                    for (SessionImpl s : set.sessions) {
                        setSessionClosing(s.sessionId);
                        expirer.expire(s);
                    }
                }
                nextExpirationTime += expirationInterval;
            }
        } catch (InterruptedException e) {
            handleException(this.getName(), e);
        }
        LOG.info("SessionTrackerImpl exited loop!");
    }

    /**
     *
     * @param sessionId
     * @param timeout
     * @return
     */
    @Override
    synchronized public boolean touchSession(long sessionId, int timeout) {
        if (LOG.isTraceEnabled()) {
            ZooTrace.logTraceMessage(LOG,
                                     ZooTrace.CLIENT_PING_TRACE_MASK,
                                     "SessionTrackerImpl --- Touch session: 0x"
                    + Long.toHexString(sessionId) + " with timeout " + timeout);
        }
        SessionImpl s = sessionsById.get(sessionId);
        // Return false, if the session doesn't exists or marked as closing
        if (s == null || s.isClosing()) {
            return false;
        }
        long expireTime = roundToInterval(Time.currentElapsedTime() + timeout);
        if (s.tickTime >= expireTime) {
            // Nothing needs to be done
            return true;
        }
        SessionSet set = sessionSets.get(s.tickTime);
        if (set != null) {
            set.sessions.remove(s);
        }
        s.tickTime = expireTime;
        set = sessionSets.get(s.tickTime);
        if (set == null) {
            set = new SessionSet();
            sessionSets.put(expireTime, set);
        }
        set.sessions.add(s);
        return true;
    }

    /**
     *
     * @param sessionId
     */
    @Override
    synchronized public void setSessionClosing(long sessionId) {
        System.out.println("设置session正在关闭------->"+sessionId);
        addCountTotal--;
        if (LOG.isTraceEnabled()) {
            LOG.info("Session closing: 0x" + Long.toHexString(sessionId));
        }
        SessionImpl s = sessionsById.get(sessionId);
        if (s == null) {
            return;
        }
        s.isClosing = true;
    }

    /**
     *
     * @param sessionId
     */
    @Override
    synchronized public void removeSession(long sessionId) {
        System.out.println("去除session------->"+sessionId);
        //这是我自己填的，大家别在意，可以注释掉。
        synchronized (addCountTotal){
            System.out.println("剩余session数量------>"+addCountTotal--);
        }
        SessionImpl s = sessionsById.remove(sessionId);
        sessionsWithTimeout.remove(sessionId);
        if (LOG.isTraceEnabled()) {
            ZooTrace.logTraceMessage(LOG, ZooTrace.SESSION_TRACE_MASK,
                    "SessionTrackerImpl --- Removing session 0x"
                    + Long.toHexString(sessionId));
        }
        if (s != null) {
            SessionSet set = sessionSets.get(s.tickTime);
            // Session expiration has been removing the sessions   
            if(set != null){
                set.sessions.remove(s);
            }
        }
    }

    /**
     *
     */
    @Override
    public void shutdown() {
        LOG.info("Shutting down");
        running = false;
        if (LOG.isTraceEnabled()) {
            ZooTrace.logTraceMessage(LOG, ZooTrace.getTextTraceLevel(),
                                     "Shutdown SessionTrackerImpl!");
        }
    }

    @Override
    synchronized public long createSession(int sessionTimeout) {
        System.out.println("构建session"+nextSessionId);
        addSession(nextSessionId, sessionTimeout);
        return nextSessionId++;
    }
    @Override
    synchronized public void addSession(long id, int sessionTimeout) {
        System.out.println("添加session"+id);
        sessionsWithTimeout.put(id, sessionTimeout);
        if (sessionsById.get(id) == null) {

            SessionImpl s = new SessionImpl(id, sessionTimeout, 0);
            sessionsById.put(id, s);
            System.out.println("已经添加session数量"+SessionTrackerImpl.addCountTotal++);
            if (LOG.isTraceEnabled()) {
                ZooTrace.logTraceMessage(LOG, ZooTrace.SESSION_TRACE_MASK,
                        "SessionTrackerImpl --- Adding session 0x"
                        + Long.toHexString(id) + " " + sessionTimeout);
            }
        } else {
            if (LOG.isTraceEnabled()) {
                ZooTrace.logTraceMessage(LOG, ZooTrace.SESSION_TRACE_MASK,
                        "SessionTrackerImpl --- Existing session 0x"
                        + Long.toHexString(id) + " " + sessionTimeout);
            }
        }
        touchSession(id, sessionTimeout);

    }

    /**
     *
     * @param sessionId
     * @param owner
     * @throws KeeperException.SessionExpiredException
     * @throws KeeperException.SessionMovedException
     */
    @Override
    synchronized public void checkSession(long sessionId, Object owner) throws KeeperException.SessionExpiredException, KeeperException.SessionMovedException {
        System.out.println("检验session-------->"+sessionId);
        SessionImpl session = sessionsById.get(sessionId);
        if (session == null || session.isClosing()) {
            throw new KeeperException.SessionExpiredException();
        }
        if (session.owner == null) {
            session.owner = owner;
        } else if (session.owner != owner) {
            throw new KeeperException.SessionMovedException();
        }
    }

    /**
     *
     * @param id
     * @param owner
     * @throws SessionExpiredException
     */
    @Override
    synchronized public void setOwner(long id, Object owner) throws SessionExpiredException {
        System.out.println("设置session的拥有者"+owner+"--->"+id);
        SessionImpl session = sessionsById.get(id);
        if (session == null || session.isClosing()) {
            throw new KeeperException.SessionExpiredException();
        }
        session.owner = owner;
    }
}
