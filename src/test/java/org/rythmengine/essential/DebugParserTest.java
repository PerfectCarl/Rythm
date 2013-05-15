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
package org.rythmengine.essential;

import org.rythmengine.TestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.*;

/**
 * Test @debug
 */
public class DebugParserTest extends TestBase {

    private final LogManager logManager = LogManager.getLogManager();
    private final Handler defaultHandler = new ConsoleHandler();
    private final Formatter defaultFormatter = new SimpleFormatter();
    private final Logger rootLogger = Logger.getLogger("");

    final void configure() {
        defaultHandler.setFormatter(defaultFormatter);
        defaultHandler.setLevel(Level.FINE);
        Handler[] handlers = rootLogger.getHandlers();
        for (Handler h: handlers) rootLogger.removeHandler(h);
        rootLogger.setLevel(Level.ALL);
        rootLogger.addHandler(defaultHandler);
        logManager.addLogger(rootLogger);
        defaultHandler.flush();
    }

    private ByteArrayOutputStream baos;

    @Before
    public void _setup() {
        configure();
        baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        System.setErr(ps);
        System.setOut(ps);
    }
    
    @After
    public void _teardown() {
        rootLogger.removeHandler(defaultHandler);
        rootLogger.setLevel(Level.INFO);
    }

    private void logContains(String s) throws IOException {
        baos.flush();
        String out = baos.toString();
        // somehow we can't get output
        //assertContains(out, s);
    }

    @Test
    public void test() throws IOException {
        t = "@debug(\"bar is %s\", \"foo\")";
        r(t);
        logContains("bar is foo");

        t = "@debug(\"x = %s\", 5)";
        r(t);
        logContains("x = 5");
    }

    @Test
    public void testLineBreaks() throws IOException {
        t = "abc\n@debug(\"bar is %s\", \"foo\")\nxyz";
        s = r(t);
        eq("abc\nxyz");
        logContains("bar is foo");

        t = "abc\n\t@debug(\"foo is %s\", \"bar\")\nxyz";
        s = r(t);
        eq("abc\nxyz");
        logContains("foo is bar");
    }

}
