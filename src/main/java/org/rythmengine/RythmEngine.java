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
package org.rythmengine;

import org.rythmengine.extension.ICacheService;
import org.rythmengine.conf.RythmConfiguration;
import org.rythmengine.conf.RythmConfigurationKey;
import org.rythmengine.exception.RythmException;
import org.rythmengine.exception.TagLoadException;
import org.rythmengine.extension.ICodeType;
import org.rythmengine.extension.IDurationParser;
import org.rythmengine.extension.Transformer;
import org.rythmengine.internal.*;
import org.rythmengine.internal.compiler.*;
import org.rythmengine.internal.dialect.AutoToString;
import org.rythmengine.internal.dialect.BasicRythm;
import org.rythmengine.internal.dialect.DialectManager;
import org.rythmengine.internal.dialect.ToString;
import org.rythmengine.logger.ILogger;
import org.rythmengine.extension.ILoggerFactory;
import org.rythmengine.logger.Logger;
import org.rythmengine.logger.NullLogger;
import org.rythmengine.resource.ITemplateResource;
import org.rythmengine.resource.StringTemplateResource;
import org.rythmengine.resource.TemplateResourceManager;
import org.rythmengine.resource.ToStringTemplateResource;
import org.rythmengine.sandbox.SandboxExecutingService;
import org.rythmengine.sandbox.SandboxThreadFactory;
import org.rythmengine.template.ITag;
import org.rythmengine.template.ITemplate;
import org.rythmengine.template.JavaTagBase;
import org.rythmengine.template.TemplateBase;
import org.rythmengine.toString.ToStringOption;
import org.rythmengine.toString.ToStringStyle;
import org.rythmengine.utils.F;
import org.rythmengine.utils.IO;
import org.rythmengine.utils.JSONWrapper;
import org.rythmengine.utils.S;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <p>Not Thread Safe</p>
 * 
 * <p>A Rythm Template Engine is the entry to the Rythm templating system. It provides a set of
 * APIs to render template. Each JVM allows multiple <code>RythmEngine</code> instance, with each
 * one represent a set of configurations.</p> 
 * 
 * <p>The {@link Rythm} facade contains a default <code>RythmEngine</code> instance to make it
 * easy to use for most cases</p>
 */
public class RythmEngine implements IEventDispatcher {
    private static final ILogger logger = Logger.get(RythmEngine.class);

    /**
     * Rythm Engine Version. Used along with
     * {@link org.rythmengine.conf.RythmConfigurationKey#ENGINE_PLUGIN_VERSION plugin version} to
     * check if the cached template bytecode need to be refreshed or not
     * <p/>
     */
    private static final String version;
    static {
        version = IO.readContentAsString(RythmEngine.class.getClassLoader().getResourceAsStream("rythm-engine-version"));
    }

    private static final InheritableThreadLocal<RythmEngine> _engine = new InheritableThreadLocal<RythmEngine>();

    /**
     * Set the engine instance to a {@link ThreadLocal} variable, thus it is easy to
     * {@link #get() get} the current
     * <code>RythmEngine</code> dominating the rendering process.
     * <p/>
     * <p><b>Note</b>, this method is NOT an API to be called by user application</p>
     *
     * @param engine
     */
    public static void set(RythmEngine engine) {
        _engine.set(engine);
    }

    /**
     * Get the current engine instance from a {@link ThreadLocal} variable which is
     * {@link #set(RythmEngine) set} previously.
     * <p/>
     * <p><b>Note</b>, this method is NOT an API to be called by user application</p>
     *
     * @return the engine
     */
    public static RythmEngine get() {
        return _engine.get();
    }


    /**
     * Check if the current rendering is dominated by a {@link Sandbox}
     * <p/>
     * <p><b>Note</b>, this method is NOT an API to be called by user application</p>
     *
     * @return true if the current thread is running in Sandbox mode
     */
    public static boolean insideSandbox() {
        return Sandbox.sandboxMode();
    }

    @Override
    public String toString() {
        return null == _conf ? "rythm-engine-uninitialized" : id();
    }
    
    /* -----------------------------------------------------------------------------
      Fields and Accessors
    -------------------------------------------------------------------------------*/

    private RythmConfiguration _conf = null;

    /**
     * Return {@link RythmConfiguration configuration} of the engine
     * <p/>
     * <p>Usually user application should not call this method</p>
     *
     * @return rythm configuration
     */
    public RythmConfiguration conf() {
        if (null == _conf) {
            throw new IllegalStateException("Rythm engine not initialized");
        }
        return _conf;
    }

    /**
     * Return Version string of the engine instance. The version string
     * is composed by {@link #version Rythm version} and the
     * configured {@link RythmConfigurationKey#ENGINE_PLUGIN_VERSION plugin version}. The version
     * string will be used by Rythm to see if compiled bytecodes cached on disk should
     * be refreshed in an new version or not.
     * <p/>
     * <p><code>Note</code>, this method is not generally used by user application</p>
     *
     * @return engine version along with plugin version
     */
    public String version() {
        return version + "-" + conf().pluginVersion();
    }

    private Rythm.Mode _mode = null;

    /**
     * Return the engine {@link Rythm.Mode mode}
     *
     * @return engine running mode
     */
    public Rythm.Mode mode() {
        if (null == _mode) {
            _mode = conf().get(RythmConfigurationKey.ENGINE_MODE);
        }
        return _mode;
    }

    private String _id = null;
    
    /**
     * Return the instance {@link org.rythmengine.conf.RythmConfigurationKey#ENGINE_ID}
     * 
     * @return the id
     */
    public String id() {
        if (null == _id) {
            _id = conf().get(RythmConfigurationKey.ENGINE_ID);
        }
        return _id;
    }

    /**
     * Is this engine the default {@link Rythm#engine} instance?
     * <p/>
     * <p><b>Note</b>, not to be used by user application</p>
     *
     * @return true if this is the default engine
     */
    public boolean isSingleton() {
        return Rythm.engine == this;
    }

    /**
     * Is the engine running in {@link Rythm.Mode#prod product} mode?
     *
     * @return true if engine is running in prod mode
     */
    public boolean isProdMode() {
        return mode() == Rythm.Mode.prod;
    }

    /**
     * Is the engine running in {@link Rythm.Mode#dev development} mode?
     *
     * @return true if engine is running is debug mode
     */
    public boolean isDevMode() {
        return mode() != Rythm.Mode.prod;
    }

    private TemplateResourceManager _resourceManager;

    /**
     * Get {@link TemplateResourceManager resource manager} of the engine
     * <p/>
     * <p><b>Note</b>, this method should not be used by user application</p>
     *
     * @return resource manager
     */
    public TemplateResourceManager resourceManager() {
        return _resourceManager;
    }

    private TemplateClassManager _classes;

