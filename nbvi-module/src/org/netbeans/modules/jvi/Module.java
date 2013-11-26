package org.netbeans.modules.jvi;

import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JEditorPane;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import org.netbeans.modules.jvi.impl.NbAppView;
import org.netbeans.modules.jvi.impl.NbFS;
import org.netbeans.modules.jvi.impl.NbFactory;
import org.netbeans.modules.jvi.impl.NbJviPrefs;
import org.netbeans.modules.jvi.reflect.NbWindows;
import org.netbeans.modules.jvi.spi.WindowsProvider;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.Actions;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.InstanceCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.ModuleInfo;
import org.openide.modules.ModuleInstall;
import org.openide.modules.SpecificationVersion;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import com.raelity.jvi.ViCaret;
import com.raelity.jvi.ViInitialization;
import com.raelity.jvi.core.ColonCommands;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.core.lib.CcFlag;
import com.raelity.jvi.core.lib.PreferencesChangeMonitor;
import com.raelity.jvi.manager.AppViews;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.options.DebugOption;
import com.raelity.jvi.options.OptUtil;

/**
 * Initialization/install/uninstall, TopComponent tracking,
 * KeyBinding installation, some colon commands for state output.
 * The initialization has three components:
 * <ol>
 *   <li>Enable/disable jVi. This is independent of the module install stuff.
 *       jVi can be started/stopped while the module is instaelled. </li>
 *   <li>Early init. The stuff that should be done when the module is first
 *       installed and/or accessed, eg start up listeners, prepare jVi for use.
 *       Since jVi might be accessed before it is installed. There is more in
 *       here than seems right. But it may be that I don't understand the
 *       startup very well.</li>
 *   <li>Stuff that can be done later</li>
 * </ol>
 */
public class Module extends ModuleInstall
{
    private static final Logger LOG = Logger.getLogger(Module.class.getName());

    private static NbFactory factory;

    private static boolean jViEnabled;

    public static final String MOD
            = "Module-" + Integer.toHexString(System.identityHashCode(
                            Module.class.getClassLoader())) + ": ";

    private static final String PREF_ENABLED = "viEnabled";
    
    // The persistent option names and their variables
    public static final String DBG_MODULE = "DebugNbModule";
    public static final String DBG_TC = "DebugNbTopComponent";
    public static final String DBG_HL = "DebugNbHilight";
    private static WeakReference<TopComponent> refOutput;
    // Could set init value from a system property, but they init very early
    private static DebugOption dbgNb = OptUtil.createBootDebugOption(false);
    private static DebugOption dbgTC = OptUtil.createBootDebugOption(false);
    private static DebugOption dbgHL = OptUtil.createBootDebugOption(false);

    public static DebugOption dbgNb() {
        return dbgNb;
    }
    public static DebugOption dbgTC()
    {
        return dbgTC;
    }
    public static DebugOption dbgHL()
    {
        return dbgHL;
    }

    private static WindowsProvider wp;
    public static WindowsProvider getWindowsProvider()
    {
        if(wp == null) {
            // wp = Lookup.getDefault().lookup(WindowsProvider.class);
            if(wp == null)
                wp = NbWindows.getReflectionWindowsProvider();
        }
        return wp;
    }

    public static final String HACK_CC = "NB6.7 Code Completion";
    public static final String HACK_SCROLL = "NB6.7 Text Scroll";
    public static final String HACK_WINDOW_GROUP = "NB7.1 MinimizeWindowGroup";
    public static final String HACK_CLONE_LOSE_EDITS = "NB7.1 Clone Lose Edits";
    public static final String HACK_FOLD_ASYNC = "NB7.4 Fold Collapse Async";
    
    private static TopComponentRegistryListener topComponentRegistryListener;

    // a SET
    private static final Map<TopComponent, Object> tcChecked
            = new WeakHashMap<TopComponent, Object>();

    private static Runnable shutdownHook;

