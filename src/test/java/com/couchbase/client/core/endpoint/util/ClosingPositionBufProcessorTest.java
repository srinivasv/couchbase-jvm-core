/*
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

package com.couchbase.client.core.endpoint.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class ClosingPositionBufProcessorTest {

    @Test
    public void shouldFindClosingInSimpleSection() {
        ByteBuf source = Unpooled.copiedBuffer("{ this is simple }", CharsetUtil.UTF_8);
        int closingPos = source.forEachByte(new ClosingPositionBufProcessor('{', '}'));
        assertEquals(17, closingPos);
        assertEquals(0, source.readerIndex());
    }

    @Test
    public void shouldNotFindClosingInBrokenSection() {
        ByteBuf source = Unpooled.copiedBuffer("{ this is simple", CharsetUtil.UTF_8);
        int closingPos = source.forEachByte(new ClosingPositionBufProcessor('{', '}'));
        assertEquals(-1, closingPos);
        assertEquals(0, source.readerIndex());
    }

    @Test
    public void shouldFindClosingInSectionWithSubsection() {
        ByteBuf source = Unpooled.copiedBuffer("{ this is { simple } }", CharsetUtil.UTF_8);
        int closingPos = source.forEachByte(new ClosingPositionBufProcessor('{', '}'));
        assertEquals(21, closingPos);
        assertEquals(0, source.readerIndex());
    }

    @Test
    public void shouldNotFindClosingInBrokenSectionWithCompleteSubSection() {
        ByteBuf source = Unpooled.copiedBuffer("{ this is { complex } oups", CharsetUtil.UTF_8);
        int closingPos = source.forEachByte(new ClosingPositionBufProcessor('{', '}'));
        assertEquals(-1, closingPos);
        assertEquals(0, source.readerIndex());
    }

    @Test
    public void shouldIgnoreJsonStringWithRandomSectionChars() {
        ByteBuf source = Unpooled.copiedBuffer(
                "{ this is \"a string \\\"with escaped quote and sectionChars like } or {{{!\" }", CharsetUtil.UTF_8);

        int closingPos = source.forEachByte(new ClosingPositionBufProcessor('{', '}', true));

        assertEquals(74, closingPos);
        assertEquals(0, source.readerIndex());
    }

    @Test
    public void shouldIgnoreJsonStringWithClosingSectionCharEvenIfStreamInterrupted() {
        ByteBuf source = Unpooled.copiedBuffer(
                "{ this is \"a string \\\"with }", CharsetUtil.UTF_8);

        int closingPos = source.forEachByte(new ClosingPositionBufProcessor('{', '}', true));

        assertEquals(-1, closingPos);
        assertEquals(0, source.readerIndex());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentWhenSymmetricChars() {
        new ClosingPositionBufProcessor('"', '"', true);
    }
}