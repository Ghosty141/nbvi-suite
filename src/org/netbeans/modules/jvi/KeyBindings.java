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

import org.netbeans.modules.jvi.impl.NbFactory;
import org.netbeans.modules.jvi.impl.NbCaret;
import com.raelity.jvi.ViCaret;
import com.raelity.jvi.ViOutputStream;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.swing.KeyBinding;
import com.raelity.jvi.swing.SwingFactory;
import java.awt.event.ActionEvent;
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
import javax.swing.JEditorPane;
import javax.swing.KeyStroke;
import javax.swing.text.Caret;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.EditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.TextAction;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.MultiKeyBinding;
import org.netbeans.editor.BaseAction;
import org.netbeans.modules.editor.settings.storage.spi.StorageFilter;

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

    static void enableKeyBindings()
    {
        if(Module.isDbgNb())
            System.err.println(Module.MOD + " runJViEnable knownJEP: "
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
        if(Module.isDbgNb())
            System.err.println(Module.MOD + " runJViDisable knownJEP: "
                    + knownEditors.size());

        // restore the carets
        for (JEditorPane ep : knownEditors.keySet()) {
            Caret c01 = editorToCaret.get(ep);
            if(c01 != null) {
                if(ep.getCaret() instanceof NbCaret) {
                    NbFactory.installCaret(ep, c01);
                    if(Module.isDbgNb()) {
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
            System.err.println(Module.MOD + "restore caret: "
                + "HUH? editorToCaret size: " + editorToCaret.size());
        }

        updateKeymap();
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

    static void updateKeymap()
    {
        if (KB_INJECTOR != null) {
            if (Module.isDbgNb())
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
            if (Module.isDbgNb())
                System.err.println(Module.MOD + "capture: "
                        + Module.cid(ep.getEditorKit())
                        + " " + Module.cid(ep) + " action: "
                        + a.getClass().getSimpleName());
        }
        else if (Module.isDbgNb())
            System.err.println(Module.MOD + "ALREADY DKTA: "
                    + Module.cid(ep) + " action: "
                    + a.getClass().getSimpleName());
    }

    /**
     * Find NB's DefaultKeyTypedAction for the edtior pane, or one for
     * the associted editor kit if the JEP doesn't have one.
     *
     * Turns out only need to save per EditorKit
     *
     * @param ep the edtior pane.
     * @return a best guess defaultKeytypedAction.
     */
    static Action getDefaultKeyAction(JEditorPane ep)
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
        if (!Module.jViEnabled())
            return;
        if (!(ep.getCaret() instanceof ViCaret)) {
            if (editorToCaret.get(ep) == null) {
                editorToCaret.put(ep, ep.getCaret());
                if (Module.isDbgNb())
                    System.err.println(Module.MOD + "capture caret");
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
            implements PropertyChangeListener
    {
        private static final MultiKeyBinding KP_UP =
                new MultiKeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP, 0),
                                    "ViUpKey");
        private static final MultiKeyBinding KP_DOWN =
                new MultiKeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_KP_DOWN,
                                                           0), "ViDownKey");
        // rebuilt/updated when the jvi bindings change
        private static final Map<Collection<KeyStroke>, MultiKeyBinding> mapJvi =
                new HashMap<Collection<KeyStroke>, MultiKeyBinding>();
        Map<String, Map<Collection<KeyStroke>, MultiKeyBinding>> origMaps =
                new HashMap<String, Map<Collection<KeyStroke>, MultiKeyBinding>>();

        public KeybindingsInjector()
        {
            super("Keybindings");
            KB_INJECTOR = this;
            Module.earlyInit();
            KeyBinding.addPropertyChangeListener(KeyBinding.KEY_BINDINGS, this);
            if (Module.isDbgNb())
                System.err.println("~~~ KeybindingsInjector: " + this);
        }

        public void propertyChange(PropertyChangeEvent evt)
        {
            if (Module.isDbgNb())
                System.err.println("Injector: change: " + evt.getPropertyName());
            if (evt.getPropertyName().equals(KeyBinding.KEY_BINDINGS))
                // the bindings have changed
                forceKeymapRefresh();
        }

        void forceKeymapRefresh()
        {
            if (Module.isDbgNb())
                System.err.println("Injector: forceKeymapRefresh: ");
            synchronized (mapJvi) {
                mapJvi.clear();
            }
            notifyChanges();
        }

        private String createKey(MimePath mimePath, String profile,
                                 boolean defaults)
        {
            String key =
                    mimePath.toString() + ":" + profile + ":" +
                    String.valueOf(defaults);
            return key;
        }

        private void checkBinding(String tag,
                                  Map<Collection<KeyStroke>, MultiKeyBinding> map)
        {
            for (MultiKeyBinding mkb : map.values()) {
                String match = "";
                if ("caret-up".equals(mkb.getActionName()))
                    match += "ACTION-MATCH: ";
                if (mkb.getKeyStroke(0).
                        equals(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)) ||
                        mkb.getKeyStroke(0).
                        equals(KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP, 0)))
                    match += "KEY-MATCH: ";
                if (!match.equals(""))
                    System.err.println("UP-BINDING: " + tag + ": " + match +
                            "key: " + mkb.getKeyStroke(0) + " " + "action: " +
                            mkb.getActionName());
            }
        }

        @Override
        public void afterLoad(Map<Collection<KeyStroke>, MultiKeyBinding> map,
                              MimePath mimePath, String profile,
                              boolean defaults) throws IOException
        {
            if (!Module.jViEnabled())
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
            // jVi binds VK_UP/DOWN, it doesn't bind VK_KP_UP/DOWN (but they
            // work as they should). However, NB code completion searches for
            // "caret-up" action and uses its keybindings for up in the code
            // completion list. CodeCompletion doesn't find VK_UP, it does find
            // VK_KP_UP so it binds that and VK_UP doesn't work in code
            // completion list. If code completion doesn't find anything, it
            // binds to its default, which is VK_UP. So here we also bind to
            // VK_KP_UP, so CC finds no bindings for "caret-up" and defaults
            // to VK_UP.(This means that VK_KP_UP won't work, sigh.)
            // If interested see editor.completion/src/org/netbeans/modules
            //                      /editor/completion/CompletionScrollPane
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
                    // NEEDSWORK: cleanup
                    mapJvi.put(KP_UP.getKeyStrokeList(),
                                                   KP_UP);
                    mapJvi.put(KP_DOWN.getKeyStrokeList(),
                                                   KP_DOWN);
                    if (Module.isDbgNb())
                        checkBinding("mapJvi", mapJvi);
                    if (Module.isDbgNb())
                        System.err.println("Injector: build jVi map. size " +
                                mapJvi.size());
                }
                if (Module.isDbgNb())
                    checkBinding("mapOrig", map); // (of either mulit or single key binding) is
                // a jvi key, then save the NB binding for use
                // in beforeSave and remove it from map.
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
                if (Module.isDbgNb())
                    System.err.println("Injector: afterLoad: " + "mimePath \'" +
                            mimePath + "\' profile \'" + profile + "\' defaults \'" +
                            defaults + "\' orig map size: " + map.size());
                map.putAll(mapJvi);
            }
            //hackCaptureCheckLater(); // check all opened TC's ep's.
            //hackCaptureCheckLater(); // check all opened TC's ep's.
        }

        @Override
        public void beforeSave(Map<Collection<KeyStroke>, MultiKeyBinding> map,
                               MimePath mimePath, String profile,
                               boolean defaults) throws IOException
        {
            // NEEDSWORK: don't think this is correct
            //              consider if jvi is disabled afterLoad and beforeSave
            if (!Module.jViEnabled())
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
            if (Module.isDbgNb())
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

        public void actionPerformed(ActionEvent e)
        {
            addKnownEditor((JEditorPane)e.getSource());
            if (!Module.jViEnabled())
                return;
            JEditorPane ep = (JEditorPane)e.getSource();
            if (Module.isDbgNb())
                System.err.printf(Module.MOD + "kit installed: %s into %s\n",
                                  ep.getEditorKit().getClass().getSimpleName(),
                                  Module.cid(ep));
            captureDefaultKeyTypedActionAndEtc(ep);
            // Make sure the nomadic editors have the right cursor.
            checkCaret(ep);
        }
    }
}