    private static boolean didInit;
    @ServiceProvider(service=ViInitialization.class,
                     path="jVi/init",
                     position=10)
    public static class Init implements ViInitialization
    {
        @Override
        public void init()
        {
            if(didInit)
                return;
            Module.init();
            didInit = true;
        }
    }

    private static void init()
    {
        PropertyChangeListener pcl = new PropertyChangeListener()
        {
            @Override
            public void propertyChange(PropertyChangeEvent evt)
            {
                String pname = evt.getPropertyName();
                if(pname.equals(ViManager.P_BOOT)) {
                    addDebugOptions();
                } else if(pname.equals(ViManager.P_LATE_INIT)) {
                    NbColonCommands.init();
                    addDebugColonCommands();
                }
            }
        };
        ViManager.addPropertyChangeListener(ViManager.P_BOOT, pcl);
        ViManager.addPropertyChangeListener(ViManager.P_LATE_INIT, pcl);

        PreferencesChangeMonitor.setFileHack(
                new PreferencesChangeMonitor.FileHack()
                {
                    @Override
                    public boolean hasKeyValue(Preferences prefs, String child,
                                               String key, String val )
                    {
                        // look into the preferences file,
                        // not what the preferences subsystem thinks,
                        // to see if the key value is there.
                        FileObject cf = FileUtil.getConfigFile(
                              "Preferences" + prefs.absolutePath()
                              + "/" + child + ".properties");
                        if(cf != null) {
                            try {
                                String s = cf.asText();
                                if(s.contains(key + "=" + val)) {
                                    return true;
                                }
                            } catch(IOException ex) {
                                LOG.log(Level.SEVERE, null, ex);
                            }
                        }
                        return false;
                    }
                });
    }

    public static String cid(Object o)
    {
        return ViManager.cid(o);
    }

    public static boolean jViEnabled() {
        return jViEnabled;
    }

    /** @return the module specific preferences.
     */
    private static Preferences getModulePreferences() {
        return factory.getPreferences().node("module");
    }
    private static boolean isModuleEnabled() {
        Preferences prefs = getModulePreferences();
        return prefs.getBoolean(PREF_ENABLED, true);
    }
    private static void setModuleEnabled(boolean flag) {
        getModulePreferences().putBoolean(PREF_ENABLED, flag);
    }

    public static TopComponent getOutput()
    {
        return refOutput == null ? null : refOutput.get();
    }

    public static String getName(JEditorPane ep)
    {
        String s = ViManager.getFactory().getFS().getDisplayFileName(
                                ViManager.getFactory().getAppView(ep));
        if(NbFS.NULL_APP_VIEW.equals(s)) {
            // NEEDSWORK:  av = (NbAppView)ep
            //                  .getClientProperty(SwingFactory.PROP_AV);
            s = ep.getClass().getName() + ":" + s;
        }
        return s;
    }
    
