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
package org.rythmengine.internal.parser.build_in;

import org.rythmengine.extension.ICodeType;
import org.rythmengine.internal.IContext;
import org.rythmengine.internal.parser.CodeToken;
import org.rythmengine.internal.parser.ParserBase;
import org.rythmengine.logger.ILogger;
import org.rythmengine.logger.Logger;
import org.rythmengine.utils.TextBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detect if a type block is ended put
 * instruction in template java source to switch
 * back type context
 * <p/>
 * <p>For example when &lt;/script&gt; is reached
 * a instruction <code>popCodeType()</code>
 * should be put in place</p>
 */
public class CodeTypeBlockEndSensor extends ParserBase {
    ILogger logger = Logger.get(CodeTypeBlockEndSensor.class);

    public CodeTypeBlockEndSensor(IContext context) {
        super(context);
    }

    private static Map<String, Pattern> patterns = new HashMap<String, Pattern>();

    @Override
    public TextBuilder go() {
        IContext ctx = ctx();
        ICodeType curType = ctx.peekCodeType();
        if (curType.allowedExternalTypes().isEmpty()) return null;

        String remain = ctx.getRemain();

        String blockEnd = curType.blockEnd();
        if (null == blockEnd) {
            logger.warn("null block end found for type[%s]", curType);
            return null;
        }

        Pattern p = patterns.get(blockEnd);
        if (null == p) {
            p = Pattern.compile(blockEnd, Pattern.DOTALL);
            patterns.put(blockEnd, p);
        }
        Matcher m = p.matcher(remain);
        if (m.matches()) {
            String matched = m.group(1);
            ctx.step(matched.length());
            ctx.popCodeType();
            String s = String.format("p(\"%s\");__ctx.popCodeType();", matched, curType);
            return new CodeToken(s, ctx);
        }

        return null;
    }
}
