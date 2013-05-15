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
import org.rythmengine.internal.parser.BlockCodeToken;
import org.rythmengine.internal.parser.CodeToken;
import org.rythmengine.internal.parser.ParserBase;
import org.rythmengine.internal.parser.Patterns;
import org.rythmengine.utils.S;
import org.rythmengine.utils.TextBuilder;
import com.stevesoft.pat.Regex;

import java.util.regex.Matcher;

/**
 * Parse @render section|content
 */
public class RenderSectionParser extends KeywordParserFactory {

    public class DefaultSectionToken extends BlockCodeToken {
        private String section;

        public DefaultSectionToken(String section, IContext context) {
            super(null, context);
            if (S.isEmpty(section)) {
                this.section = "__CONTENT__";
            }
            this.section = section;
        }

        @Override
        public void output() {
            p2t("__startSection(\"").p(section).p("\");");
            pline();
        }

        @Override
        public String closeBlock() {
            StringBuilder sbNew = new StringBuilder();
            StringBuilder sbOld = __getBuffer();
            __setBuffer(sbNew);
            p2tline("__endSection(true);");
            if ("__CONTENT__".equals(section)) {
                p2tline("__pLayoutContent();");
            } else {
                p2t("__pLayoutSection(\"").p(section).p("\");");
                pline();
            }
            __setBuffer(sbOld);
            return sbNew.toString();
        }
    }

    @Override
    public Keyword keyword() {
        return Keyword.RENDER_SECTION;
    }

    public IParser create(final IContext ctx) {
        return new ParserBase(ctx) {
            public TextBuilder go() {
                Matcher m = ptn(dialect()).matcher(remain());
                if (!m.matches()) return null;
                String matched = m.group(1);
                boolean lineBreak = false;
                if (matched.startsWith("\n") || matched.endsWith("\n")) {
                    lineBreak = matched.endsWith("\n");
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
                String section = m.group(4);
                String s = remain();
                Matcher m0 = InvokeTemplateParser.P_HEREDOC_SIMBOL.matcher(s);
                Matcher m1 = InvokeTemplateParser.P_STANDARD_BLOCK.matcher(s);
                if (m0.matches()) {
                    ctx().step(m0.group(1).length());
                    return new DefaultSectionToken(section, ctx());
                } else if (m1.matches()) {
                    ctx().step(m1.group(1).length());
                    return new DefaultSectionToken(section, ctx());
                } else {
                    boolean isSection = S.notEmpty(section);
                    if (matched.startsWith("\n") && !isSection) {
                        ctx.getCodeBuilder().addBuilder(new Token.StringToken("\n", ctx));
                    }
                    String code = !isSection ? "__pLayoutContent();" : "__pLayoutSection(\"" + section + "\");";
                    if (lineBreak && isSection) {
                        //layout content often come from a file which always gets one additional line break
                        code = code + ";\npn();\n";
                    }
                    return new CodeToken(code, ctx());
                }
            }
        };
    }

    @Override
    protected String patternStr() {
        return "(^\\n?[ \\t\\x0B\\f]*%s%s\\s*[\\s\\(]\"?'?(" + Patterns.VarName + ")?\"?'?\\)?).*";
    }

}