    /** called when the module is loaded (at netbeans startup time) */
    @Override
    public void restored() {
        if (dbgNb().getBoolean()) {
            dbgNb().println(MOD + "***** restored *****");
        }
        // Look for an UndoRedo-patch.jar
        File patchDir = InstalledFileLocator.getDefault().locate(
                "modules/patches/org-openide-awt", null, false);
        if(patchDir != null) {
            LOG.log(Level.INFO, "Found patch dir \"{0}\"", patchDir.getAbsolutePath());
            File[] f = patchDir.listFiles(new FilenameFilter() {
                   @Override
                   public boolean accept(File dir, String name)
                   {
                       return name.contains("UndoRedo-patch");
                   }
               });
            if(f != null && f.length > 0) {
                NotifyDescriptor d = new NotifyDescriptor.Message(
                        "Found \"" + f[0].getAbsolutePath() + "\"\n"
                        + "If for jVi undo/redo operation then"
                        + " \n\nremove this file and restart NetBeans\n\n"
                        + "or functionality is lost."
                        + "\nNetBeans now has native support"
                        + "for UndoRedo grouping.",
                        NotifyDescriptor.WARNING_MESSAGE);
                DialogDisplayer.getDefault().notify(d);
            }
        }

        for (ModuleInfo mi : Lookup.getDefault().lookupAll(ModuleInfo.class)) {
            if (mi.getCodeNameBase().equals(
                    "org.netbeans.modules.editor.codetemplates")) {
                if (mi.getSpecificationVersion().compareTo(
                        new SpecificationVersion("1.8.0")) < 0) {
                    ViManager.putHackMap(
                            "NB-codetemplatesHang", Boolean.TRUE);
                }
            } else if (mi.getCodeNameBase().equals(
                    "org.netbeans.modules.editor.lib2")) {
                //System.err.println("LIB2 VERSION: "
                //        + mi.getSpecificationVersion());
                if (mi.getSpecificationVersion().compareTo(
                        new SpecificationVersion("1.11.1.2")) >= 0) {
                    // HACK_CC fixup code-compl registratin and bindings
                    ViManager.putHackMap(HACK_CC, Boolean.TRUE);
                    ViManager.putHackMap(HACK_SCROLL, Boolean.TRUE);
                }
            } else if (mi.getCodeNameBase().equals(
                    "org.netbeans.core.windows")) {
                if (mi.getSpecificationVersion().compareTo(
                        new SpecificationVersion("2.41.1")) >= 0) {
                    ViManager.putHackMap(HACK_WINDOW_GROUP, Boolean.TRUE);
                }
            } else if (mi.getCodeNameBase().equals(
                    "org.openide.text")) {
                if (mi.getSpecificationVersion().compareTo(
                        new SpecificationVersion("6.40")) > 0
                    && mi.getSpecificationVersion().compareTo(
                        new SpecificationVersion("6.43.2")) < 0)
                {
                    ViManager.putHackMap(HACK_CLONE_LOSE_EDITS, Boolean.TRUE);
                }
            } else if (mi.getCodeNameBase().equals(
                    "org.netbeans.modules.editor.fold")) {
                if (mi.getSpecificationVersion().compareTo(
                        new SpecificationVersion("1.37.1")) >= 0) // NB-7.4
                {
                    ViManager.putHackMap(HACK_FOLD_ASYNC, Boolean.TRUE);
                }
            }
        }

        if(ViManager.getHackFlag(HACK_CLONE_LOSE_EDITS)) {
            WindowManager.getDefault().invokeWhenUIReady(new Runnable() {
                @Override public void run() { permanentDisableDialog(); }
            });
        }

        earlyInit(); // set up the ViFactory
        // Add the actions used by jVi to the System's FileSystem
        KeyActionsFS.injectKeyActionsLayer();

        // in layer.xml Actions/Tools: <file name="o-n-m-jvi-enable.instance">
        // produces the checkbox linked to preferences.
        Preferences prefNode = getModulePreferences();
        String prefEnabled = prefNode.get(PREF_ENABLED, "");
        if(prefEnabled.isEmpty()
                || Boolean.parseBoolean(prefEnabled)
                   && ViManager.getHackFlag(HACK_CLONE_LOSE_EDITS)) {
            // Not set or set and shouldn't be
            prefNode.putBoolean(PREF_ENABLED,
                                !ViManager.getHackFlag(HACK_CLONE_LOSE_EDITS));
        }

        // Monitor activations/opens/closes.
        if(topComponentRegistryListener == null) {
            topComponentRegistryListener = new TopComponentRegistryListener();
            TopComponent.getRegistry().addPropertyChangeListener(
                    topComponentRegistryListener);
        }

        if (isModuleEnabled()) {
            ViManager.runInDispatch(true, new RunJViEnable());
        }

        prefNode.addPreferenceChangeListener(new PreferenceChangeListener() {
            @Override
            public void preferenceChange(PreferenceChangeEvent evt) {
                if(evt.getKey().equals(PREF_ENABLED)) {
                    if(ViManager.getHackFlag(HACK_CLONE_LOSE_EDITS)) {
                        if(Boolean.parseBoolean(evt.getNewValue())) {
                            permanentDisableDialog();
                            setModuleEnabled(false);
                        }
                        return;
                    }
                    LOG.log(Level.INFO, "jVi PREF CHANGE TO: {0}", evt.getNewValue());
                    boolean enabled = getModulePreferences()
                            .getBoolean(PREF_ENABLED, true);
                    EventQueue.invokeLater(enabled
                                           ? new RunJViEnable()
                                           : new RunJViDisable());
                    fixupJviButton();
                }
            }
        });
    }

