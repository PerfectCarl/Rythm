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
package org.rythmengine.conf;

import org.rythmengine.Rythm;
import org.rythmengine._Rythm;
import org.rythmengine.cache.NoCacheService;
import org.rythmengine.cache.SimpleCacheService;
import org.rythmengine.exception.ConfigurationException;
import org.rythmengine.extension.ICodeType;
import org.rythmengine.extension.IDurationParser;
import org.rythmengine.extension.II18nMessageResolver;
import org.rythmengine.logger.JDKLogger;
import org.rythmengine.utils.S;

import java.io.File;
import java.net.URL;
import java.util.*;

/**
 * {@link org.rythmengine.RythmEngine} configuration keys. General rules:
 * <p/>
 * <ul>
 * <li>When a key is ended with <code>.enabled</code>, then you should be able to set
 * the setting without <code>.enabled</code> or replace it with <code>.disabled</code>
 * but the value will be inverted. For example, <code>built_in.transformer.enabled</code>
 * is equal to <code>built_in.transformer</code> and invert to
 * <code>built_in.transformer.disabled</code></li>
 * <li>When a key is ended with <code>.impl</code>, then you can either put an instance into
 * the configuration map or a string of the class name</li>
 * </ul>
 */
public enum RythmConfigurationKey {

    /**
     * "built_in.code_type.enabled": Enable built-in {@link org.rythmengine.extension.ICodeType code type} implementations
     * <p/>
     * <p>Default value: <code>true</code></p>
     */
    BUILT_IN_CODE_TYPE_ENABLED("built_in.code_type.enabled", true),

    /**
     * "built_in.transformer.enabled": Enable built-in {@link org.rythmengine.extension.Transformer transformer} implementations
     * <p/>
     * <p>Default value: <code>true</code></p>
     *
     * @see #FEATURE_TRANSFORM_ENABLED
     */
    BUILT_IN_TRANSFORMER_ENABLED("built_in.transformer.enabled", true),

    /**
     * "cache.enabled": Enable disable {@link org.rythmengine.extension.ICacheService cache service}. When this
     * setting is turned off, the {@link #CACHE_SERVICE_IMPL} will be set to
     * {@link org.rythmengine.cache.NoCacheService} without regarding to it's configuration
     * <p/>
     * <p>Default value: <code>false</code></p>
     * <p/>
     * TODO: add link to cache service reference
     */
    CACHE_ENABLED("cache.enabled", false),

    /**
     * "cache.service.impl": Set {@link org.rythmengine.extension.ICacheService cache service} implementation
     * <p/>
     * <p>Default value: {@link org.rythmengine.cache.SimpleCacheService}</p>
     * <p/>
     * <p>Note when {@link #CACHE_ENABLED} is set to <code>false</code>, then this setting
     * will be ignored, and the service impl will be set to {@link org.rythmengine.cache.NoCacheService}
     * anyway</p>
     * <p/>
     * TODO: add link to cache service reference
     */
    CACHE_SERVICE_IMPL("cache.service.impl") {
        @Override
        protected Object getDefVal(Map<String, ?> configuration) {
            Boolean cacheEnabled = CACHE_ENABLED.getConfiguration(configuration);
            return cacheEnabled ? SimpleCacheService.INSTANCE : NoCacheService.INSTANCE;
        }
    },

    /**
     * "cache.duration_parser.impl": set {@link org.rythmengine.extension.IDurationParser duration parser} implementation.
     * <p/>
     * <p>Default value: {@link org.rythmengine.extension.IDurationParser#DEFAULT_PARSER}</p>
     */
    CACHE_DURATION_PARSER_IMPL("cache.duration_parser.impl", IDurationParser.DEFAULT_PARSER),

    /**
     * "cache.prod_only.enabled": Turn on/off cache at
     * {@link org.rythmengine.Rythm.Mode#dev dev} mode. When
     * this setting is turned on, then cache will not effect at dev mode
     * <p/>
     * <p>Default value: <code>true</code></p>
     */
    CACHE_PROD_ONLY_ENABLED("cache.prod_only.enabled", true),