    /**
     * Get {@link TemplateClassManager template class manager} of the engine
     * <p/>
     * <p><b>Note</b>, this method should not be used by user application</p>
     *
     * @return template class manager
     */
    public TemplateClassManager classes() {
        return _classes;
    }

    private TemplateClassLoader _classLoader = null;

    /**
     * Get {@link TemplateClassLoader class loader} of the engine
     * <p/>
     * <p><b>Note</b>, this method should not be used by user application</p>
     *
     * @return template class loader
     */
    public TemplateClassLoader classLoader() {
        return _classLoader;
    }

    private TemplateClassCache _classCache = null;

    /**
     * Get {@link TemplateClassCache class cache} of the engine
     * <p/>
     * <p><b>Note</b>, this method should not be used by user application</p>
     *
     * @return template class cache
     */
    public TemplateClassCache classCache() {
        return _classCache;
    }

    private ExtensionManager _extensionManager;

    /**
     * Return {@link ExtensionManager} of this engine
     *
     * @return extension manager
     */
    public ExtensionManager extensionManager() {
        return _extensionManager;
    }

    private final DialectManager _dialectManager = new DialectManager();

    /**
     * Not an API
     * @return {@link DialectManager} instance
     */
    public DialectManager dialectManager() {
        return _dialectManager;
    }

    private ICacheService _cacheService = null;

    /**
     * Define the render time settings, which is intialized each time a renderXX method
     * get called
     */
    public class RenderSettings {
        private final ThreadLocal<Locale> _locale = new ThreadLocal<Locale>() {
            @Override
            protected Locale initialValue() {
                return RythmEngine.this.conf().locale();
            }
        };
        private final ThreadLocal<ICodeType> _codeType = new ThreadLocal<ICodeType>() {
            @Override
            protected ICodeType initialValue() {
                //return RythmEngine.this.conf().defaultCodeType();
                return null; // there are logic in TemplateClass.asTemplate() to further deduct code type from resource 
            }
        };
        private final ThreadLocal<Map<String, Object>> _usrCtx = new ThreadLocal<Map<String, Object>>();

        /**
         * Init the render time by setting {@link org.rythmengine.extension.ICodeType code type}. 
         * This method should called before calling render methods to setup the context of this render 
         * 
         * @param codeType
         * @return the render setting instance
         */
        public final RenderSettings init(ICodeType codeType) {
            if (null != codeType) _codeType.set(codeType);
            else _codeType.remove();
            return this;
        }

        /**
         * Init the render time by setting {@link java.util.Locale locale}. 
         * This method should called before calling render methods to setup the context of this render 
         * 
         * @param locale
         * @return the render setting instance
         */
        public final RenderSettings init(Locale locale) {
            if (null != locale) _locale.set(locale);
            else _locale.remove();
            return this;
        }

        /**
         * Init the render time by setting user context 
         * This method should called before calling render methods to setup the context of this render 
         * 
         * @param usrCtx
         * @return the render setting instance
         */
        public final RenderSettings init(Map<String, Object> usrCtx) {
            if (null != usrCtx) _usrCtx.set(usrCtx);
            else _usrCtx.remove();
            return this;
        }
        
        /**
         * Return ThreadLocal locale.
         * 
         * @return locale setting for this render process
         */
        public final Locale locale() {
            return _locale.get();
        }

        /**
         * Return thread local code type
         * 
         * @return {@link org.rythmengine.extension.ICodeType code type} setting for this render process
         */
        public final ICodeType codeType() {
            return _codeType.get();
        }

        /**
         * Return thread local user context map
         * 
         * @return the user context map
         */
        public final Map<String, Object> userContext() {
            return _usrCtx.get();
        }
    
        /**
         * Clear the render time after render process done
         * @return this engine instance
         */
        public final RythmEngine clear() {
            _locale.remove();
            _codeType.remove();
            _usrCtx.remove();
            return RythmEngine.this;
        }
    
    }

    /**
     * The RenderSettings instance keep the environment settings for one render operation
     */
    public final RenderSettings renderSettings = new RenderSettings();

    /**
     * Prepare the render operation environment settings
     * 
     * @param codeType
     * @param locale
     * @param usrCtx
     * @return the engine instance itself
     */
    public final RythmEngine prepare(ICodeType codeType, Locale locale, Map<String, Object> usrCtx) {
        renderSettings.init(codeType).init(locale).init(usrCtx);
        return this;
    }
    
    /**
     * Prepare the render operation environment settings
     * 
     * @param codeType
     * @return the engine instance itself
     */
    public final RythmEngine prepare(ICodeType codeType) {
        renderSettings.init(codeType);
        return this;
    }

    /**
     * Prepare the render operation environment settings
     * 
     * @param locale
     * @return the engine instance itself
     */
    public final RythmEngine prepare(Locale locale) {
        renderSettings.init(locale);
        return this;
    }
    
    /**
     * Prepare the render operation environment settings
     * 
     * @param userContext
     * @return the engine instance itself
     */
    public final RythmEngine prepare(Map<String, Object> userContext) {
        renderSettings.init(userContext);
        return this;
    }

    /* -----------------------------------------------------------------------------
      Constructors, Configuration and Initializing
    -------------------------------------------------------------------------------*/

    private void _initLogger(Map<String, ?> conf) {
        boolean logEnabled = (Boolean)RythmConfigurationKey.LOG_ENABLED.getConfiguration(conf);
        if (logEnabled) {
            ILoggerFactory factory = RythmConfigurationKey.LOG_FACTORY_IMPL.getConfiguration(conf);
            Logger.registerLoggerFactory(factory);
        } else {
            Logger.registerLoggerFactory(new NullLogger.Factory());
        }
    }
    
    // trim "rythm." from conf keys
    private Map<String, Object> _processConf(Map<String, ?> conf) {
        Map<String, Object> m = new HashMap<String, Object>(conf.size());
        for (String s : conf.keySet()) {
            Object o = conf.get(s);
            if (s.startsWith("rythm.")) s = s.replaceFirst("rythm\\.", "");
            m.put(s, o);
        }
        return m;
    }

    private void _initConf(Map<String, ?> conf, File confFile) {
        // load conf from disk
        Map<String, ?> rawConf = _loadConfFromDisk(confFile);
        rawConf = _processConf(rawConf);

        // load conf from System.properties
        Properties sysProps = System.getProperties();
        rawConf.putAll((Map) sysProps);

        // load conf from user supplied configuration
        if (null != conf) rawConf.putAll((Map)_processConf(conf));
        
        _initLogger(rawConf);

        // initialize the configuration with all loaded data 
        this._conf = new RythmConfiguration(rawConf);
        
        // initialize logger factory
        ILoggerFactory logFact = this._conf.get(RythmConfigurationKey.LOG_FACTORY_IMPL);
        Logger.registerLoggerFactory(logFact);
        
        // check if it needs to debug the conf information
        if (rawConf.containsKey("rythm.debug_conf") && Boolean.parseBoolean(rawConf.get("rythm.debug_conf").toString())) {
            this._conf.debug();
        }
    }

