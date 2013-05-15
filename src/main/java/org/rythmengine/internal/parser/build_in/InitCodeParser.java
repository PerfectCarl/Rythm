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
import org.rythmengine.internal.parser.Directive;
import org.rythmengine.internal.parser.RemoveLeadingLineBreakAndSpacesParser;
import org.rythmengine.utils.TextBuilder;
import com.stevesoft.pat.Regex;

/**
 * Parse @init{arg1 = "";...}
 */
public class InitCodeParser extends KeywordParserFactory {

    private static final String R = "(\\n?[ \\t\\x0B\\f]*%s%s\\s*(\\(\\s*\\))?\\s*((?@{}))?[ \\t\\x0B\\f]*\\n?)";

    public InitCodeParser() {
    }

    protected String patternStr() {
        return R;
    }

    public IParser create(final IContext ctx) {
        return new RemoveLeadingLineBreakAndSpacesParser(ctx) {
            public TextBuilder go() {
                Regex r = reg(dialect());
                if (r.search(remain())) {
                    final String matched = r.stringMatched();
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
                    step(matched.length());
                    String s0 = r.stringMatched(3);
                    s0 = s0.substring(1); // strip '{'
                    s0 = s0.substring(0, s0.length() - 1); // strip '}'
                    String s = s0;
                    return new Directive(s, ctx) {
                        @Override
                        public void call() {
                            ctx.getCodeBuilder().setInitCode(s);
                        }
                    };
                }
                raiseParseException("Invalid @init syntax found. Correct usage: @init{/*your code come here*/}");
                return null;
            }
        };
    }

    @Override
    public Keyword keyword() {
        return Keyword.INIT;
    }
}
