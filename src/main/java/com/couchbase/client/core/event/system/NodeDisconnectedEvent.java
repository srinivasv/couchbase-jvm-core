/**
 * Copyright (c) 2015 Couchbase, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
 * IN THE SOFTWARE.
 */
package com.couchbase.client.core.event.system;

import com.couchbase.client.core.event.CouchbaseEvent;
import com.couchbase.client.core.event.EventType;
import com.couchbase.client.core.utils.Events;

import java.net.InetAddress;
import java.util.Map;

/**
 * Event published when a node is disconnected.
 *
 * @author Michael Nitschinger
 * @since 1.1.0
 */
public class NodeDisconnectedEvent implements CouchbaseEvent {

    private final InetAddress host;

    public NodeDisconnectedEvent(InetAddress host) {
        this.host = host;
    }

    @Override
    public EventType type() {
        return EventType.SYSTEM;
    }

    /**
     * The host address of the disconnected node.
     *
     * @return the inet address of the disconnected node
     */
    public InetAddress host() {
        return host;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("NodeDisconnectedEvent{");
        sb.append("host=").append(host);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> result = Events.identityMap(this);
        result.put("host", host().toString());
        return result;
    }
}