    /**
     * "codegen.compact.enabled": Enable/disable compact redundant space and lines
     * <p/>
     * <p>Default value: <code>true</code></p>
     */
    CODEGEN_COMPACT_ENABLED("codegen.compact.enabled", true),

    /**
     * "codegen.source_code_enhancer.impl": Set template
     * {@link org.rythmengine.extension.ISourceCodeEnhancer source code enhancer}
     * implementation.
     * <p>Default value: <code>null</code></p>
     */
    CODEGEN_SOURCE_CODE_ENHANCER("codegen.source_code_enhancer.impl"),

    /**
     * "codegen.byte_code_enhancer.impl": Set template
     * {@link org.rythmengine.extension.IByteCodeEnhancer byte code enhancer} implementation.
     * <p>Default value: <code>null</code></p>
     */
    CODEGEN_BYTE_CODE_ENHANCER("codegen.byte_code_enhancer.impl"),

    /**
     * "default.code_type.impl": Set default {@link org.rythmengine.extension.ICodeType code type}
     * <p/>
     * <p>Default value: {@link org.rythmengine.extension.ICodeType.DefImpl#RAW raw}</p>
     * <p/>
     * TODO: what if {@link #BUILT_IN_CODE_TYPE_ENABLED} is false
     */
    DEFAULT_CODE_TYPE_IMPL("default.code_type.impl", ICodeType.DefImpl.RAW),

    /**
     * "default.cache_ttl": Set default {@link org.rythmengine.extension.ICacheService cache} ttl
     * in second
     * <p/>
     * <p>Default value: 60 * 60(1hr</p>
     */
    DEFAULT_CACHE_TTL("default.cache_ttl") {
        public <T> T getConfiguration(Map<String, ?> configuration) {
            String k = getKey();
            Object v = configuration.get(k);
            if (null == v) {
                return (T) (Number) (60 * 60);
            }
            if (v instanceof Number) {
                return (T) v;
            }
            return (T) (Integer.valueOf(v.toString()));
        }
    },

    /**
     * "engine.mode": Set the {@link org.rythmengine.Rythm.Mode mode} of rythm engine
     * <p>Default value: {@link org.rythmengine.Rythm.Mode#prod}</p>
     */
    ENGINE_MODE("engine.mode") {
        @Override
        public <T> T getConfiguration(Map<String, ?> configuration) {
            String k = getKey();
            Object v = configuration.get(k);
            if (null == v) {
                return (T) Rythm.Mode.prod;
            } else {
                if (v instanceof Rythm.Mode) {
                    return (T) v;
                } else {
                    return (T) Rythm.Mode.valueOf(v.toString());
                }
            }
        }
    },

    /**
     * "engine.id": Set the ID of rythm engine instance
     * <p>Default value: "re-" plus a random String with 3 chars</p>
     */
    ENGINE_ID("engine.id") {
        @Override
        protected Object getDefVal(Map<String, ?> configuration) {
            return "re-" + S.random(3);
        }
    },

