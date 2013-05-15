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
package org.rythmengine.internal;

import org.rythmengine.RythmEngine;
import org.rythmengine.internal.compiler.TemplateClass;

public interface IDialect {

    /**
     * Return the ID of the dialect, might be something like "rythm" or "play-groovy" etc.
     *
     * @return dialect id
     */
    String id();

    /**
     * Return the primary caret marker, e.g. "#" in play-groovy, "@" in rythm and "`" in japid. To escape the
     * marker repeat the marker twice, e.g. "@@", "##", "``"
     *
     * @return the primary caret
     */
    String a();

    /**
     * Register a special case parser which will be processed before all other parsers
     * <p/>
     * <p>for example, the rythm extension for play!framework might want to register a special case parser to
     * process something like @{Controller.actionMethod()} or &{'MSG_ID'} etc.
     *
     * @param parser
     */
    void registerParserFactory(IParserFactory parser);

    boolean isMyTemplate(String template);

    void begin(IContext ctx);

    void end(IContext ctx);

    boolean enableScripting();

    boolean enableFreeForLoop();

    CodeBuilder createCodeBuilder(String template, String className, String tagName, TemplateClass templateClass, RythmEngine engine);
}
