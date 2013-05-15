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

import org.rythmengine.internal.IContext;
import org.rythmengine.internal.IParser;
import org.rythmengine.internal.Keyword;
import org.rythmengine.internal.Token;
import org.rythmengine.internal.parser.RemoveLeadingLineBreakAndSpacesParser;
import org.rythmengine.utils.TextBuilder;
import com.stevesoft.pat.Regex;

/**
 * Parse @set("name":val())
 */
public class SetParser extends KeywordParserFactory {

    @Override
    public Keyword keyword() {
        return Keyword.SET;
    }

    @Override
    protected String patternStr() {
        return "^(\\n?[ \\t\\x0B\\f]*%s%s((?@()))([ \\t\\x0B\\f]*\\n?))";
    }

    @Override
    public IParser create(final IContext ctx) {
        return new RemoveLeadingLineBreakAndSpacesParser(ctx) {
            @Override
            public TextBuilder go() {
                Regex r = reg(dialect());
                if (!r.search(remain())) return null;
                final String matched = r.stringMatched();
                step(matched.length()); // remain: @set("name": val)...
                if (matched.startsWith("\n") || matched.endsWith("\n")) {
                    ctx.getCodeBuilder().addBuilder(new Token.StringToken("\n", ctx));
                    Regex r0 = new Regex("\\n([ \\t\\x0B\\f]*).*");
                    if (r0.search(matched)) {
                        String blank = r0.stringMatched(1);
                        if (blank.length() > 0) {
                            ctx.getCodeBuilder().addBuilder(new Token.StringToken(blank, ctx));
                        }
                    }
                } else {
                    Regex r0 = new Regex("([ \\t\\x0B\\f]*).*");
                    if (r0.search(matched)) {
                        String blank = r0.stringMatched(1);
                        if (blank.length() > 0) {
                            ctx.getCodeBuilder().addBuilder(new Token.StringToken(blank, ctx));
                        }
                    }
                }
                String s = r.stringMatched(2); // s: ("name": val)
                s = s.substring(1); // s: name: val)
                s = s.substring(0, s.length() - 1); // s: "name": val
                //r = new Regex("((?@\"\")|(?@'')|[a-zA-Z_][\\w_]+)\\s*[=:]\\s*('.'|(?@\"\")|[a-zA-Z_][a-zA-Z0-9_\\.]*(?@())*(?@[])*(?@())*(\\.[a-zA-Z][a-zA-Z0-9_\\.]*(?@())*(?@[])*(?@())*)*)");
                r = new Regex("((?@\"\")|(?@'')|[a-zA-Z_][\\w_]+)\\s*[=:]\\s*(.*)");
                if (!r.search(s)) {
                    raiseParseException("Error parsing @set tag. Correct usage: @set(\"name\": val)");
                }
                s = r.stringMatched(1); // propName: "name"
                if (s.startsWith("\"") || s.startsWith("'")) {
                    s = s.substring(1);
                    s = s.substring(0, s.length() - 1);
                    // propName: name
                }
                final String propName = s;
                final String propVal = r.stringMatched(2);
                logger.warn("@set directive is deprecated. Please follow instruction at http://localhost:9000/doc/directive.md#get to change your code");
                return new Token("", ctx()) {
                    @Override
                    protected void output() {
                        p("\n__setRenderProperty(\"").p(propName).p("\",").p(propVal).p(");");
                    }
                };
            }
        };
    }

}