    /**
     * "engine.class_loader.parent.impl": Set the {@link ClassLoader#getParent() parent} class loader of the rythm
     * template class loader
     * <p>Default value: first try to use {@link Thread#getContextClassLoader() current thread's context class loader}
     * if the context classloader is <code>null</code>, then use the class loader which loads the <code>Rythm.class</code></p>
     */
    ENGINE_CLASS_LOADER_PARENT_IMPL("engine.class_loader.parent.impl") {
        @Override
        protected Object getDefVal(Map<String, ?> configuration) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (null == cl) {
                cl = Rythm.class.getClassLoader();
            }
            return cl;
        }
    },

    /**
     * "engine.class_loader.byte_code_helper.impl": Set the {@link org.rythmengine.extension.IByteCodeHelper bytecode helper}
     * implementation
     * <p>Default value: <code>null</code></p>
     */
    ENGINE_CLASS_LOADER_BYTE_CODE_HELPER_IMPL("engine.class_loader.byte_code_helper.impl"),

    /**
     * "engine.load_precompiled.enabled": Set the flag so that Rythm will load precompiled template class directly from
     * bytecode cached in the {@link #HOME_PRECOMPILED precompiled root} when running
     * in {@link org.rythmengine.Rythm.Mode#prod prod} mode.
     * <p>Default value: <code>false</code></p>
     */
    ENGINE_LOAD_PRECOMPILED_ENABLED("engine.load_precompiled.enabled", false),

    /**
     * "engine.file_write.enabled": Enable/disable write to file system. This option is used by rythm to check if
     * it should write template class bytecode cache to disk or not. In some cases
     * you want to disable file write due the limit of the runtime environment, e.g.
     * on GAE platform
     * <p/>
     * <p>Default value: <code>true</code></p>
     */
    ENGINE_FILE_WRITE_ENABLED("engine.file_write.enabled", true),

    /**
     * "engine.precompile_mode.enabled": Set/unset precompile mode. This option is used by play-rythm plugin (could also
     * be other plugin) to notify rythm that is is doing a precompile. User application
     * should not use this option
     * <p/>
     * <p>Default value: <code>false</code></p>
     */
    ENGINE_PRECOMPILE_MODE("engine.precompile_mode.enabled") {
        @Override
        public <T> T getConfiguration(Map<String, ?> configuration) {
            String k = getKey();
            Object v = configuration.get(k);
            if (null == v) {
                return (T) Boolean.FALSE;
            } else {
                if (v instanceof Boolean) {
                    return (T) v;
                } else {
                    return (T) Boolean.valueOf(v.toString());
                }
            }
        }
    },
    
    ENGINE_OUTPUT_JAVA_SOURCE_ENABLED("engine.debug_java_source.enabled", false),

    /**
     * "engine.playframework.enabled": A special flag used when Rythm is working with rythm-plugin for Play!Framework. Usually
     * you should not touch this setting.
     * <p/>
     * <p>Default value: <code>false</code></p>
     */
    ENGINE_PLAYFRAMEWORK("engine.playframework.enabled", false),

    /**
     * "engine.plugin.version": Set by plugin of certain framework, e.g. play!framework. Used to determine
     * whether it needs to refresh the cached template class bytecode. Default value: <code>""</code> (empty string)
     */
    ENGINE_PLUGIN_VERSION("engine.plugin.version", ""),

    /**
     * "feature.transform.enabled": Enable disable {@link org.rythmengine.extension.Transformer transformer}
     * <p/>
     * <p>Default value: <code>true</code></p>
     */
    FEATURE_TRANSFORM_ENABLED("feature.transform.enabled", true),

    /**
     * "feature.type_inference.enabled": Enable disable type inference. TODO add link to type inference reference page
     * <p/>
     * <p>Default value: <code>false</code></p>
     */
    FEATURE_TYPE_INFERENCE_ENABLED("feature.type_inference.enabled", false),

    /**
     * "feature.smart_escape.enabled": Enable disable smart escape. TODO: add link to smart escape reference page
     * <p/>
     * <p>Default value: <code>true</code></p>
     */
    FEATURE_SMART_ESCAPE_ENABLED("feature.smart_escape.enabled", true),

    /**
     * "feature.natural_template.enabled": Enable disable natural template. TODO: add reference link to natural template
     * <p/>
     * <p>Default value: <code>true</code></p>
     */
    FEATURE_NATURAL_TEMPLATE_ENABLED("feature.natural_template.enabled", false),

    /**
     * "home.template.dir": Set the home dir of template files. This configuration is used when the {@link #RESOURCE_LOADER_IMPL}
     * is not configured, therefore the {@link org.rythmengine.resource.TemplateResourceManager} will
     * try to load {@link org.rythmengine.resource.FileTemplateResource} from this template home dir
     * configured.
     * <p/>
     * <p>Default value: a file created with the following logic</p>
     * <p/>
     * <pre><code>new File(Thread.currentThread().getContextClassLoader().getResource("rythm").getFile())</code></pre>
     */
    HOME_TEMPLATE("home.template.dir") {
        @Override
        protected Object getDefVal(Map<String, ?> configuration) {
            URL url = Thread.currentThread().getContextClassLoader().getResource("rythm");
            if (null != url) return new File(url.getPath());
            return new File("rythm");
        }
    },

    /**
     * "home.tmp.dir": Set the rythm tmp dir. The tmp dir is to where Rythm write compiled template class bytecode
     * when running in the {@link org.rythmengine.Rythm.Mode#dev dev} mode.
     * <p/>
     * <p>Default value: a file created with the following logic</p>
     * <p/>
     * <pre><code>new File(System.__getProperty("java.io.tmpdir"), "__rythm")</code></pre>
     */
    HOME_TMP("home.tmp.dir") {
        @Override
        protected Object getDefVal(Map<String, ?> configuration) {
            return new File(System.getProperty("java.io.tmpdir"), "__rythm");
        }
    },

    /**
     * "home.precompiled.dir": Set the dir root of the precompiled template bytecodes. Default value: <code>null</code>
     *
     * @see #ENGINE_LOAD_PRECOMPILED_ENABLED
     */
    HOME_PRECOMPILED("home.precompiled.dir") {
        @Override
        protected Object getDefVal(Map<String, ?> configuration) {
            return null;
        }
    },

    /**
     * "i18n.locale": the locale for the rythm runtime environment. This configuration
     * return the {@link java.util.Locale} type of instance.
     * 
     * <p>Default value: <code>java.util.Locale.getDefault()</code></p>
     * 
     * @see <a href="http://docs.oracle.com/javase/7/docs/api/java/util/Locale.html">Java Locale</a>
     */
    I18N_LOCALE("i18n.locale") {
        @Override
        public <T> T getConfiguration(Map<String, ?> configuration) {
            String k = getKey();
            Object o = configuration.get(k);
            if (o instanceof Locale) {
                return (T)o;
            }
            // check lang_REGION style
            String s = S.str(o);
            if (S.empty(s)) {
                return (T)Locale.getDefault();
            }
            Locale retval;
            String[] sa = s.split("_");
            String lang = sa[0];
            if (sa.length > 1) {
                String region = sa[1];
                retval = new Locale(lang, region);
            } else {
                retval = new Locale(lang);
            }
            return (T)retval;
        }
    },

    /**
     * "i18n.message.sources": Set message sources. Should be a String of message (resource bundle) properties 
     * file names separated by ",", E.g. "format,exception,windows".
     * <p>Default value: <code>message</code></p>
     * 
     * @see <a href="http://static.springsource.org/spring/docs/3.0.x/spring-framework-reference/html/beans.html#context-functionality-messagesource">[Spring]Internationalization using MessageSource</a>
     */
    I18N_MESSAGE_SOURCES("i18n.message.sources", "messages"),

    /**
     * "i18n.message.resolver.impl": Set i18n message resolver. Should implement {@link org.rythmengine.extension.II18nMessageResolver}
     * interface. Default value: {@link org.rythmengine.extension.II18nMessageResolver.DefaultImpl#INSTANCE}, which delegate
     * to {@link org.rythmengine.utils.S#i18n(org.rythmengine.template.ITemplate, String, Object...)} method
     */
    I18N_MESSAGE_RESOLVER("i18n.message.resolver.impl", II18nMessageResolver.DefaultImpl.INSTANCE),

    /**
     * "log.enabled": Enable disable log in Rythm. Default value: true
     */
    LOG_ENABLED("log.enabled", true),

    /**
     * "log.factory.impl": Configure the {@link org.rythmengine.extension.ILoggerFactory logger factory} implementation.
     * When this configuration is not set, then a {@link org.rythmengine.logger.JDKLogger.Factory} instance
     * is used to create the logger
     * <p/>
     * <p>Default value: <code>org.rythmengine.logger.JDKLogger.Factory</code></p>
     */
    LOG_FACTORY_IMPL("log.factory.impl", JDKLogger.Factory.class),

    /**
     * "log.source.java.enabled": Print out relevant java source lines when exception encountered
     * <p/>
     * <p>Default value: <code>true</code></p>
     */
    LOG_SOURCE_JAVA_ENABLED("log.source.java.enabled", true),

    /**
     * "log.source.template.enabled": Print out relevant template source lines when exception encountered
     * <p/>
     * <p>Default value: <code>true</code></p>
     */
    LOG_SOURCE_TEMPLATE_ENABLED("log.source.template.enabled", true),

    /**
     * "log.time.render.enabled": Log time spent executing a template. The level used to log the time logRenderTime
     * is {@link org.rythmengine.logger.ILogger#debug(String, Object...)}
     * <p/>
     * <p>Default value: <code>false</code></p>
     */
    LOG_TIME_RENDER_ENABLED("log.time.render.enabled", false),

    /**
     * "render.listener.impl": Set {@link org.rythmengine.extension.IRythmListener tag
     * invocation listener} implementation.
     * <p>Default value: <code>null</code></p>
     */
    RENDER_LISTENER("render.listener.impl"),

    /**
     * "render.exception_handler.impl": Set {@link org.rythmengine.extension.IRenderExceptionHandler
     * render exception handler} implementation.
     * <p>Default value: <code>null</code></p>
     */
    RENDER_EXCEPTION_HANDLER("render.exception_handler.impl"),

    /**
     * "resource.loader.impl": The {@link org.rythmengine.extension.ITemplateResourceLoader resource loader}
     * implementation
     * <p>Default value: <code>null</code>. But if this is not configured, try templates will be loaded as
     * {@link org.rythmengine.resource.FileTemplateResource file template resource} first and if
     * still not found then try to load as
     * {@link org.rythmengine.resource.ClasspathTemplateResource classpath resource}.</p>
     *
     * @see #HOME_TEMPLATE
     */
    RESOURCE_LOADER_IMPL("resource.loader.impl"),

    /**
     * "resource.name.suffix": does resource name has special rythm suffix attached? 
     * E.g. <tt>.rythm</tt> or <tt>.rtl</tt> etc. Default is empty string 
     * 
     * <p>Note, do not use regular file extensions for this setting, like ".html", ".js" etc
     * as they can be used to identify the code type of the template</p>
     */
    RESOURCE_NAME_SUFFIX("resource.name.suffix", "") {
        @Override
        public <T> T getConfiguration(Map<String, ?> configuration) {
            String s = super.getConfiguration(configuration);
            if (S.empty(s)) return (T)"";
            if (!s.startsWith(".")) s = "." + s;
            return (T)s;
        }
    },

    /**
     * "sandbox.security_manager.impl": Set the security manager to be used when running a template in
     * {@link org.rythmengine.Sandbox sandbox} mode.
     * <p>Default value: <code>null</code>. When no security manager is configured, when the sandbox mode is running, an
     * instance of {@link org.rythmengine.sandbox.RythmSecurityManager} will be initiated to supervise the
     * execution. Usually you should NOT set this configuration and allow Rythm to run it's <code>SecurityManager</code>
     * implementation.</p>
     */
    SANDBOX_SECURITY_MANAGER_IMPL("sandbox.security_manager.impl"),

    /**
     * "sandbox.timeout": Set the timeout of a {@link org.rythmengine.Sandbox sandbox} execution in milliseconds.
     * If the execution failed to return after timeout, then Rythm will interrupt the execution thread and force it
     * to return. This setting prevent infinite loop in untrusted template.
     * <p>Default value: 1000</p>
     */
    SANDBOX_TIMEOUT("sandbox.timeout") {
        @Override
        public <T> T getConfiguration(Map<String, ?> configuration) {
            String k = getKey();
            Object v = configuration.get(k);
            if (null == v) {
                return (T) (Integer) 2000;
            }
            if (v instanceof Number) {
                return (T) v;
            }
            return (T) Integer.valueOf(v.toString());
        }
    },

    /**
     * "sandbox.pool.size": Set the thread pool size of {@link org.rythmengine.Sandbox sandbox} executors.
     * <p>Default value: 10</p>
     */
    SANDBOX_POOL_SIZE("sandbox.pool.size") {
        @Override
        public <T> T getConfiguration(Map<String, ?> configuration) {
            String k = getKey();
            Object v = configuration.get(k);
            if (null == v) {
                return (T) (Integer) 10;
            }
            if (v instanceof Number) {
                return (T) v;
            }
            return (T) Integer.valueOf(v.toString());
        }
    },

    /**
     * "sandbox.restricted_class": Set restricted classes for {@link org.rythmengine.Sandbox sandbox} execution.
     * The value should be full name of the classes or packages separated by <code>;</code>. For example,
     * "foo.bar.Employee;foo.secure;...".
     * <p>
     * If a class or package name is presented in this setting, then the sandbox executor will raise a
     * {@link SecurityException} when the template trying to access the class. Note whatever this setting is
     * configured, Rythm will prevent the access to the following classes/packages:
     * </p>
     * <ul>
     * <li><code>org.rythmengine.Rythm;</code></li>
     * <li><code>org.rythmengine.RythmEngine;</code></li>
     * <li><code>java.io;</code></li>
     * <li><code>java.nio;</code></li>
     * <li><code>java.security;</code></li>
     * <li><code>java.rmi;</code></li>
     * <li><code>java.net;</code></li>
     * <li><code>java.awt;</code></li>
     * <li><code>java.applet</code></li>
     * </ul>
     * <code>Default value: <code>""</code></code>
     */
    SANDBOX_RESTRICTED_CLASS("sandbox.restricted_class", ""),

    /**
     * "sandbox.allowed_system_properties": Set allowed system properties in string separated by <tt>,</tt>. 
     * 
     * By default the following properties are allowed to access by sandbox thread
     * <ul>
     * <li><code>user.dir</code></li>
     * <li><code>line.separator</code></li>
     * <li><code>java.vm.name</code></li>
     * <li><code>java.protocol.handler.pkgs</code></li>
     * </ul>
     */
    SANDBOX_ALLOWED_SYSTEM_PROPERTIES("sandbox.allowed_system_properties", 
        "java.io.tmpdir,file.encoding,user.dir,line.separator,java.vm.name,java.protocol.handler.pkgs,suppressRawWhenUnchecked"),

    /**
     * "sandbox.thread_factory.impl": Configure the thread factory to be used by the sandbox executing service.
     * <p>Note this configuration should be very rare used as it is create to support rythmfiddle implementation.
     * Should you really need to configure this item, make sure it is configured as an instance of {@link org.rythmengine.sandbox.SandboxThreadFactory}</p>
     * <p>Default value: <code>null</code></p>
     */
    SANBOX_THREAD_FACTORY_IMPL("sandbox.thread_factory.impl", null),

    /**
     * "transformer.udt": User defined transformers, should be a list of class names separated by ",". If configured
     * then {@link org.rythmengine.RythmEngine#registerTransformer(Class[]) RythmEngine.registerTransformer} will
     * be called to register these user defined transformer classes. Default value: <code>null</code>
     */
    TRANSFORMER_UDT("transformer.udt");

    private String key;
    private Object defVal;

    private RythmConfigurationKey(String key) {
        this(key, null);
    }

    private RythmConfigurationKey(String key, Object defVal) {
        this.key = key;
        this.defVal = defVal;
    }

    /**
     * Return the key string
     *
     * @return the key of the configuration
     */
    public String getKey() {
        return key;
    }

    /**
     * Return default value of this setting. The configuration data map
     * is passed in in case the default value be variable depending on
     * another setting. For example, the default value of {@link #HOME_TMP tmp dir}
     * setting depend on the value of {@link #ENGINE_MODE mode} setting
     *
     * @param configuration
     * @return return the default value
     */
    protected Object getDefVal(Map<String, ?> configuration) {
        return defVal;
    }

    /**
     * Calling to this method is equals to calling {@link #getKey()}
     *
     * @return key of the configuration
     */
    @Override
    public String toString() {
        return key;
    }

    private static List<String> aliases(String key, String suffix) {
        List<String> l = new ArrayList<String>();
        l.add("rythm." + key);
        l.add(key);
        if (S.notEmpty(suffix)) {
            String k0 = key.replace("." + suffix, "");
            l.add("rythm." + k0);
            l.add(k0);
        }
        return l;
    }
    
    private Object getValFromAliases(Map<String, ?> configuration, String key, String suffix) {
        Object v = configuration.get(key);
        if (null == v) {
            for (String k0 : aliases(key, suffix)) {
                v = configuration.get(k0);
                if (null != v) break;
            }
            if (null == v) {
                // still not found, load default value
                v = getDefVal(configuration);
            }
        }
        return v;
    }

    private static boolean toBoolean(Object v) {
        if (null == v) return false;
        if (v instanceof Boolean) return (Boolean)v;
        return Boolean.parseBoolean(v.toString());
    } 

    private Boolean getEnabled(String key, Map<String, ?> configuration) {
        Object v = getValFromAliases(configuration, key, "enabled");
        if (null == v) {
            v = getValFromAliases(configuration, key, "disabled");
            return !toBoolean(v);
        }
        return toBoolean(v);
    }
    
    private <T> T getImpl(String key, Map<String, ?> configuration) {
        Object v = getValFromAliases(configuration, key, "impl");
        if (null == v) return null;
        if (v instanceof Class) {
            try {
                return (T) ((Class) v).newInstance();
            } catch (Exception e) {
                throw new ConfigurationException(e, "Error getting implementation configuration: %s", key);
            }
        }
        if (!(v instanceof String)) return (T)v;
        String clsName = (String)v;
        try {
            return (T) Class.forName(clsName).newInstance();
        } catch (Exception e) {
            // try to evaluate the string
            try {
                Object o = _Rythm.eval(clsName);
                if (o instanceof Class) {
                    return (T) ((Class) o).newInstance();
                } else {
                    return (T) o;
                }
            } catch (Exception e1) {
                throw new ConfigurationException(e, "Error getting implementation configuration: %s", key);
            }
        }
    }

    private File getFile(String key, Map<String, ?> configuration) {
        Object v = getValFromAliases(configuration, key, "dir");
        if (null == v) return null;
        if (v instanceof File) {
            return (File) v;
        }
        String s = v.toString();
        boolean isAbsolute = false;
        if (s.startsWith("/") || s.startsWith(File.separator)) {
            isAbsolute = true;
        } else if (s.matches("^[a-zA-Z]:.*")) {
            isAbsolute = true;
        }
        if (isAbsolute) return new File(s);
        try {
            URL url = Thread.currentThread().getContextClassLoader().getResource(s);
            return new File(url.getPath());
        } catch (Exception e) {
            throw new ConfigurationException(e, "Error reading file configuration %s", key);
        }
    }

    /**
     * Return configuration value from the configuration data map using the {@link #key}
     * of this {@link RythmConfigurationKey setting} instance
     *
     * @param configuration
     * @param <T>
     * @return return the configuration
     */
    public <T> T getConfiguration(Map<String, ?> configuration) {
        String key = this.key;
        if (key.endsWith(".enabled")) {
            return (T) getEnabled(key, configuration);
        }
        if (key.endsWith(".impl")) {
            return getImpl(key, configuration);
        }
        if (key.endsWith(".dir")) {
            return (T) getFile(key, configuration);
        }
        return (T) getValFromAliases(configuration, key, null);
    }

    /**
     * Return default configuration of this item
     * 
     * @param <T>
     * @return default configuration for this item
     */
    public <T> T getDefaultConfiguration() {
        return (T)getConfiguration((Map)Collections.emptyMap());
    }

    private static Map<String, RythmConfigurationKey> lookup = new HashMap<String, RythmConfigurationKey>(50); static {
        for (RythmConfigurationKey k : values()) {
            lookup.put(k.getKey().toLowerCase(), k);
        }
    }

    /**
     * Return key enum instance from the string in case insensitive mode
     *
     * @param s
     * @return configuration key from the string
     */
    public static RythmConfigurationKey valueOfIgnoreCase(String s) {
        if (S.empty(s)) throw new IllegalArgumentException();
        return lookup.get(s.trim().toLowerCase());
    }

}
