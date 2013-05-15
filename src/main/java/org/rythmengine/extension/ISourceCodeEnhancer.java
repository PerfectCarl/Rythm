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
package org.rythmengine.extension;

import org.rythmengine.template.ITemplate;

import java.util.List;
import java.util.Map;

/**
 * Allow user application or more probably a framework plugin based on rythm to inject
 * common import statements, render arguments and source code into generated java source code
 */
public interface ISourceCodeEnhancer {

    /**
     * Return a list of import statement to be injected into the generated java source code.
     * <p/>
     * <p>Note, only package declaration part needs to be put inside the Strings. the
     * <code>import</code> directive is not required. E.g.</p>
     * <p/>
     * <pre><code>
     * List<String> ls = new ArrayList<String>();
     * ls.put("models.*");
     * ls.put("controllers.*);
     * </code></pre>
     * <p/>
     * <p>This method is called by rythm when generating java source
     * code out from a template souce code</p>
     *
     * @return list of imports
     */
    List<String> imports();

    /**
     * Return source code to be added to template class. This method
     * is called during code generation to inject customized source code
     * into final generated template source. The string returned will be
     * injected into the generated template source code class body directly
     * 
     * @return the added source code
     */
    String sourceCode();

    /**
     * Return implicit render args type information indexed by render arg name.
     * The type info could be either a <code>Class</code> instance or a <code>String</code>
     * of the class name. E.g, if the implicit render args are
     * <p/>
     * <pre><code>
     * Map<String, Object> descs = new HashMap<String, Object>();
     * descs.put("_play", play.Play.class); // arg type with Class instance
     * descs.put("request", "play.mvc.Request"); // arg type with class name
     * </code></pre>
     * <p/>
     * <p>The method is called when {@link org.rythmengine.internal.CodeBuilder code builder}
     * generating the java source out from the template source</p>
     *
     * @return render arg types mapped by name
     */
    Map<String, ?> getRenderArgDescriptions();

    /**
     * Set implicit render arg values to a
     * {@link org.rythmengine.template.ITemplate template instance}. Usually inside this
     * method, the implicit arguments to be set should be corresponding to the
     * render args described in {@link #getRenderArgDescriptions()} method. E.g.
     * <p/>
     * <pre><code>
     * template.__setRenderArg("_play", new play.Play());
     * template.__setRenderArg("request", play.mvc.Request.current());
     * </code></pre>
     * <p/>
     * <p>This method is called before {@link org.rythmengine.RythmEngine rythm engine} start
     * to execute a template instance</p>
     *
     * @param template
     */
    void setRenderArgs(ITemplate template);

    /**
     * This is used by {@link org.rythmengine.conf.RythmConfiguration} and user application
     * should not use this static member
     */
    public static final class INSTS {

        public static final ISourceCodeEnhancer NULL = new ISourceCodeEnhancer() {
            @Override
            public Map<String, ?> getRenderArgDescriptions() {
                return null;
            }

            @Override
            public void setRenderArgs(ITemplate template) {
            }

            @Override
            public String sourceCode() {
                return null;
            }

            @Override
            public List<String> imports() {
                return null;
            }
        };
    }
}
