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
package org.rythmengine.internal.compiler;

import org.rythmengine.RythmEngine;
import org.rythmengine.utils.S;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Facilitate {@link org.rythmengine.RythmEngine} to infer param types
 */
public class ParamTypeInferencer {

    public static String typeTransform(String type) {
        type = type.trim();
        if (type.contains("boolean") && type.matches("(.*[^a-zA-Z0-9_]+)?boolean([^a-zA-Z0-9_].*|$)")) return type.replace("boolean","Boolean");
        else if (type.contains("int") && type.matches("(.*[^a-zA-Z0-9_]+)?int([^a-zA-Z0-9_].*|$)")) return type.replace("int","Integer");
        else if (type.contains("float") && type.matches("(.*[^a-zA-Z0-9_]+)?float([^a-zA-Z0-9_].*|$)")) return type.replace("float","Float");
        else if (type.contains("double") && type.matches("(.*[^a-zA-Z0-9_]+)?double([^a-zA-Z0-9_].*|$)")) return type.replace("double","Double");
        else if (type.contains("char") && type.matches("(.*[^a-zA-Z0-9_]+)?char([^a-zA-Z0-9_].*|$)")) return type.replace("char","Character");
        else if (type.contains("long") && type.matches("(.*[^a-zA-Z0-9_]+)?long([^a-zA-Z0-9_].*|$)")) return type.replace("long","Long");
        else if (type.contains("byte") && type.matches("(.*[^a-zA-Z0-9_]+)?byte([^a-zA-Z0-9_].*|$)")) return type.replace("byte","Byte");
        else return type;
    }

    private static final ThreadLocal<Map<String, String>> typeMap = new ThreadLocal<Map<String, String>>() {
        @Override
        protected Map<String, String> initialValue() {
            return new HashMap<String, String>();
        }
    };

    private static final ThreadLocal<String> uuid = new ThreadLocal<String>() {
        @Override
        protected String initialValue() {
            return "";
        }
    };

    private static String getTypeName(Object val) {
        String clsName;
        if (null == val) {
            clsName = "Object";
        } else {
            Class c = val.getClass();
            clsName = c.getName();
            if (clsName.contains("$")) {
                //anonymous or embedded class, let's try parent type
                if (clsName.startsWith("java.")) {
                    clsName = c.getSuperclass().getName();  
                } else {
                    clsName = clsName.replace('$', '.');
                }
            }
            if (c.isArray()) {
                Class cc = c.getComponentType();
                while (cc.isArray()) cc = cc.getComponentType();
                String cName = cc.getName();
                String s = clsName;
                // now count the number of '[' to see how many dimension this array has
                int d = 0;
                for (int i = 0; i < s.length(); i++) {
                    if (s.charAt(i) == '[') {
                        d++;
                    } else {
                        break;
                    }
                }
                StringBuilder sb = new StringBuilder(cName);
                for (int i = 0; i < d; ++i) {
                    sb.append("[]");
                }
                clsName = sb.toString();
            } else {
                // try to see if this is a generic type
                if (val instanceof Collection) {
                    Collection col = (Collection)val;
                    if (col.size() > 0) {
                        if (val instanceof Map) {
                            Object k = ((Map) val).keySet().iterator().next();
                            Object v = ((Map) val).get(k);
                            String kType = getTypeName(k);
                            String vType = null == v ? "Object" : getTypeName(v);
                            clsName = clsName + "<" + kType + "," + vType + ">";
                        } else {
                            Object e = col.iterator().next();
                            String eType = null == val ? "Object" : getTypeName(e);
                            clsName = clsName + "<" + eType + ">";
                        }
                    }
                }
            }
        }
        return clsName;
    }

    public static void registerParams(RythmEngine engine, Object... args) {
        if (!engine.conf().typeInferenceEnabled()) return;

        if (null == args || args.length == 0) return;

        Map<String, String> tMap = typeMap.get();
        tMap.clear();
        long id = 0;
        if (args.length == 1 && args[0] instanceof Map) {
            Map<String, Object> params = (Map) args[0];
            for (String name : params.keySet()) {
                Object val = params.get(name);
                String typeName = getTypeName(val);
                tMap.put(name, typeName);
                id += typeName.hashCode() * name.hashCode();
            }
        } else {
            // suppose template variable is denoted with @1, @2 ...
            for (int i = 0; i < args.length; ++i) {
                String name = "__v_" + (i + 1); // start from 1 instead of 0
                String typeName = getTypeName(args[i]);
                tMap.put(name, typeName);
                id += (i + 1) * typeName.hashCode();
            }
        }
        if (id < 0) id = -1 * id;
        uuid.set(String.valueOf(id));
    }
    
    public static String uuid() {
        return S.str(uuid.get());
    }

    public static Map<String, String> getTypeMap() {
        return typeMap.get();
    }

}