    /**
     * Create a rythm engine instance with default configuration
     *
     * @see org.rythmengine.conf.RythmConfigurationKey
     */
    public RythmEngine() {
        init(null, null);
    }

    /**
     * Create a rythm engine instance with either template root or configuration file specified
     *
     * @param file if is directory then the template root, otherwise then the configuration file
     * @see org.rythmengine.conf.RythmConfigurationKey
     */
    public RythmEngine(File file) {
        init(null, file);
    }

    public RythmEngine(Properties userConfiguration) {
        this((Map) userConfiguration);
    }

    /**
     * Create a rythm engine instance with user supplied configuration data
     *
     * @param userConfiguration
     * @see org.rythmengine.conf.RythmConfigurationKey
     */
    public RythmEngine(Map<String, ?> userConfiguration) {
        init(userConfiguration, null);
    }

    private Map<String, Object> properties = new HashMap<String, Object>();

    /**
     * Set user property to the engine. Note The property is not used by the engine program
     * it's purely a place for user to add any tags or properties to the engine and later
     * get fetched out and use in the user application
     * 
     * @param key
     * @param val
     */
    public void setProperty(String key, Object val) {
        properties.put(key, val);
    }

    /**
     * Get user property by key
     * @param key
     * @param <T>
     * @return the property being cast to type T
     */
    public <T> T getProperty(String key) {
        return (T) properties.get(key);
    }

