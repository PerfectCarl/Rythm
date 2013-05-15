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
package org.rythmengine.template;

import org.rythmengine.utils.TextBuilder;

/**
 * classes extends JavaTagBase are not template based, it's kind of like FastTag in Play
 */
public abstract class JavaTagBase extends TagBase {
    protected __ParameterList _params;
    protected __Body _body;

    public TemplateBase __setRenderArgs0(__ParameterList params) {
        _params = null == params ? new __ParameterList() : params;
        __renderArgs.putAll(params.asMap());
        return this;
    }

    @Override
    public ITemplate __setRenderArg(String name, Object val) {
        if ("__body".equals(name)) _body = (__Body) val;
        super.__setRenderArg(name, val);
        return this;
    }

    @Override
    public TextBuilder build() {
        if (null == _params) _params = new __ParameterList();
        call(_params, _body);
        return this;
    }

    @Override
    protected void __internalBuild() {
        build();
    }

    /**
     * Subclass overwrite this method and call various p() methods to render the output
     *
     * @param params
     * @param body
     */
    abstract protected void call(__ParameterList params, __Body body);
}
