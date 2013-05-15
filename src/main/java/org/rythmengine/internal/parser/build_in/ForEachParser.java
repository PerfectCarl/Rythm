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

import org.rythmengine.internal.*;
import org.rythmengine.internal.parser.BlockCodeToken;
import org.rythmengine.internal.parser.RemoveLeadingSpacesIfLineBreakParser;
import org.rythmengine.logger.ILogger;
import org.rythmengine.logger.Logger;
import org.rythmengine.utils.S;
import org.rythmengine.utils.TextBuilder;
import com.stevesoft.pat.Regex;

public class ForEachParser extends KeywordParserFactory {
    private static final ILogger logger = Logger.get(ForEachParser.class);

    public IParser create(final IContext ctx) {

        return new RemoveLeadingSpacesIfLineBreakParser(ctx) {
            public TextBuilder go() {
                Regex r = new Regex(String.format(patternStr2(), dialect().a(), keyword()));
                String remain = remain();
                if (!r.search(remain)) {
                    raiseParseException("Error parsing @for statement, correct usage: @for(Type var: Iterable){...} or @for(int i = ...)");
                }
                int lineNo = ctx.currentLine();
                final String matched = r.stringMatched();
                boolean leadingLB = !isLastBuilderLiteral();
                if (matched.startsWith("\n") || matched.endsWith("\n")) {
                    if (matched.startsWith("\n")) {
                        leadingLB = true;
                        lineNo++;
                    }
                    ctx.getCodeBuilder().addBuilder(new Token.StringToken("\n", ctx));
                    if (!matched.startsWith("\n")) {
                        Regex r0 = new Regex("\\n([ \\t\\x0B\\f]*).*");
                        if (r0.search(matched)) {
                            String blank = r0.stringMatched(1);
                            if (blank.length() > 0) {
                                ctx.getCodeBuilder().addBuilder(new Token.StringToken(blank, ctx));
                            }
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
                String s = r.stringMatched(2);
                String sep = r.stringMatched(4);
                if (!S.empty(sep)) {
                    sep = sep.replaceAll("(\\r?\\n)+", "\\\\n");
                    sep = S.stripBrace(sep).trim();
                    boolean hasQuotation = sep.startsWith("'");
                    if (hasQuotation) {
                        sep = S.strip(sep, "'", "'");
                        sep = "\"" + sep + "\"";
                    }
                    if ("".equals(sep)) sep = "\",\"";
                    else sep = ExpressionParser.processPositionPlaceHolder(sep);
                }
                final String separator = sep;
                if (s.contains(";")) {
                    if (!ctx().getDialect().enableFreeForLoop()) {
                        throw new TemplateParser.NoFreeLoopException(ctx());
                    }
                    String s1 = "for ";
                    String s2 = "{ //line: " + lineNo + "\n\t";
                    if (null != sep) {
                        String varCursor = ctx.getCodeBuilder().newVarName();
                        s1 = "int " + varCursor + " = 0;//line: " + lineNo + "\nfor ";
                        s2 = s2 + "if (" + varCursor + "++ > 0) {p(" + separator + ");} //line: " + lineNo + "\n\t"; 
                    }
                    // get rid of the "{" if it is followed
                    processFollowingOpenBraceAndLineBreak(leadingLB);
                    return new BlockCodeToken(s1 + s + s2, ctx()) {
                        @Override
                        public void openBlock() {
                            ctx().pushBreak(IContext.Break.BREAK);
                            ctx().pushContinue(IContext.Continue.CONTINUE);
                        }

                        @Override
                        public void output() {
                            super.output();
                        }

                        @Override
                        public String closeBlock() {
                            ctx().popBreak();
                            return super.closeBlock();
                        }
                    };
                } else {
                    s = S.stripBrace(s);
                    int pos0 = -1, pos1 = -1;
                    String iterable = null, varname = null, type = null;
                    if (s.matches("\\s*\".*")) {
                        iterable = s;
                    } else if (s.contains(" in ")) {
                        pos0 = s.indexOf(" in ");
                        pos1 = pos0 + 4;
                    } else if (s.contains(" <- ")) {
                        pos0 = s.indexOf(" <- ");
                        pos1 = pos0 + 4;
                    } else if (s.contains(":")) {
                        pos0 = s.indexOf(":");
                        pos1 = pos0 + 1;
                    } else {
                        // the for(Iterable) style
                        iterable = s;
                    }
                    if (-1 != pos0) {
                        String s1 = s.substring(0, pos0).trim();
                        iterable = s.substring(pos1, s.length());
                        if (s1.contains(" ")) {
                            pos0 = s1.lastIndexOf(" ");
                            type = s1.substring(0, pos0);
                            varname = s1.substring(pos0, s1.length());
                        } else {
                            varname = s1;
                        }
                    }
                    processFollowingOpenBraceAndLineBreak(leadingLB);
                    return new ForEachCodeToken(type, varname, iterable, ctx(), lineNo, separator);
                }
            }
        };
    }

    @Override
    public Keyword keyword() {
        return Keyword.EACH;
    }

    // match for(int i=0; i<100;++i) {
    protected String patternStr2() {
        return "^\\n?[ \\t\\x0B\\f]*%s%s\\s*((?@()))(\\.join((?@())))?([ \\t\\x0B\\f]*\\n?)";
    }

    @Override
    protected String patternStr() {
        //return "^(%s%s(\\s*\\(\\s*)(((" + Patterns.Type + "\\s+)?)((" + Patterns.VarName + "))?)\\s*([\\:]?)\\s*(" + Patterns.Expression2 + ")(\\s*\\)?[\\s\\r\\n]*|[\\s\\r\\n]+)\\{?[\\s\\r\\n]*).*";
        //return "^(((" + Patterns.Type + ")\\s+)?(" + Patterns.VarName + ")\\s*\\:\\s*)?(" + Patterns.Expression2 + ")$";
        //return "^((([a-zA-Z0-9_\\.]+)(\\s*\\[\\s*\\]|\\s*(?@<>))?\\s+)?(" + Patterns.VarName + ")\\s*\\:\\s*)?(" + Patterns.Expression2 + ")$";
        //return "^((([a-zA-Z0-9_\\.]+)(\\s*\\[\\s*\\]|\\s*(?@<>))?\\s+)?(" + Patterns.VarName + ")\\s*\\:\\s*)?(" + Patterns.Expression2 + ")$";
        // this method not used anymore
        return null;
    }
}
