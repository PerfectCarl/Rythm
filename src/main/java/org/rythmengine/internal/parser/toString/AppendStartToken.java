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
package org.rythmengine.internal.parser.toString;

import org.rythmengine.internal.parser.CodeToken;
import org.rythmengine.utils.TextBuilder;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 15/07/12
 * Time: 8:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class AppendStartToken extends CodeToken {

    private static String getCode() {
        return "__style.appendStart(buffer(), _);";
    }

    public AppendStartToken(TextBuilder caller) {
        super(getCode(), caller);
    }
}