    private static void permanentDisableDialog()
    {
        NotifyDescriptor d = new NotifyDescriptor.Message(
                "NetBeans 7.1 loses edits.\n\njVi disabled.\n\n"
                + "See NetBeans Bug 205835",
                NotifyDescriptor.ERROR_MESSAGE);
        d.setTitle("jVi Disabling");
        DialogDisplayer.getDefault().notifyLater(d);
    }

    ////////////////////////////////////////////////////////////////////
    //
    // NEEDSWORK: TEMPORARY; DELETE when handled by system
    //
    ////////////////////////////////////////////////////////////////////
    private static void fixupJviButton()
    {
        ViManager.runInDispatch(false, new Runnable() {
            @Override public void run()
            {
                if(jviButton != null) {
                    jviButton.setSelected(jViEnabled());
                }
            }
        });
    }
    private static AbstractButton jviButton;
    @ServiceProvider(service=Actions.ButtonActionConnector.class)
    public static class checkActions implements Actions.ButtonActionConnector {

        @Override
        public boolean connect(AbstractButton button, Action action)
        {
            if("jVi".equals(action.getValue(Action.NAME))) {
                if(action instanceof Presenter.Toolbar)
                    return false;

                // Older version of NetBeans, need to work around
                // Bug 197639 - Actions.checkbox has no icon for "off" state

                //System.err.println("connetc: " + action.getValue(Action.NAME));
                jviButton = button;
                Icon jviOnIcon = ImageUtilities.loadImageIcon("org/netbeans/modules/jvi/resources/jViLogoToggle24_selected.png", false);
                jviButton.setSelectedIcon(jviOnIcon);
                EventQueue.invokeLater(new Runnable()
                {
                    @Override public void run() {
                        fixupJviButton();
                    }
                });
            }
            return false;
        }

        @Override
        public boolean connect(JMenuItem item, Action action, boolean popup)
        {
            return false;
        }
    }

    public static void setShutdownHook(Runnable hook) {
        assert shutdownHook == null;
        shutdownHook = hook;
    }

    @Override
    public void close() {
        if(shutdownHook != null)
            shutdownHook.run();
    }

    private static boolean didEarlyInit = false;
    public static synchronized void earlyInit() {
        if(didEarlyInit)
            return;
        didEarlyInit = true;

        ViManager.runInDispatch(true, new Runnable() {
            @Override
            public void run() {
                factory = new NbFactory();
                ViManager.setViFactory(factory);
            }
        });
    }

    private static class RunJViEnable implements Runnable {
        @Override
        public void run() {
            if(jViEnabled)
                return;
            if(ViManager.getHackFlag(HACK_CLONE_LOSE_EDITS))
                return;
            jViEnabled = true;

            KeyBindings.enableKeyBindings();
        }
    }

    /**
     * Restore editor's caret's and keymaps
     */
    private static class RunJViDisable implements Runnable {
        @Override
        public void run() {
            if(!jViEnabled)
                return;
            // TODO: get rid of extra undo/redo state?
            ViManager.exitInputMode();
            jViEnabled = false;

            if(dbgNb().getBoolean())
                dbgNb().println(AppViews.dump(null).toString());
                //AppViews.dump(dbgNb());

            JViOptionWarning.clear();

            KeyBindings.disableKeyBindings();

            NbJviPrefs.jviDisabling(); // HACK
        }
    }
    