    private Map _loadConfFromDisk(File conf) {
        InputStream is = null;
        boolean emptyConf = false;
        if (null == conf) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (null == cl) cl = Rythm.class.getClassLoader();
            is = cl.getResourceAsStream("rythm.conf");
        } else {
            try {
                is = new FileInputStream(conf);
            } catch (IOException e) {
                if (!emptyConf) logger.warn(e, "Error opening conf file:" + conf);
            }
        }
        if (null != is) {
            Properties p = new Properties();
            try {
                p.load(is);
                return p;
            } catch (Exception e) {
                logger.warn(e, "Error loading rythm.conf");
            } finally {
                try {
                    if (null != is) is.close();
                } catch (Exception e) {
                    //ignore
                }
            }
        }
        return new HashMap();
    }

    private void init(Map<String, ?> conf, File file) {
        if (null == file || file.isDirectory()) {
            _initConf(conf, null);
            if (null != file) _conf.setTemplateHome(file);
        } else if (file.isFile() && file.canRead()) {
            _initConf(conf, file);
        }

        // post configuration initializations 
        _mode = _conf.get(RythmConfigurationKey.ENGINE_MODE);
        _classes = new TemplateClassManager(this);
        _classLoader = new TemplateClassLoader(this);
        _classCache = new TemplateClassCache(this);
        _resourceManager = new TemplateResourceManager(this);
        _extensionManager = new ExtensionManager(this);
        int ttl = (Integer) _conf.get(RythmConfigurationKey.DEFAULT_CACHE_TTL);
        _cacheService = _conf.get(RythmConfigurationKey.CACHE_SERVICE_IMPL);
        _cacheService.setDefaultTTL(ttl);
        _cacheService.startup();


        // register built-in transformers if enabled
        boolean enableBuiltInJavaExtensions = (Boolean) _conf.get(RythmConfigurationKey.BUILT_IN_TRANSFORMER_ENABLED);
        if (enableBuiltInJavaExtensions) {
            registerTransformer("rythm", "(org.rythmengine.utils.S|s\\(\\))", S.class);
        }

        boolean enableBuiltInTemplateLang = (Boolean) _conf.get(RythmConfigurationKey.BUILT_IN_CODE_TYPE_ENABLED);
        if (enableBuiltInTemplateLang) {
            ExtensionManager em = extensionManager();
            em.registerCodeType(ICodeType.DefImpl.HTML);
            em.registerCodeType(ICodeType.DefImpl.JS);
            em.registerCodeType(ICodeType.DefImpl.JSON);
            em.registerCodeType(ICodeType.DefImpl.CSV);
            em.registerCodeType(ICodeType.DefImpl.CSS);
        }

        _templates.clear();
        _templates.put("chain", new JavaTagBase() {
            @Override
            protected void call(__ParameterList params, __Body body) {
                body.render(__getBuffer());
            }
        });
        
        Object o = _conf.get(RythmConfigurationKey.TRANSFORMER_UDT);
        if (null != o) {
            List<Class> udts = new ArrayList<Class>();
            if (o.getClass().isArray()) {
                int len = Array.getLength(o);
                for (int i = 0; i < len; ++i) {
                    Object e = Array.get(o, i);
                    if (e instanceof Class) {
                        udts.add((Class)e);
                    } else if (null != e) {
                        String s = e.toString();
                        try {
                            udts.add(Class.forName(s));
                        } catch (ClassNotFoundException ce) {
                            logger.warn("User defined transformer class not found: %s", s);
                        }
                    }
                }
            } else if (o instanceof List) {
                List l = (List) o;
                for (Object e : l) {
                    if (e instanceof Class) {
                        udts.add((Class)e);
                    } else if (null != e) {
                        String s = e.toString();
                        try {
                            udts.add(Class.forName(s));
                        } catch (ClassNotFoundException ce) {
                            logger.warn("User defined transformer class not found: %s", s);
                        }
                    }
                }
            } else if (o instanceof Class) {
                udts.add((Class)o);
            } else {
                String s = o.toString();
                for (String tc: s.split("[, \t]+")){
                    try {
                        Class c = Class.forName(tc);
                        udts.add(c);
                    } catch (ClassNotFoundException e) {
                        logger.warn("User defined transformer class not found: %s", tc);
                    }
                }
            }
            registerTransformer(udts.toArray(new Class[]{}));
        }
        
        if (isDevMode()) {
            resourceManager().scan(conf().templateHome());
        }

        logger.debug("Rythm-%s started in %s mode", version, mode());
    }

    /* -----------------------------------------------------------------------------
      Registrations
    -------------------------------------------------------------------------------*/

    /**
     * Register {@link Transformer transformers} using namespace specified in
     * the namespace {@link org.rythmengine.extension.Transformer#value() value} defined in
     * the annotation.
     *
     * @param transformerClasses
     */
    public void registerTransformer(Class<?>... transformerClasses) {
        registerTransformer(null, null, transformerClasses);
    }

    /**
     * Register {@link Transformer transformers} using namespace specified to
     * replace the namespace {@link org.rythmengine.extension.Transformer#value() value} defined in
     * the annotation.
     *
     * @param transformerClasses
     */
    public void registerTransformer(String namespace, String waivePattern, Class<?>... transformerClasses) {
        ExtensionManager jem = extensionManager();
        for (Class<?> extensionClass : transformerClasses) {
            Transformer t = extensionClass.getAnnotation(Transformer.class);
            boolean classAnnotated = null != t;
            String nmsp = namespace;
            boolean namespaceIsEmpty = S.empty(namespace);
            if (classAnnotated && namespaceIsEmpty) {
                nmsp = t.value();
            }
            String waive = waivePattern;
            boolean waiveIsEmpty = S.empty(waive);
            if (classAnnotated && waiveIsEmpty) {
                waive = t.waivePattern();
            }
            boolean clsRequireTemplate = null == t ? false : t.requireTemplate();
            for (Method m : extensionClass.getDeclaredMethods()) {
                int flag = m.getModifiers();
                if (!Modifier.isPublic(flag) || !Modifier.isStatic(flag)) continue;
                int len = m.getParameterTypes().length;
                if (len <= 0) continue;

                Transformer tm = m.getAnnotation(Transformer.class);
                boolean methodAnnotated = null != tm;
                if (!methodAnnotated && !classAnnotated) continue;
                
                String mnmsp = nmsp;
                if (methodAnnotated && namespaceIsEmpty) {
                    mnmsp = tm.value();
                    if (S.empty(mnmsp)) mnmsp = nmsp;
                }
                
                String mwaive = waive;
                if (methodAnnotated && waiveIsEmpty) {
                    mwaive = tm.waivePattern();
                    if (S.empty(mwaive)) mwaive = waive;
                }
                String cn = extensionClass.getSimpleName();
                if (S.empty(mwaive)) mwaive = cn;
                
                boolean requireTemplate = clsRequireTemplate;
                if (null != tm && tm.requireTemplate()) {
                    requireTemplate = true;
                }

                String cn0 = extensionClass.getName();
                String mn = m.getName();
                String fullName = String.format("%s.%s", cn0, mn);
                if (S.notEmpty(mnmsp) && !"rythm".equals(mnmsp)) {
                    mn = mnmsp + "_" + mn;
                }
                if (len == 1) {
                    jem.registerJavaExtension(new IJavaExtension.VoidParameterExtension(mwaive, mn, fullName, requireTemplate));
                } else {
                    jem.registerJavaExtension(new IJavaExtension.ParameterExtension(mwaive, mn, ".+", fullName, requireTemplate));
                }
            }
        }
    }

    /* -----------------------------------------------------------------------------
      Rendering methods and APIs
    -------------------------------------------------------------------------------*/

    private void setRenderArgs(ITemplate t, Object... args) {
        if (null == args) {
            t.__setRenderArg(0, null);
        } else if (1 == args.length) {
            Object o0 = args[0];
            if (o0 instanceof Map) {
                t.__setRenderArgs((Map<String, Object>) args[0]);
            } else if (o0 instanceof JSONWrapper) {
                t.__setRenderArg((JSONWrapper) o0);
            } else {
                t.__setRenderArgs(args);
            }
        } else {
            t.__setRenderArgs(args);
        }
        //if (mode.isDev()) cceCounter.remove();
    }

    @Deprecated
    private void handleCCE(ClassCastException ce) {
//        Integer I = cceCounter.get();
//        if (null == I) {
//            I = 0;
//            cceCounter.set(1);
//        } else {
//            I++;
//            cceCounter.set(I);
//        }
//        if (I > 2) {
//            cceCounter.remove();
//            throw ce;
//        }
//        restart(ce);
    }

    //static ThreadLocal<Integer> cceCounter = new ThreadLocal<Integer>();

    private ITemplate getTemplate(IDialect dialect, String template, Object... args) {
        set(this);
        boolean typeInferenceEnabled = conf().typeInferenceEnabled();
        if (typeInferenceEnabled) {
            ParamTypeInferencer.registerParams(this, args);
        }
        
        String key = template;
        if (typeInferenceEnabled) {
            key += ParamTypeInferencer.uuid();
        }

        TemplateClass tc = classes().getByTemplate(key);
        if (null == tc) {
            tc = new TemplateClass(template, this, dialect);
            //classes().add(key, tc);
        }
        ITemplate t = tc.asTemplate();
        String fullTagName = resourceManager().getFullTagName(tc);
        tc.setFullName(fullTagName);
        _templates.put(fullTagName, t);
        setRenderArgs(t, args);
//        try {
//            __setRenderArgs(t, args);
//        } catch (ClassCastException ce) {
//            if (mode.isDev()) {
//                handleCCE(ce);
//                return getTemplate(dialect, template, args);
//            }
//            throw ce;
//        }
        return t;
    }

    /**
     * Get an new {@link ITemplate template} instance from a String and an array
     * of render args. The string parameter could be either a template file path
     * or the inline template source content.
     * <p/>
     * <p>When the args array contains only one element and is of {@link java.util.Map} type
     * the the render args are passed to template
     * {@link ITemplate#__setRenderArgs(java.util.Map) by name},
     * otherwise they passes to template instance by position</p>
     *
     * @param template
     * @param args
     * @return template instance
     */
    @SuppressWarnings("unchecked")
    public ITemplate getTemplate(String template, Object... args) {
        return getTemplate(null, template, args);
    }

    /**
     * (3rd party API, not for user application)
     * Get an new template class by {@link org.rythmengine.resource.ITemplateResource template resource}
     *
     * @param resource the template resource
     * @return template class
     */
    public TemplateClass getTemplateClass(ITemplateResource resource) {
        String key = S.str(resource.getKey());
        TemplateClass tc = classes().getByTemplate(key);
        if (null == tc) {
            tc = new TemplateClass(resource, this);
            //classes().add(key, tc);
        }
        return tc;
    }

    /**
     * Get an new template instance by template source {@link java.io.File file}
     * and an array of arguments.
     *
     * <p>When the args array contains only one element and is of {@link java.util.Map} type
     * the the render args are passed to template
     * {@link ITemplate#__setRenderArgs(java.util.Map) by name},
     * otherwise they passes to template instance by position</p>
     *
     * @param file the template source file
     * @param args the render args. See {@link #getTemplate(String, Object...)}
     * @return template instance
     */
    @SuppressWarnings("unchecked")
    public ITemplate getTemplate(File file, Object... args) {
        boolean typeInferenceEnabled = conf().typeInferenceEnabled();
        if (typeInferenceEnabled) {
            ParamTypeInferencer.registerParams(this, args);
        }

        String key = S.str(resourceManager().get(file).getKey());
        if (typeInferenceEnabled) {
            key += ParamTypeInferencer.uuid();
        }
        TemplateClass tc = classes().getByTemplate(key);
        ITemplate t;
        if (null == tc) {
            tc = new TemplateClass(file, this);
            t = tc.asTemplate();
            if (null == t) return null;
            String fullTagName = resourceManager().getFullTagName(tc);
            tc.setFullName(fullTagName);
            _templates.put(fullTagName, t);
            //classes().add(key, tc);
        } else {
            t = tc.asTemplate();
        }
        setRenderArgs(t, args);
        return t;
    }

    /**
     * Render template by string parameter and an array of
     * template args. The string parameter could be either
     * a path point to the template source file, or the inline
     * template source content. The render result is returned
     * as a String
     * <p/>
     * <p>See {@link #getTemplate(java.io.File, Object...)} for note on
     * render args</p>
     *
     * @param template either the path of template source file or inline template content
     * @param args     render args array
     * @return render result
     */
    public String render(String template, Object... args) {
        ITemplate t = getTemplate(template, args);
        return t.render();
    }

    /**
     * Render template by string parameter and an array of
     * template args. The string parameter could be either
     * a path point to the template source file, or the inline
     * template source content. The render result is output
     * to the specified binary output stream
     * <p/>
     * <p>See {@link #getTemplate(java.io.File, Object...)} for note on
     * render args</p>
     *
     * @param os       the output stream
     * @param template either the path of template source file or inline template content
     * @param args     render args array
     */
    public void render(OutputStream os, String template, Object... args) {
        outputMode.set(OutputMode.os);
        ITemplate t = getTemplate(template, args);
        t.render(os);
    }

    /**
     * Render template by string parameter and an array of
     * template args. The string parameter could be either
     * a path point to the template source file, or the inline
     * template source content. The render result is output
     * to the specified character based writer
     * <p/>
     * <p>See {@link #getTemplate(java.io.File, Object...)} for note on
     * render args</p>
     *
     * @param w        the writer
     * @param template either the path of template source file or inline template content
     * @param args     render args array
     */
    public void render(Writer w, String template, Object... args) {
        outputMode.set(OutputMode.writer);
        ITemplate t = getTemplate(template, args);
        t.render(w);
    }

    /**
     * Render template with source specified by {@link java.io.File file instance}
     * and an array of render args. Render result return as a String
     * <p/>
     * <p>See {@link #getTemplate(java.io.File, Object...)} for note on
     * render args</p>
     *
     * @param file the template source file
     * @param args render args array
     * @return render result
     */
    public String render(File file, Object... args) {
        ITemplate t = getTemplate(file, args);
        return t.render();
    }

    /**
     * Render template with source specified by {@link java.io.File file instance}
     * and an array of render args. Render result output into the specified binary
     * {@link java.io.OutputStream}
     * <p/>
     * <p>See {@link #getTemplate(java.io.File, Object...)} for note on
     * render args</p>
     *
     * @param os   the output stream
     * @param file the template source file
     * @param args render args array
     */
    public void render(OutputStream os, File file, Object... args) {
        outputMode.set(OutputMode.os);
        ITemplate t = getTemplate(file, args);
        t.render(os);
    }

    /**
     * Render template with source specified by {@link java.io.File file instance}
     * and an array of render args. Render result output into the specified binary
     * {@link java.io.Writer}
     * <p/>
     * <p>See {@link #getTemplate(java.io.File, Object...)} for note on
     * render args</p>
     *
     * @param w    the writer
     * @param file the template source file
     * @param args render args array
     */
    public void render(Writer w, File file, Object... args) {
        outputMode.set(OutputMode.writer);
        ITemplate t = getTemplate(file, args);
        t.render(w);
    }

    /**
     * Render template by string typed inline template content and an array of
     * template args. The render result is returned as a String
     * <p/>
     * <p>See {@link #getTemplate(java.io.File, Object...)} for note on
     * render args</p>
     *
     * @param template the inline template content
     * @param args     the render args array
     * @return render result
     */
    public String renderStr(String template, Object... args) {
        return renderString(template, args);
    }

    /**
     * Alias of {@link #renderString(String, Object...)}
     * <p/>
     * <p>See {@link #getTemplate(java.io.File, Object...)} for note on
     * render args</p>
     *
     * @param template the inline template content
     * @param args     the render args array
     * @return render result
     */
    @SuppressWarnings("unchecked")
    public String renderString(String template, Object... args) {
        boolean typeInferenceEnabled = conf().typeInferenceEnabled();
        if (typeInferenceEnabled) {
            ParamTypeInferencer.registerParams(this, args);
        }
        
        String key = template;
        if (typeInferenceEnabled) {
            key += ParamTypeInferencer.uuid();
        }
        TemplateClass tc = classes().getByTemplate(key, false);
        if (null == tc) {
            tc = new TemplateClass(new StringTemplateResource(template), this);
            //classes().add(key, tc);
        }
        ITemplate t = tc.asTemplate();
        setRenderArgs(t, args);
        return t.render();
    }

    /**
     * Render template in substitute mode by string typed template source
     * and an array of render args. The string parameter could be either
     * a path point to the template source file, or the inline
     * template source content. Return render result as String
     * <p/>
     * <p>See {@link #getTemplate(java.io.File, Object...)} for note on
     * render args</p>
     *
     * @param template either the template source file path or the inline template content
     * @param args     the render args array
     * @return render result
     */
    public String substitute(String template, Object... args) {
        ITemplate t = getTemplate(BasicRythm.INSTANCE, template, args);
        return t.render();
    }

    /**
     * Render template in substitute mode by File typed template source
     * and an array of render args. Return render result as String
     * <p/>
     * <p>See {@link #getTemplate(java.io.File, Object...)} for note on
     * render args</p>
     *
     * @param file the template source file
     * @param args the render args array
     * @return render result
     */
    public String substitute(File file, Object... args) {
        ITemplate t = getTemplate(file, args, BasicRythm.INSTANCE);
        return t.render();
    }

    /**
     * Render template in ToString mode by string typed template source
     * and one object instance which state is to be output.
     * The string parameter could be either a path point to the template
     * source file, or the inline template source content. Return render
     * result as String
     *
     * @param template either the template source file path or the inline template content
     * @param obj      the object instance which state is to be output as a string
     * @return render result
     */
    public String toString(String template, Object obj) {
        Class argClass = obj.getClass();
        String clsName = argClass.getName();
        if (clsName.matches(".*\\$[0-9].*")) {
            argClass = obj.getClass().getSuperclass();
        }
        String key = template + argClass;
        TemplateClass tc = classes().getByTemplate(key);
        if (null == tc) {
            tc = new TemplateClass(template, this, new ToString(argClass));
            //classes().add(key, tc);
        }
        ITemplate t = tc.asTemplate();
        t.__setRenderArg(0, obj);
        return t.render();
    }

    /**
     * Render template in AutoToString mode by one object instance which state is
     * to be output. Return render result as String
     *
     * @param obj the object instance which state is to be output as a string
     * @return render result
     */
    public String toString(Object obj) {
        return toString(obj, ToStringOption.DEFAULT_OPTION, (ToStringStyle) null);
    }

    /**
     * Render template in AutoToString mode by one object instance which state is
     * to be output and {@link ToStringOption option} and {@link ToStringStyle stype}.
     * Return render result as String
     *
     * @param obj    the object instance which state is to be output as a string
     * @param option the output option
     * @param style  the output style
     * @return render result
     */
    public String toString(Object obj, ToStringOption option, ToStringStyle style) {
        Class<?> c = obj.getClass();
        AutoToString.AutoToStringData key = new AutoToString.AutoToStringData(c, option, style);
        //String template = AutoToString.templateStr(c, option, style);
        TemplateClass tc = classes().getByTemplate(key);
        if (null == tc) {
            tc = new TemplateClass(new ToStringTemplateResource(key), this, new AutoToString(c, key));
            //classes().add(key, tc);
        }
        ITemplate t = tc.asTemplate();
        t.__setRenderArg(0, obj);
        return t.render();
    }

    /**
     * Render template in AutoToString mode by one object instance which state is
     * to be output and {@link ToStringOption option} and
     * {@link org.apache.commons.lang3.builder.ToStringStyle apache commons to string stype}.
     * Return render result as String
     *
     * @param obj    the object instance which state is to be output as a string
     * @param option the output option
     * @param style  the output style specified as apache commons ToStringStyle
     * @return render result
     */
    public String commonsToString(Object obj, ToStringOption option, org.apache.commons.lang3.builder.ToStringStyle style) {
        return toString(obj, option, ToStringStyle.fromApacheStyle(style));
    }

    private Set<String> nonExistsTemplates = new HashSet<String>();

    private class NonExistsTemplatesChecker {
        boolean started = false;
        private ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);

        NonExistsTemplatesChecker() {
            scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    List<String> toBeRemoved = new ArrayList<String>();
                    for (String template : nonExistsTemplates) {
                        ITemplateResource rsrc = resourceManager().getResource(template);
                        if (rsrc.isValid()) {
                            toBeRemoved.add(template);
                        }
                    }
                    nonExistsTemplates.removeAll(toBeRemoved);
                    toBeRemoved.clear();
                    TemplateClass tc = classes().all().get(0);
                    for (String tag : _nonExistsTags) {
                        if (null != resourceManager().tryLoadTemplate(tag, tc)) {
                            toBeRemoved.add(tag);
                        }
                    }
                    _nonExistsTags.removeAll(toBeRemoved);
                    toBeRemoved.clear();
                }
            }, 0, 1000 * 10, TimeUnit.MILLISECONDS);
        }
    }

    private NonExistsTemplatesChecker nonExistsTemplatesChecker = null;

    /**
     * Render template if specified template exists, otherwise return empty string
     *
     * @param template the template source path
     * @param args     render args. See {@link #getTemplate(String, Object...)}
     * @return render result
     */
    public String renderIfTemplateExists(String template, Object... args) {
        boolean typeInferenceEnabled = conf().typeInferenceEnabled();
        if (typeInferenceEnabled) {
            ParamTypeInferencer.registerParams(this, args);
        }

        if (nonExistsTemplates.contains(template)) return "";

        String key = template;
        if (typeInferenceEnabled) {
            key += ParamTypeInferencer.uuid();
        }

        TemplateClass tc = classes().getByTemplate(template);
        if (null == tc) {
            ITemplateResource rsrc = resourceManager().getResource(template);
            if (rsrc.isValid()) {
                tc = new TemplateClass(rsrc, this);
                //classes().add(key, tc);
            } else {
                nonExistsTemplates.add(template);
                if (mode().isDev() && nonExistsTemplatesChecker == null) {
                    nonExistsTemplatesChecker = new NonExistsTemplatesChecker();
                }
                return "";
            }
        }
        ITemplate t = tc.asTemplate();
        setRenderArgs(t, args);
        return t.render();
    }
    
    /* -----------------------------------------------------------------------------
      Eval
    -------------------------------------------------------------------------------*/

    /**
     * Evaluate a script and return executing result. Note the API is not mature yet
     * don't use it in your application
     * 
     * @param script
     * @return the result
     */
    public Object eval(String script) {
        // use Java's ScriptEngine at the moment
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine jsEngine = manager.getEngineByName("JavaScript");
        try {
            return jsEngine.eval(script);
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    /* -----------------------------------------------------------------------------
      Tags
    -------------------------------------------------------------------------------*/

    private final Map<String, ITemplate> _templates = new HashMap<String, ITemplate>();
    private final Set<String> _nonTmpls = new HashSet<String>();

    /**
     * Whether a {@link ITemplate template} is registered to the engine by name specified
     * <p/>
     * <p>Not an API for user application</p>
     *
     * @param tmplName
     * @return true if there is a template with the name specified
     */
    public boolean templateRegistered(String tmplName) {
        return _templates.containsKey(tmplName);
    }

    /**
     * Get a {@link ITemplate template} registered to the engine by name
     * <p/>
     * <p>Not an API for user application</p>
     *
     * @param tmplName
     * @return the template instance
     */
    public ITemplate getRegisteredTemplate(String tmplName) {
        return _templates.get(tmplName);
    }

    /**
     * Return {@link TemplateClass} from a tag name
     * <p/>
     * <p>Not an API for user application</p>
     *
     * @param name
     * @return template class
     */
    public TemplateClass getRegisteredTemplateClass(String name) {
        TemplateBase tmpl = (TemplateBase) _templates.get(name);
        if (null == tmpl) return null;
        return tmpl.__getTemplateClass(false);
    }

    /**
     * Register a template class and return self
     * 
     * @param tc
     * @return this engine instance
     */
    public RythmEngine registerTemplateClass(TemplateClass tc) {
        String fullName = resourceManager().getFullTagName(tc);
        tc.setFullName(fullName);
        classes().add(tc);
        return this;
    }

    /**
     * Check if a template exists and return it's name
     * <p/>
     * <p>Not an API for user application</p>
     *
     * @param name
     * @param callerClass
     * @return template name
     */
    public String testTemplate(String name, TemplateClass callerClass) {
        if (Keyword.THIS.toString().equals(name)) {
            return resourceManager().getFullTagName(callerClass);
        }
        if (mode().isProd() && _nonTmpls.contains(name)) return null;
        if (templateRegistered(name)) return name;
        // try imported path
        if (null != callerClass.importPaths) {
            for (String s : callerClass.importPaths) {
                String name0 = s + "." + name;
                if (_templates.containsKey(name0)) return name0;
            }
        }
        // try relative path
        // TODO: handle logic error here, caller foo.bar.html, tag: zee
        // then should try foo.zee.html, if tag is zee.js, then should try foo.zee.js
        String callerName = resourceManager().getFullTagName(callerClass);
        int pos = callerName.lastIndexOf(".");
        if (-1 != pos) {
            String s = callerName.substring(0, pos);
            String name0 = s + "." + name;
            if (_templates.containsKey(name0)) return name0;
            pos = s.lastIndexOf(".");
            if (-1 != pos){
                s = callerName.substring(0, pos);
                name0 = s + "." + name;
                if (_templates.containsKey(name0)) return name0;
            }
        }

        try {
            // try to ask resource manager
            TemplateClass tc = resourceManager().tryLoadTemplate(name, callerClass);
            if (null == tc) {
                if (mode().isProd()) _nonTmpls.add(name);
                return null;
            }
            String fullName = tc.getFullName();
            return fullName;
        } catch (TagLoadException e) {
            throw e;
        } catch (RythmException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e, "error trying load tag[%s]", name);
            // see if the
        }
        return null;
    }

    /**
     * Register a template. If there is name collision then registration
     * will fail
     * <p/>
     * <p>Not an API for user application</p>
     *
     * @return true if registration success
     */
    public boolean registerTemplate(ITemplate template) {
        //TemplateResourceManager rm = resourceManager();
        String name;
        if (template instanceof JavaTagBase) {
            name = template.__getName();
        } else {
            name = template.__getTemplateClass(false).getFullName();
        }
        return registerTemplate(name, template);
    }

    /**
     * Register a tag using the given name
     * <p/>
     * <p>Not an API for user application</p>
     *
     * @param name
     * @param template
     * @return false if registration failed
     */
    public boolean registerTemplate(String name, ITemplate template) {
        if (null == template) throw new NullPointerException();
        if (_templates.containsKey(name)) {
            return false;
        }
        _templates.put(name, template);
        logger.trace("tag %s registered", name);
        return true;
    }

    /**
     * Invoke a template
     * <p/>
     * <p>Not an API for user application</p>
     *
     * @param line
     * @param name
     * @param caller
     * @param params
     * @param body
     * @param context
     */
    public void invokeTemplate(int line, String name, ITemplate caller, ITag.__ParameterList params, ITag.__Body body, ITag.__Body context) {
        invokeTemplate(line, name, caller, params, body, context, false);
    }

    private Set<String> _nonExistsTags = new HashSet<String>();

    /**
     * Invoke a tag
     * <p/>
     * <p>Not an API for user application</p>
     *
     * @param line
     * @param name
     * @param caller
     * @param params
     * @param body
     * @param context
     * @param ignoreNonExistsTag
     */
    public void invokeTemplate(int line, String name, ITemplate caller, ITag.__ParameterList params, ITag.__Body body, ITag.__Body context, boolean ignoreNonExistsTag) {
        if (_nonExistsTags.contains(name)) return;
        // try tag registry first
        ITemplate t = _templates.get(name);
        TemplateClass tc = caller.__getTemplateClass(true);
        if (null == t) {
            // is calling self
            if (S.isEqual(name, caller.__getName())) t = caller;
        }

        if (null == t) {
            // try imported path
            if (null != tc.importPaths) {
                for (String s : tc.importPaths) {
                    String name0 = s + "." + name;
                    t = _templates.get(name0);
                    if (null != t) break;
                }
            }

            // try relative path
            if (null == t) {
                String callerName = resourceManager().getFullTagName(tc);
                int pos = callerName.lastIndexOf(".");
                if (-1 != pos) {
                    String name0 = callerName.substring(0, pos) + "." + name;
                    t = _templates.get(name0);
                }
            }

            // try load the tag from resource
            if (null == t) {
                tc = resourceManager().tryLoadTemplate(name, tc);
                if (null != tc) t = _templates.get(tc.getFullName());
                if (null == t) {
                    if (ignoreNonExistsTag) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("cannot find tag: " + name);
                        }
                        _nonExistsTags.add(name);
                        if (mode().isDev() && nonExistsTemplatesChecker == null) {
                            nonExistsTemplatesChecker = new NonExistsTemplatesChecker();
                        }
                        return;
                    } else {
                        throw new NullPointerException("cannot find tag: " + name);
                    }
                }
                t = t.__cloneMe(this, caller);
            }
        }

        if (!(t instanceof JavaTagBase)) {
            // try refresh the tag loaded from template file under tag root
            // note Java source tags are not reloaded here
            String cn = t.getClass().getName();
            TemplateClass tc0 = classes().getByClassName(cn);
            if (null == tc0) {
                System.out.println(t.getClass());
                System.out.println(name);
                System.out.println(cn);
                System.out.println(caller.getClass());
            }
            t = tc0.asTemplate(caller);
        } else {
            t = t.__cloneMe(this, caller);
        }

        if (null != params) {
            if (t instanceof JavaTagBase) {
                ((JavaTagBase) t).__setRenderArgs0(params);
            } else {
                for (int i = 0; i < params.size(); ++i) {
                    ITag.__Parameter param = params.get(i);
                    if (null != param.name) t.__setRenderArg(param.name, param.value);
                    else t.__setRenderArg(i, param.value);
                }
            }
        }
        if (null == body && null != params) {
            body = (ITag.__Body)params.getByName("__body");
            if (null == body) {
                body = (ITag.__Body)params.getByName("_body");
            }
        }
        if (null != body) {
            t.__setRenderArg("__body", body);
            t.__setRenderArg("_body", body); // for compatiblity
        }
        RythmEvents.ON_TAG_INVOCATION.trigger(this, F.T2((TemplateBase) caller, t));
        try {
            if (null != context) {
                t.__setBodyContext(context);
            }
            t.__call(line);
        } finally {
            RythmEvents.TAG_INVOKED.trigger(this, F.T2((TemplateBase) caller, t));
        }
    }

    // -- cache api

    /**
     * Cache object using key and args for ttl seconds
     * <p/>
     * <p>Not an API for user application</p>
     *
     * @param key
     * @param o
     * @param ttl  if zero then defaultTTL used, if negative then never expire
     * @param args
     */
    public void cache(String key, Object o, int ttl, Object... args) {
        if (conf().cacheDisabled()) return;
        ICacheService cacheService = _cacheService;
        Serializable value = null == o ? "" : (o instanceof Serializable ? (Serializable) o : o.toString());
        if (args.length > 0) {
            StringBuilder sb = new StringBuilder(key);
            for (Object arg : args) {
                sb.append("-").append(arg);
            }
            key = sb.toString();
        }
        cacheService.put(key, value, ttl);
    }

    /**
     * Store object o into cache service with ttl equals to duration specified.
     * <p/>
     * <p>The duration is a string to be parsed by @{link #durationParser}</p>
     * <p/>
     * <p>The object o is associated with given key and a list of argument values</p>
     * <p/>
     * <p>Not an API for user application</p>
     *
     * @param key
     * @param o
     * @param duration
     * @param args
     */
    public void cache(String key, Object o, String duration, Object... args) {
        if (conf().cacheDisabled()) return;
        IDurationParser dp = conf().durationParser();
        int ttl = null == duration ? 0 : dp.parseDuration(duration);
        cache(key, o, ttl, args);
    }

    /**
     * Get cached value using key and a list of argument values
     * <p/>
     * <p>Not an API for user application</p>
     *
     * @param key
     * @param args
     * @return cached item
     */
    public Serializable cached(String key, Object... args) {
        if (conf().cacheDisabled()) return null;
        ICacheService cacheService = _cacheService;
        if (args.length > 0) {
            StringBuilder sb = new StringBuilder(key);
            for (Object arg : args) {
                sb.append("-").append(arg);
            }
            key = sb.toString();
        }
        return cacheService.get(key);
    }

    // -- SPI interface
    // -- issue #47
    private Map<TemplateClass, Set<TemplateClass>> extendMap = new HashMap<TemplateClass, Set<TemplateClass>>();

    /**
     * Not an API for user application
     *
     * @param parent
     * @param child
     */
    public void addExtendRelationship(TemplateClass parent, TemplateClass child) {
        if (mode().isProd()) return;
        Set<TemplateClass> children = extendMap.get(parent);
        if (null == children) {
            children = new HashSet<TemplateClass>();
            extendMap.put(parent, children);
        }
        children.add(child);
    }

    /**
     * Not an API for user application
     *
     * @param parent
     */
    // called to invalidate all template class which extends the parent
    public void invalidate(TemplateClass parent) {
        if (mode().isProd()) return;
        Set<TemplateClass> children = extendMap.get(parent);
        if (null == children) return;
        for (TemplateClass child : children) {
            invalidate(child);
            child.reset();
        }
    }

    // -- Sandbox

    private SandboxExecutingService _secureExecutor = null;

    private SandboxExecutingService secureExecutor() {
        if (null == _secureExecutor) {
            int poolSize = (Integer) conf().get(RythmConfigurationKey.SANDBOX_POOL_SIZE);
            SecurityManager sm = conf().get(RythmConfigurationKey.SANDBOX_SECURITY_MANAGER_IMPL);
            int timeout = (Integer) conf().get(RythmConfigurationKey.SANDBOX_TIMEOUT);
            SandboxThreadFactory fact = conf().get(RythmConfigurationKey.SANBOX_THREAD_FACTORY_IMPL);
            if (null == fact) fact = new SandboxThreadFactory(sm, null, this);
            _secureExecutor = new SandboxExecutingService(poolSize, fact, timeout, this);
        }
        return _secureExecutor;
    }

    /**
     * Create a {@link Sandbox} instance to render the template
     *
     * @return an new sandbox instance
     */
    public Sandbox sandbox() {
        return new Sandbox(this, secureExecutor());
    }

    /**
     * Create a {@link Sandbox} instance with user supplied context 
     * 
     * @param context
     * @return an new sandbox instance
     */
    public Sandbox sandbox(Map<String, Object> context) {
        return new Sandbox(this, secureExecutor()).setUserContext(context);
    }

    // dispatch rythm events
    private IEventDispatcher eventDispatcher = null;

    private IEventDispatcher eventDispatcher() {
        if (null == eventDispatcher) {
            eventDispatcher = new EventBus(this);
        }
        return eventDispatcher;
    }

    /**
     * Not an API for user application
     *
     * @param event
     * @param param
     * @return event handler process result
     */
    @Override
    public Object accept(IEvent event, Object param) {
        return eventDispatcher().accept(event, param);
    }

    /* -----------------------------------------------------------------------------
      Output Mode
    -------------------------------------------------------------------------------*/

    /**
     * Defines the output method for render result.
     * <ul>
     * <li>os: output to binary {@link java.io.OutputStream}</li>
     * <li>writer: output to character based {@link java.io.Writer}</li>
     * <li>str: return render result as a {@link java.lang.String}</li>
     * </ul>
     * <p/>
     * <p>This is not an API used in user application</p>
     */
    public static enum OutputMode {
        os, writer, str {
            @Override
            public boolean writeOutput() {
                return false;
            }
        };

        /**
         * Return true if the current output mode is to output to {@link java.io.OutputStream}
         * or {@link java.io.Writer}
         *
         * @return true if output mode is not return string
         */
        public boolean writeOutput() {
            return true;
        }
    }

    private final static InheritableThreadLocal<OutputMode> outputMode = new InheritableThreadLocal<OutputMode>() {
        @Override
        protected OutputMode initialValue() {
            return OutputMode.str;
        }
    };

    /**
     * Not an API.
     *
     * @return output mode
     */
    public static OutputMode outputMode() {
        return outputMode.get();
    }

    /* -----------------------------------------------------------------------------
      Restart and Shutdown
    -------------------------------------------------------------------------------*/

    /**
     * Restart the engine with an exception as the cause.
     * <p><b>Note</b>, this is not supposed to be called by user application</p>
     *
     * @param cause
     */
    public void restart(RuntimeException cause) {
        if (isProdMode()) throw cause;
        if (!(cause instanceof ClassReloadException)) {
            String msg = cause.getMessage();
            if (cause instanceof RythmException) {
                RythmException re = (RythmException) cause;
                msg = re.getSimpleMessage();
            }
            logger.warn("restarting rythm engine due to %s", msg);
        }
        restart();
    }

    private void restart() {
        if (isProdMode()) return;
        _classLoader = new TemplateClassLoader(this);

        // clear all template tags which is managed by TemplateClassManager
        List<String> templateTags = new ArrayList<String>();
        for (String name : _templates.keySet()) {
            ITag tag = _templates.get(name);
            if (!(tag instanceof JavaTagBase)) {
                templateTags.add(name);
            }
        }
        for (String name : templateTags) {
            _templates.remove(name);
        }
    }

    interface IShutdownListener {
        void onShutdown();
    }

    private IShutdownListener shutdownListener = null;

    void setShutdownListener(IShutdownListener listener) {
        this.shutdownListener = listener;
    }

    /**
     * Shutdown this rythm engine
     */
    public void shutdown() {
        if (null != _cacheService) {
            try {
                _cacheService.shutdown();
            } catch (Exception e) {
                logger.error(e, "Error shutdown cache service");
            }
        }
        if (null != _secureExecutor) {
            try {
                _secureExecutor.shutdown();
            } catch (Exception e) {
                logger.error(e, "Error shutdown secure executor");
            }
        }
        if (null != shutdownListener) {
            try {
                shutdownListener.onShutdown();
            } catch (Exception e) {
                logger.error(e, "Error execute shutdown listener");
            }
        }
        if (null != _templates) _templates.clear();
        if (null != _classes) _classes.clear();
        if (null != _nonExistsTags) _nonExistsTags.clear();
        if (null != _nonTmpls) _nonTmpls.clear();
    }

}
