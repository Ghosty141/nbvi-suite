/*
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * The Original Code is jvi - vi editor clone.
 *
 * The Initial Developer of the Original Code is Ernie Rael.
 * Portions created by Ernie Rael are
 * Copyright (C) 2000-2010 Ernie Rael.  All Rights Reserved.
 *
 * Contributor(s): Ernie Rael <err@raelity.com>
 */

package org.netbeans.modules.jvi;

import static org.netbeans.modules.jvi.Module.earlyInit;
import static org.netbeans.modules.jvi.Module.MOD;
import static org.netbeans.modules.jvi.Module.jViEnabled;
import static org.netbeans.modules.jvi.Module.cid;
import static org.netbeans.modules.jvi.Module.dbgNb;
import com.raelity.jvi.ViCaret;
import com.raelity.jvi.ViInitialization;
import com.raelity.jvi.ViOutputStream;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.swing.KeyBinding;
import com.raelity.jvi.swing.SwingFactory;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JEditorPane;
import javax.swing.KeyStroke;
import javax.swing.text.Caret;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.EditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import javax.swing.text.TextAction;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.MultiKeyBinding;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseKit;
import org.netbeans.modules.editor.settings.storage.spi.StorageFilter;
import org.netbeans.modules.jvi.impl.NbCaret;
import org.netbeans.modules.jvi.impl.NbFactory;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * JViInstallAction is registered in layer.xml<br/>
 * createKBA is registered in layer.xml<br/>
 * getKitInstallActionNameList is registered in prefereces.xml (indirect layer)<br/>
 *
 * @author Ernie Rael <err at raelity.com>
 */
public class KeyBindings {
    private static KeybindingsInjector KB_INJECTOR = null;
    private static final String JVI_INSTALL_ACTION_NAME = "jvi-install";
    private static Map<JEditorPane, Action> epToDefaultKeyAction =
            new HashMap<JEditorPane, Action>();
    private static Map<EditorKit, Action> kitToDefaultKeyAction =
            new HashMap<EditorKit, Action>();
    private static Map<JEditorPane, Caret> editorToCaret =
            new WeakHashMap<JEditorPane, Caret>();
    private static Map<JEditorPane, Object> knownEditors =
            new WeakHashMap<JEditorPane, Object>();