    private static void addDebugOptions()
    {
        dbgNb = OptUtil.createDebugOption(DBG_MODULE);
        OptUtil.setupOptionDesc(DBG_MODULE, "Module interface",
                                "Module and editor kit install/install");
        dbgTC = OptUtil.createDebugOption(DBG_TC);
        OptUtil.setupOptionDesc(DBG_TC, "Top Component",
                                "TopComponent activation/open");
        dbgHL = OptUtil.createDebugOption(DBG_HL);
        OptUtil.setupOptionDesc(DBG_HL, "Hilighting",
                                "Visual/Search highlighting");
    }
    
    private static void addDebugColonCommands() {
        
        //
        // Some debug commands
        //
        ColonCommands.register("dumpTopcomponent", "dumpTopcomponent",
            new ActionListener() {
                @SuppressWarnings("UseOfSystemOutOrSystemErr")
                @Override
                public void actionPerformed(ActionEvent e) {
                    Set<TopComponent> s = TopComponent.getRegistry().getOpened();
                    ViManager.println("TopComponents:");
                    for (TopComponent tc : s) {
                        if(tc == null) continue;
                        ViManager.println("    tc = " + tc.getDisplayName()
                                            + ", " + tc.isVisible()
                                            + ", " + tc.getClass().getName());
                    }
                }
            }, EnumSet.of(CcFlag.DBG)
        );
        ColonCommands.register("dumpKit", "dumpKit",
            new ActionListener() {
            @Override
                public void actionPerformed(ActionEvent e) {
                    KeyBindings.dumpKit();
                }
            }, EnumSet.of(CcFlag.DBG)
        );
        ColonCommands.register("checkFsActList", "checkFsActList",
            new ActionListener() {
                @SuppressWarnings("UseOfSystemOutOrSystemErr")
                @Override
                public void actionPerformed(ActionEvent e) {
                    for(FsAct fsAct : FsAct.values()) {
                        String path = fsAct.path();
                        ViManager.println("checkFsAct: "+fsAct.name()+" "+ path);
                        if(path == null)
                            continue;
                        if(fetchFileSystemAction(fsAct) == null) {
                            ViManager.println("\tNot found by getTheObject");
                        }
                    }

                }
            }, EnumSet.of(CcFlag.DBG)
        );
        
        /*
        WindowManager.getDefault().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if(G.dbgEditorActivation.getBoolean()) {
                    System.err.println("WM evt = " + evt.getPropertyName() + ": "
                            + dispName(evt.getOldValue())
                            + " --> " + dispName(evt.getNewValue()));
                }
            }
        });
         */
    }
    
    public static void execFileSystemAction(FsAct fsAct, ActionEvent e) {
        Action act = fetchFileSystemAction(fsAct);
        if(act != null && act.isEnabled())
            act.actionPerformed(e);
        else
            Util.beep_flush();
    }
    
