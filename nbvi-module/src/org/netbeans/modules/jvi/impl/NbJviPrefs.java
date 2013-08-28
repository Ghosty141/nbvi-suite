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
 * Copyright (C) 2011 Ernie Rael.  All Rights Reserved.
 *
 * Contributor(s): Ernie Rael <err@raelity.com>
 */
package org.netbeans.modules.jvi.impl;

import java.awt.EventQueue;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.SimpleValueNames;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.editor.indent.spi.CodeStylePreferences;
import org.netbeans.modules.jvi.JViOptionWarning;

import com.raelity.jvi.manager.ViManager;

/**
 * public for jviDisabling HACK
 *
 * @author Ernie Rael <err at raelity.com>
 */
public class NbJviPrefs
{
    //private static final Logger LOG = Logger.getLogger(NbJviPrefs.class.getName());
    private NbJviPrefs() { }

    private static enum I {
        // The type of the preference
        INT,
        BOOL,
        STRING,
        // where to set the preference
        STYLE,          // uses CodeStylePreferences, this is indent related
        MIME,           // uses mimeType
        GLOBAL,         // global pref
        // special handling
        WRAP,           // line wrap
    }

    private static final Map<String, Set<I>> prefType = new HashMap<String, Set<I>>();
    static {
        // the code style prefs. It is not documented what they are.
        // look at editor.indent/.../IndentUtils
        prefType.put(SimpleValueNames.TAB_SIZE, EnumSet.of(I.INT, I.STYLE));
        prefType.put(SimpleValueNames.SPACES_PER_TAB, EnumSet.of(I.INT, I.STYLE));
        prefType.put(SimpleValueNames.EXPAND_TABS, EnumSet.of(I.BOOL, I.STYLE));
        prefType.put(SimpleValueNames.INDENT_SHIFT_WIDTH, EnumSet.of(I.INT, I.STYLE));

        prefType.put(SimpleValueNames.CARET_BLINK_RATE, EnumSet.of(I.INT, I.MIME));

        prefType.put(SimpleValueNames.LINE_NUMBER_VISIBLE, EnumSet.of(I.BOOL, I.MIME));
        prefType.put(SimpleValueNames.NON_PRINTABLE_CHARACTERS_VISIBLE, EnumSet.of(I.BOOL, I.MIME));

        // not sure TEXT_LINE_WRAP scope. But it responds to propertes.
        prefType.put(SimpleValueNames.TEXT_LINE_WRAP, EnumSet.of(I.STRING, I.GLOBAL, I.WRAP));
    }

    static void putPrefs(Map<String, Object> put, NbTextView tv, NbBuffer b)
    {
        String wrapKey = "";
        String wrapVal = "";
        // NEEDSWORK: only do the "put" if something changed
        JViOptionWarning.setInternalAction(true);
        try {
            for(Map.Entry<String, Object> e : put.entrySet()) {
                Set<I> info = prefType.get(e.getKey());
                if(b == null && tv != null)
                    b = (NbBuffer)tv.getBuffer();
                Preferences prefs = info.contains(I.STYLE) ? getStylePrefs(b)
                        : info.contains(I.MIME) ? getMimePrefs(tv, b)
                        : getGlobalMimePrefs();
                if(prefs == null) {
                    Logger.getLogger(NbJviPrefs.class.getName()).log(Level.SEVERE,
                                     "No preferences for {0} tv={1}, b={2}",
                                     new Object[] {e.getKey(), tv, b});
                    continue;
                }
                if(info.contains(I.INT))
                    prefs.putInt(e.getKey(), (Integer)e.getValue());
                else if(info.contains(I.BOOL))
                    prefs.putBoolean(e.getKey(), (Boolean)e.getValue());
                else if(info.contains(I.STRING))
                    prefs.put(e.getKey(), (String)e.getValue());
                else
                    assert false;
                if(info.contains(I.WRAP)) {
                    wrapKey = e.getKey();
                    wrapVal = (String)e.getValue();
                }
            }
            if(!wrapKey.isEmpty())
                setWrapPref(tv, wrapKey, wrapVal);
        } finally {
            JViOptionWarning.setInternalAction(false);
        }
    }

    static Preferences getStylePrefs(NbBuffer b)
    {
        return CodeStylePreferences.get(b.getDocument()).getPreferences();
    }

    static Preferences getGlobalMimePrefs()
    {
        return MimeLookup.getLookup(MimePath.EMPTY).lookup(Preferences.class);
    }

    /**
     * Get the Preferences node for the mime type associated with tv.
     * If tv is null, then use mime type of the document.
     * @param tv
     * @return
     */
    static Preferences getMimePrefs(NbTextView tv, NbBuffer b)
    {
        if(tv == null && b == null)
            return getGlobalMimePrefs();
        Preferences prefs = null;
        String mimeType;
        JTextComponent jtc = null;
        Document d = null;
        if(tv != null)
            jtc = tv.getEditor();
        else if(b != null)
            d = b.getDocument();
        mimeType = jtc != null
                ? NbEditorUtilities.getMimeType(jtc)
                : NbEditorUtilities.getMimeType(d);
        if(mimeType != null) {
            addUsedMime(mimeType);
            prefs = MimeLookup.getLookup(
                    MimePath.parse(mimeType)).lookup(Preferences.class);
        }
        return prefs;
    }

    private static void setWrapPref(final NbTextView tv,
                                    final String key, final String value)
    {
        EventQueue.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                // NOTE: not spinning through the editor registry.
                //       This gives per text view control (unlike stock NB editor)
                // Needs to check for null since it might be closed
                // by the time we get here.
                if(tv.getEditor() == null
                        || tv.getEditor().getDocument() == null)
                    return;
                tv.getEditor().putClientProperty(key, value);
                //tv.getEditor().getDocument().putProperty(key, value);
            }
        });
    }

    // keep used mime prefs as a colon separated list
    private static final String USED_MIME = "used_mime_prefs";
    private static void addUsedMime(String mt)
    {
        Set<String> s = getUsedMime();
        if(s.add(mt)) {
            setUsedMime(s);
            // System.err.printf("NbJviPrefs: addUsedMime %s\n", mt);
        }
    }

    private static Set<String> getUsedMime()
    {
        Set<String> s = new HashSet<String>();

        String[] mimes = ViManager.getFactory().getPreferences()
                                .get(USED_MIME, "").split(":");
        s.addAll(Arrays.asList(mimes));

        return s;
    }

    private static void setUsedMime(Set<String> s)
    {
        StringBuilder sb = new StringBuilder();
        for(String mt : s) {
            if(sb.length() != 0)
                sb.append(':');
            sb.append(mt);
        }

        ViManager.getFactory().getPreferences().put(USED_MIME, sb.toString());
    }

    public static void jviDisabling()
    {
        for(String mt : getUsedMime()) {
            Preferences prefs;
            prefs = MimeLookup.getLookup(
                    MimePath.parse(mt)).lookup(Preferences.class);
            if(prefs != null) {
                // System.err.printf("NbJviPrefs: removeMimeTypePrefs %s\n", mt);
                for(Entry<String, Set<I>> entry : prefType.entrySet()) {
                    if(entry.getValue().contains(I.MIME)) {
                        prefs.remove(entry.getKey());
                    }
                }
            }
        }
        ViManager.getFactory().getPreferences().remove(USED_MIME);
    }
}
