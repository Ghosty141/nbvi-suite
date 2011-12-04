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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
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

/**
 *
 * @author Ernie Rael <err at raelity.com>
 */
class NbJviPrefs
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
        String mimeType = null;
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
}