    /** Get an Action from the file system at the given path.
     * Check if it is a SystemAction, if not then try to create it.
     * @return an Action, null if couldn't get or create one
     */
    @SuppressWarnings({"BroadCatchBlock", "TooBroadCatch", "UseSpecificCatch"})
    public static Action fetchFileSystemAction(FsAct fsAct)
    {
        String path = fsAct.path();
        Action act = null;
        try {
            FileObject fo = FileUtil.getConfigFile(path);
            if(fo != null) {
                Object o = DataObject.find(fo).getLookup().
                            lookup(InstanceCookie.class).instanceCreate();
                if(o instanceof Action)
                    act = (Action)o;
            }
        } catch(Exception ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return act;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Some methods to access a TC's AppView(s)
    // A TC may have more than one JEditorPane.
    //
    
    /** This class monitors the TopComponent registry and issues
     * <ul>
     * <li> ViManager.activate("debuginfo", tc) </li>
     * <li> ViManager.deactivateCurrent(ep, tc) </li>
     * <li> ViManager.exitInputMode() </li>
     * </ul>
     */
    private static class TopComponentRegistryListener
            implements PropertyChangeListener {
        @Override
        @SuppressWarnings("DeadBranch")
        public void propertyChange(PropertyChangeEvent evt) {
            assert(EventQueue.isDispatchThread());
            if(false && dbgTC().getBoolean()) {
                dbgTC().println("NbVi REG evt = " + evt.getPropertyName() + ": "
                        + evt.getOldValue()
                        + " --> " + evt.getNewValue());
            }
            if(evt.getPropertyName()
                    .equals(TopComponent.Registry.PROP_ACTIVATED)) {
                TopComponent tc = (TopComponent) evt.getOldValue();
                if(tc != null) {
                    tcDumpInfo(tc, "activated oldTC");

                    for (NbAppView av : NbAppView.fetchAvFromTC(tc)) {
                        AppViews.deactivate(av);
                    }
                }

                tc = (TopComponent) evt.getNewValue();
                if(tc != null) {
                    if(!tcChecked.containsKey(tc)) {
                        // After first activation, look through the
                        // GUI hierarchy again for jVi editors. Sigh.
                        getOpenedPanes(tc); // force instantiation of editor panes
                        boolean isEditor = isEditor(tc);

                        List<JEditorPane> l = getDescendentJviJep(tc);
                        for (JEditorPane ep : l) {
                            NbAppView av = NbAppView.updateAppViewForTC(
                                    "P_ACTV", tc, ep, !isEditor);
                        }
                        tcChecked.put(tc, null);
                    }

                    tcDumpInfo(tc, "activated newTC");
                }
            } else if(evt.getPropertyName()
                    .equals(TopComponent.Registry.PROP_TC_OPENED)) {
                TopComponent tc = (TopComponent) evt.getNewValue();

                boolean isEditor = isEditor(tc);
                tcDumpInfo(tc, "open");

                if(tc != null && "Output".equals(tc.getName())) {
                    refOutput = new WeakReference<TopComponent>(tc);
                }

                boolean createdAppView = false;

                // When opened, traverse the GUI hierarchy looking for
                // JEP that have jVi installed.
                List<JEditorPane> l = getDescendentJviJep(tc);
                for (JEditorPane ep : l) {
                    // if TC is not an editor then start out like a nomad
                    NbAppView av = NbAppView.updateAppViewForTC(
                            "P_OPEN", tc, ep, !isEditor);
                    createdAppView = true;
                }

                if(isEditor && !createdAppView) {
                    NbAppView av = NbAppView.updateAppViewForTC(
                            "P_OPEN_LAZY", tc, null);
                }
            } else if(evt.getPropertyName()
                    .equals(TopComponent.Registry.PROP_TC_CLOSED)) {
                TopComponent tc = (TopComponent) evt.getNewValue();
                for (NbAppView av : NbAppView.fetchAvFromTC(tc)) {
                    tcDumpInfo(tc, "close");
                    KeyBindings.removeKnownEditor(av.getEditor());
                    AppViews.close(av);
                }
                NbAppView.closeTC(tc);
            }
        }
    }

    public static List<JEditorPane> getDescendentJviJep(Component parent) {
        List<JEditorPane> l = new ArrayList<JEditorPane>(2);
        getDescendentJep(parent, l, true);
        return l;
    }

    static List<JEditorPane> getDescendentJep(Component parent) {
        List<JEditorPane> l = new ArrayList<JEditorPane>(2);
        getDescendentJep(parent, l, false);
        return l;
    }

    static void getDescendentJep(
            Component parent, List<JEditorPane> l, boolean skipNonJvi)
    {
        if (parent instanceof Container) {
            Component components[] = ((Container)parent).getComponents();
            for(Component comp : components) {
                if (comp != null) {
                    if(comp instanceof JEditorPane) {
                        if(skipNonJvi && !(((JEditorPane)comp).getCaret()
                                instanceof ViCaret))
                            continue;
                        l.add((JEditorPane)comp);
                    } else if (comp instanceof Container) {
                        getDescendentJep(comp, l, skipNonJvi);
                    }
                }
            }
        }
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public static void dumpComponnentHierarchy(Component c) {
        System.err.println("Component Hierarcy");
        dumpComponnentHierarchy1(c, 1);
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    private static void dumpComponnentHierarchy1(Component c, int depth) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < depth; i++) {
            sb.append("    ");
        }
        sb.append(c.getClass().getName());
        System.err.println(sb.toString());
        if(c instanceof Container) {
            for(Component child : ((Container)c).getComponents()) {
                dumpComponnentHierarchy1(child, depth+1);
            }
        }
    }

    /**
     * Search the component hierarchy for a component that contains name.
     */
    public static Component findComponentByName(Component c, String name)
    {
        if(c.getClass().getSimpleName().contains(name))
            return c;
        if(c instanceof Container) {
            for(Component child : ((Container)c).getComponents()) {
                Component c01 = findComponentByName(child, name);
                if(c01 != null)
                    return c01;
            }
        }
        return null;
    }
    
    private static String ancestorStringTC(Object o)
    {
        StringBuilder s = new StringBuilder();
        TopComponent tc;
        TopComponent parent = (TopComponent)SwingUtilities
                .getAncestorOfClass(TopComponent.class, (Component)o);
        while (parent != null) {
            s.append(" ")
                .append(parent.getDisplayName())
                .append(":")
                .append(cid(parent));
            tc = parent;
            parent = (TopComponent)SwingUtilities.getAncestorOfClass(TopComponent.class, tc);
        }
        return s.toString();
    }
    
    private static void tcDumpInfo(Object o, String tag)
    {
        if(!dbgTC().getBoolean())
            return;
        if(!(o instanceof TopComponent))
            return;
        TopComponent tc = (TopComponent) o;
        List<JEditorPane> panes = getDescendentJep(tc); // with non jvi editors
        Mode mode = WindowManager.getDefault().findMode(tc);
        if(dbgTC().getBoolean()) {
            dbgTC().printf("trackTC: %s: %s:%s '%s' : nPanes = %d\n",
                    tag,
                    tc.getDisplayName(), cid(tc),
                    (mode == null ? "null" : mode.getName()),
                    panes.size());
            for (JEditorPane ep : panes) {
                NbAppView av = NbAppView.fetchAvFromTC(tc, ep);
                dbgTC().printf("\tep:%d %s tc: %s isEditable %b %s\n",
                        av != null ? av.getWNum() : 0,
                        cid(ep), ancestorStringTC(ep), ep.isEditable(),
                        !(ep.getCaret() instanceof ViCaret) ? "NOT-JVI" : "");
            }
        }
    }

    /** note that this isn't GUI containment */
    public static boolean hasEditor(TopComponent tc, JEditorPane jep)
    {
        for(JEditorPane jep01 : getOpenedPanes(tc)) {
            if(jep == jep01)
                return true;
        }
        return false;
    }

    /**
     *
     * @param tc top component to examine
     * @return true if the tc is an editor
     */
    public static boolean isEditor(TopComponent tc)
    {
        return getEC(tc) != null; // this top component has an editor cookie
    }

    private static EditorCookie getEC(TopComponent tc)
    {
        if(tc == null)
            return null;
        Lookup lookup = tc.getLookup();
        if(lookup == null)
            return null;
        return lookup.lookup(EditorCookie.class);
    }

    private static JEditorPane[] getOpenedPanes(TopComponent tc)
    {
        EditorCookie ec = getEC(tc);
        if(ec == null)
            return new JEditorPane[0]; // no editor cookie

        return ec.getOpenedPanes();
    }

    /**
     * Look for a top component that this editor is contained in.
     * Only consider top components that have been opened.
     * @param editorPane
     * @return
     */
    public static TopComponent getKnownTopComponent(JEditorPane editorPane)
    {
        Set<TopComponent> setTC = TopComponent.getRegistry().getOpened();
        TopComponent tc = null;
        Container parent = SwingUtilities
                .getAncestorOfClass(TopComponent.class, editorPane);
        while (parent != null) {
            tc = (TopComponent)parent;
            if(setTC.contains(tc))
                break;
            parent = SwingUtilities.getAncestorOfClass(TopComponent.class, tc);
        }
        return tc;
    }
}