    private static boolean didInit;
    @ServiceProvider(service=ViInitialization.class, path="jVi/init")
    public static class Init implements ViInitialization
    {
        @Override
        public void init()
        {
            if(didInit)
                return;
            KeyBindings.init();
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
                if (dbgNb())
                    System.err.println("Injector: change: "
                            + evt.getPropertyName());
                if (evt.getPropertyName().equals(KeyBinding.KEY_BINDINGS))
                    // the bindings have changed
                    enableKeyBindings();
            }
        };
        KeyBinding.addPropertyChangeListener(KeyBinding.KEY_BINDINGS, pcl);
    }

    /** updates the keymap and restores DKTA and caret */
    static void enableKeyBindings()
    {
        if(dbgNb())
            System.err.println(MOD + " enableKeyBindings knownJEP: "
                    + knownEditors.size());

        KeyBindings.updateKeymap();

        // give all the editors the jVi DKTA and cursor
        for (JEditorPane ep : knownEditors.keySet()) {
            captureDefaultKeyTypedActionAndEtc(ep);
            checkCaret(ep);
        }
    }

    static void disableKeyBindings()
    {
        if(dbgNb())
            System.err.println(MOD + " disableKeyBindings knownJEP: "
                    + knownEditors.size());

        // restore the carets
        for (JEditorPane ep : knownEditors.keySet()) {
            Caret c01 = editorToCaret.get(ep);
            if(c01 != null) {
                if(ep.getCaret() instanceof NbCaret) {
                    NbFactory.installCaret(ep, c01);
                    if(dbgNb()) {
                        System.err.println("restore caret: "
                                + ViManager.getFactory().getFS()
                                  .getDisplayFileName(
                                   ViManager.getFactory().getAppView(ep)));
                    }
                }
                editorToCaret.remove(ep);
            }
        }
        if(editorToCaret.size() > 0) {
            System.err.println(MOD + "restore caret: "
                + "HUH? editorToCaret size: " + editorToCaret.size());
        }

        KeyBindings.updateKeymap();
    }

    static void dumpKit()
    {
        ViOutputStream os = ViManager.createOutputStream(
                null, ViOutputStream.OUTPUT, "Known Kits");
        for (Map.Entry<EditorKit, Action> entry
                : kitToDefaultKeyAction.entrySet()) {
            os.println(String.format("%20s %s",
                       entry.getKey().getClass().getSimpleName(),
                       entry.getValue().getClass().getSimpleName()));
        }
        os.close();
    }

    /**
     * @return false when editor already in there
     */
    private static boolean addKnownEditor(JEditorPane ep)
    {
        return knownEditors.put(ep, null) != null;
    }

    /**
     * @return false when editor already was not in there
     */
    private static boolean removeKnownEditor(JEditorPane ep)
    {
        editorToCaret.remove(ep);
        return knownEditors.remove(ep) != null;
    }

    /** EXPECTED THAT forceKeymapRefresh DOES NOT RETUN UNTILL ALL KEYMAPS DONE*/
    static void updateKeymap()
    {
        if (KB_INJECTOR != null) {
            if (dbgNb())
                System.err.println("Injector: updateKeymap: ");
            KB_INJECTOR.forceKeymapRefresh();
        } // else no keymap has been loaded yet
    }

    public static List<String> getKitInstallActionNameList()
    {
        List<String> l;
        l = Collections.singletonList(JVI_INSTALL_ACTION_NAME);
        return l;
    }

    /**
     * Get the defaultKeyTypedAction and other per ep stuff.
     * Do JViOptionWarning.
     *
     * The DKTA is captured on a per EditorKit basis. See
     * Bug 140201 -  kit install action, JEditorPane, DefaultKeyTypedAction issues
     * for further info.
     * @param ep
     */
    private static void captureDefaultKeyTypedActionAndEtc(JEditorPane ep)
    {
        JViOptionWarning.monitorMimeType(ep);
        Action a = ep.getKeymap().getDefaultAction();
        EditorKit kit = ep.getEditorKit();
        if (!(a instanceof SwingFactory.EnqueCharAction)) {
            kitToDefaultKeyAction.put(kit, a);
            //putDefaultKeyAction(ep, a);
            ep.getKeymap().
                    setDefaultAction(((NbFactory)ViManager.getFactory())
                    .createCharAction(DefaultEditorKit.defaultKeyTypedAction));
            if (dbgNb())
                System.err.println(MOD + "capture: "
                        + cid(ep.getEditorKit())
                        + " " + cid(ep) + " action: "
                        + a.getClass().getSimpleName());
        }
        else if (dbgNb())
            System.err.println(MOD + "ALREADY DKTA: "
                    + cid(ep) + " action: "
                    + a.getClass().getSimpleName());
        fixupKeypadKeys(ep);
    }

    //
    // Fixup the bindings and actionMap for the component
    //
    // Add bindings for the VK_KP_* that correspond to the regular (non-KP)
    // bindings. jVi does not bind them.
    //
    // As a fix for some jVi issues, hint widgets in NB6.9 uses
    // textComponent ActionMap rather than EditorKit actionMap.
    // So for actionNames corresponding to some standard BaseKit names add
    // name-->jviAction to ActionMap. So hint/codecompletion code
    // can get the keystrokes associated with an actionName and bind those
    // keystrokes in the hint widgets. For Example, find the keystrokes
    // assoc'd with BaseKit.forwardAction.
    //
    // If interested see editor.completion/src/org/netbeans/modules
    //                      /editor/completion/CompletionScrollPane
    //
    private static void fixupKeypadKeys(JTextComponent ep)
    {
        Keymap km = ep.getKeymap();

        for(int i = 0; i < fixupActions.length; i++) {
            String actionName = (String)fixupActions[i];
            KeyStroke ks = (KeyStroke)fixupActions[++i];

            Action jviAction = km.getAction(ks);
            if(!(jviAction instanceof SwingFactory.EnqueKeyAction))
                continue;
            ep.getActionMap().put(actionName, jviAction);
        }

        for(int i = 0; i < fixupStrokes.length; i++) {
            KeyStroke ks = fixupStrokes[i];
            KeyStroke kp_ks = fixupStrokes[++i];

            Action a = km.getAction(ks);
            if(!(a instanceof SwingFactory.EnqueKeyAction))
                continue;
            km.addActionForKeyStroke(kp_ks, a);
        }
    }

    private static Object[] fixupActions = new Object[] {
        BaseKit.upAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
        BaseKit.downAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
        BaseKit.pageDownAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0),
        BaseKit.pageUpAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0),
        BaseKit.beginLineAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0),
        BaseKit.endLineAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_END, 0),
        BaseKit.forwardAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
        BaseKit.backwardAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
    };

    // fixStrokes is used in pairs
    private static KeyStroke[] fixupStrokes = new KeyStroke[] {
        KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
        KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0),
        KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
        KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0),
        KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
        KeyStroke.getKeyStroke(KeyEvent.VK_KP_DOWN, 0),
        KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
        KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP, 0),

        KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.CTRL_DOWN_MASK),
        KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, InputEvent.CTRL_DOWN_MASK),
        KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK),
        KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, InputEvent.CTRL_DOWN_MASK),
        KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK),
        KeyStroke.getKeyStroke(KeyEvent.VK_KP_DOWN, InputEvent.CTRL_DOWN_MASK),
        KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK),
        KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP, InputEvent.CTRL_DOWN_MASK),

        KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK),
        KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, InputEvent.SHIFT_DOWN_MASK),
        KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK),
        KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, InputEvent.SHIFT_DOWN_MASK),
        KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK),
        KeyStroke.getKeyStroke(KeyEvent.VK_KP_DOWN, InputEvent.SHIFT_DOWN_MASK),
        KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK),
        KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP, InputEvent.SHIFT_DOWN_MASK),
    };

    /**
     * Find NB's DefaultKeyTypedAction for the editor pane, or one for
     * the associated editor kit if the JEP doesn't have one.
     *
     * Turns out only need to save per EditorKit
     *
     * @param ep the editor pane.
     * @return a best guess defaultKeytypedAction.
     */
    public static Action getDefaultKeyAction(JEditorPane ep)
    {
        Action a = epToDefaultKeyAction.get(ep);
        if (a == null) {
            EditorKit kit = ep.getEditorKit();
            a = kitToDefaultKeyAction.get(kit);
        }
        return a;
    }

    private static void putDefaultKeyAction(JEditorPane ep, Action a)
    {
        epToDefaultKeyAction.put(ep, a);
        // Sometimes the ep when install action has the wrong default KTA
        // so kleep track per kit as well
        EditorKit kit = ep.getEditorKit();
        if (kitToDefaultKeyAction.get(kit) == null)
            kitToDefaultKeyAction.put(kit, a);
    }

    public static void checkCaret(JEditorPane ep)
    {
        assert knownEditors.containsKey(ep);
        if (!jViEnabled())
            return;
        if (!(ep.getCaret() instanceof ViCaret)) {
            if (editorToCaret.get(ep) == null) {
                editorToCaret.put(ep, ep.getCaret());
                if (dbgNb())
                    System.err.println(MOD + "capture caret");
            }
            ViManager.getFactory().setupCaret(ep);
        }
    }

    // invoked by filesytem for adding actions to "Editor/Actions" in layer.xml
    public static Action createKBA(Map params) {
        return KeyBinding.getAction((String)params.get("action-name"));
    }

    /**
     * registered in META-INF/services
     *
     * http://www.netbeans.org/issues/show_bug.cgi?id=90403
     */
    public static final class KeybindingsInjector
            extends StorageFilter<Collection<KeyStroke>, MultiKeyBinding>
    {
        // rebuilt/updated when the jvi bindings change
        private static final Map<Collection<KeyStroke>, MultiKeyBinding> mapJvi =
                new HashMap<Collection<KeyStroke>, MultiKeyBinding>();
        Map<String, Map<Collection<KeyStroke>, MultiKeyBinding>> origMaps =
                new HashMap<String, Map<Collection<KeyStroke>, MultiKeyBinding>>();

        @SuppressWarnings("LeakingThisInConstructor")
        public KeybindingsInjector()
        {
            super("Keybindings");
            KB_INJECTOR = this;
            earlyInit();
            if (dbgNb())
                System.err.println("~~~ KeybindingsInjector: " + this);
        }

        void forceKeymapRefresh()
        {
            if (dbgNb())
                System.err.println("Injector: forceKeymapRefresh: ");
            synchronized (mapJvi) {
                mapJvi.clear();
            }
            notifyChanges();
            if (dbgNb())
                System.err.println("Injector: forceKeymapRefresh: done");
        }

        private String createKey(MimePath mimePath, String profile,
                                 boolean defaults)
        {
            String key =
                    mimePath.toString() + ":" + profile + ":" +
                    String.valueOf(defaults);
            return key;
        }

        @Override
        public void afterLoad(Map<Collection<KeyStroke>, MultiKeyBinding> map,
                              MimePath mimePath, String profile,
                              boolean defaults) throws IOException
        {
            if (!jViEnabled())
                return;
            String key = createKey(mimePath, profile, defaults);
            Map<Collection<KeyStroke>, MultiKeyBinding> mapOrig =
                    origMaps.get(key);
            if (mapOrig == null)
                mapOrig =
                        new HashMap<Collection<KeyStroke>, MultiKeyBinding>();
            else
                mapOrig.clear(); // NEEDSWORK: is this the right thing?
                // NEEDSWORK: is this the right thing?
            origMaps.put(key, mapOrig);
            synchronized (mapJvi) {
                if (mapJvi.isEmpty()) {
                    // If needed, build jvi bindings map.
                    List<JTextComponent.KeyBinding> l =
                            KeyBinding.getBindingsList();
                    for (JTextComponent.KeyBinding kb : l) {
                        MultiKeyBinding mkb =
                                new MultiKeyBinding(kb.key, kb.actionName);
                        mapJvi.put(mkb.getKeyStrokeList(),
                                                       mkb);
                    }

                    if (dbgNb())
                        System.err.println("Injector: build jVi map. size " +
                                mapJvi.size());
                }

                List<KeyStroke> ksl =
                        new ArrayList<KeyStroke>();
                ksl.add(null);
                Iterator<MultiKeyBinding> it = map.values().iterator();
                while (it.hasNext()) {
                    MultiKeyBinding mkbOrig = it.next();
                    // ksl is a keyStrokList of the first key of the NB binding
                    ksl.set(0, mkbOrig.getKeyStroke(0));
                    // If the NB binding starts with a jVi binding, then stash it
                    if (mapJvi.get(ksl) != null) {
                        mapOrig.put(mkbOrig.getKeyStrokeList(), mkbOrig);
                        it.remove();
                    }
                }
                if (dbgNb())
                    System.err.println("Injector: afterLoad: " + "mimePath \'" +
                            mimePath + "\' profile \'" + profile + "\' defaults \'" +
                            defaults + "\' orig map size: " + map.size());
                map.putAll(mapJvi);
            }
        }

        @Override
        public void beforeSave(Map<Collection<KeyStroke>, MultiKeyBinding> map,
                               MimePath mimePath, String profile,
                               boolean defaults) throws IOException
        {
            // NEEDSWORK: don't think this is correct
            //              consider if jvi is disabled afterLoad and beforeSave
            if (!jViEnabled())
                return;
            Map<Collection<KeyStroke>, MultiKeyBinding> mapOrig =
                    origMaps.get(createKey(mimePath, profile, defaults));
            //synchronized (mapJvi) {
            //    //
            //    // NEEDSWORK: the map doesn't have the jvi keybindings YET
            //    //
            //    // for(MultiKeyBinding kb : mapJvi.values()) {
            //    //     MultiKeyBinding curKb = map.get(kb.getkeyStrokeList());
            //    //     if(curKb == null)
            //    //     if(!curKb.getActionName().equals(kb.getActionname()))
            //    //     // assert curKb != null : "lost binding";
            //    //     // assert curkb.getActionName().equals(kb.getActionName())
            //    //     //                         : "changed";
            //    //     map.remove(curKb.getKeystrokeList());
            //    // }
            //}
            if (mapOrig != null)
                map.putAll(mapOrig);
            if (dbgNb())
                System.err.println("Injector: beforeSave: " + "mimePath \'" +
                        mimePath + "\' profile \'" + profile + "\' defaults \'" +
                        defaults + "\' orig map size: " + map.size());
                // ARE THERE ISSUES AROUND THE ORIGINAL DEFAULT KEYMAP???
        }
    }

    // registered in layer.xml
    public static class JViInstallAction extends TextAction
    {
        public JViInstallAction()
        {
            super(JVI_INSTALL_ACTION_NAME);
            putValue(BaseAction.NO_KEYBINDING, Boolean.TRUE);
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            addKnownEditor((JEditorPane)e.getSource());
            if (!jViEnabled())
                return;
            JEditorPane ep = (JEditorPane)e.getSource();
            if (dbgNb())
                System.err.printf(MOD + "kit installed: %s into %s\n",
                                  ep.getEditorKit().getClass().getSimpleName(),
                                  cid(ep));
            captureDefaultKeyTypedActionAndEtc(ep);
            // Make sure the nomadic editors have the right cursor.
            checkCaret(ep);
        }
    }

    private KeyBindings()
    {
    }
}
