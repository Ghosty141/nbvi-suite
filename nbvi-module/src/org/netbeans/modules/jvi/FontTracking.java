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
package org.netbeans.modules.jvi;

import com.raelity.jvi.ViOutputStream;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.core.G;
import com.raelity.text.TextUtil;
import org.netbeans.api.editor.settings.FontColorNames;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.FocusAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JEditorPane;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.EditorStyleConstants;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.editor.FontMetricsCache;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.settings.storage.api.EditorSettings;
import org.netbeans.modules.editor.settings.storage.api.FontColorSettingsFactory;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Lookup;
import org.openide.util.Lookup.Result;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;

import static java.lang.Boolean.*;

/**
 * Check all fonts for the editor.
 *
 * NEEDSWORK: multiple mime-types for a single JEP
 *
 * options.editor/src/org/netbeans/modules/options/
 *              colors/SyntaxColoringPanel.java
 *
 * Logging:
 *          INFO   events for recalc
 *          CONFIG default size params and dialog message to log
 *          FINEST dump all category attributes
 *
 * @author Ernie Rael <err at raelity.com>
 */
final class FontTracking {
    private static final Logger LOG = Logger.getLogger(FontTracking.class.getName());
    private final static Set<String> fontChecked = new HashSet<String>();
    // need to keep strong references to results for listeners
    private static Map<String, Result<FontColorSettings>> results
            = new HashMap<String, Result<FontColorSettings>>();

    private static enum CATS { MIME, ALL }
    private static enum Style {
        Family(StyleConstants.FontFamily),
        Size(StyleConstants.FontSize),
        Bold(StyleConstants.Bold),
        Italic(StyleConstants.Italic);
        private Object key;
        private Style(Object key) {
            this.key = key;
        }
    }

    /** only called once per mimeType,
     * special relationship with JViOptionWarning
     */
    static void monitorMimeType(JEditorPane ep)
    {
        if(G.dbgFonts().getBoolean(Level.FINE))
            G.dbgFonts().println("FONTS: MONITOR FOR ep "
                    + Module.cid(ep) + " doc " + Module.cid(ep.getDocument()));
        monitorMimeType(DocumentUtilities.getMimeType(ep));
        ep.removeFocusListener(fontFocusListener);
        ep.addFocusListener(fontFocusListener);
    }

    private static FocusListener fontFocusListener = new FocusAdapter() {
        @Override
        public void focusGained(final FocusEvent e)
        {
            if(e.getComponent() instanceof JEditorPane) {
                final JEditorPane jep = (JEditorPane)e.getComponent();
                EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run()
                    {
                        focusMimeType(jep);
                    }
                });
            }
        }

    };

    private static void monitorMimeType(final String mimeType)
    {
        if(results.containsKey(mimeType))
            return;
        G.dbgFonts().println(Level.INFO, "FONTS: MONITORING FOR " + getLang(mimeType));
        Lookup lookup = MimeLookup.getLookup(MimePath.get(mimeType));
        Result<FontColorSettings> result
                = lookup.lookupResult(FontColorSettings.class);
        results.put(mimeType, result);

        result.addLookupListener(new LookupListener() {
            @Override
            public void resultChanged(LookupEvent ev) {
                boolean remove = fontChecked.remove(mimeType);
                if(G.dbgFonts().getBoolean(Level.INFO)) {
                    G.dbgFonts().println("FONTS: MIME RESULTS CHANGE="
                            + getLang(mimeType));
                    if(!remove)
                        G.dbgFonts().println("MimeType NOT FOUND");
                }
            }
        });
    }

    private static String getLang(String mimeType)
    {
        StringBuilder sb = new StringBuilder("'");
        sb.append(EditorSettings.getDefault().getLanguageName(mimeType));
        if(sb.length() == 0) {
            sb.append(mimeType.isEmpty() ? "AllLanguages" : "UnknownLanguage");
        }
        if(!mimeType.isEmpty())
            sb.append(" (").append(mimeType).append(")'");
        return sb.toString();
    }

    private static FontColorSettings getFCS(String mimeType)
    {
        return results.get(mimeType).allInstances().iterator().next();
    }

    private static void focusMimeType(JEditorPane ep)
    {
        if(ep.getGraphics() == null)
            return;
        String mimeType = DocumentUtilities.getMimeType(ep);
        focusMimeType(mimeType, ep);
    }

    // NOTE: the assoc'd jtc should be monitored already
    private static FontTracking focusMimeType(String mimeType,
                                              JTextComponent jtc)
    {
        if(fontChecked.contains(mimeType))
            return null;
        fontChecked.add(mimeType);
        // In some cases DocumentUtilities.getMimeType(ep) may return a
        // a different value than the one when the ep was first seen.
        // For example text/xml --> text/x-ant+xml
        if(!results.containsKey(mimeType)) {
            G.dbgFonts().println(Level.FINE, "FONTS: MIME_TYPE CHANGE TO "
                    + getLang(mimeType));
            monitorMimeType(mimeType);
        }

        if(G.dbgFonts().getBoolean(Level.INFO)) {
            String docid = Module.cid(jtc.getDocument());
            G.dbgFonts().println(Level.INFO, "FONTS: CHECKING FOR "
                    + getLang(mimeType) + " ep " + Module.cid(jtc)
                    + " doc " + docid);
        }
        FontTracking ft = new FontTracking(jtc, mimeType);
        ft.init();
        ft.fontCheck();
        return ft;
    }

    /** MAKE public if needed, otherwise this is NOT USED */
    private static void focusMimeType(MimePath mimePath,
                                      JTextComponent jtc)
    {
        List<FontTracking> ftl = new ArrayList<FontTracking>();
        for(int i = 0; i < mimePath.size(); i++) {
            String mimeType = mimePath.getMimeType(i);
            FontTracking ft = focusMimeType(mimeType, jtc);
            if(ft == null)
                continue;
            ftl.add(ft);
        }
        //////////////////////////////////////////////////////////////////////
        // NEEDSWORK:
        //////////////////////////////////////////////////////////////////////
        // Check the ftl's and make sure they are based on the same size.
    }

    private static String name(AttributeSet c)
    {
        return (String)c.getAttribute(StyleConstants.NameAttribute);
    }

    private final Graphics graphics;
    private final String mimeType;
    private final Font componentFont;
    private WH defaultWH;

    private final Map<WH, Set<FontSize>> sizeMap
            = new HashMap<WH, Set<FontSize>>();
    private final Set<AttributeSet> vari = new HashSet<AttributeSet>();
    // following could be made final if move the init code into constructor
    FontColorSettings fcs;
    // Following are only needed to get the list of what to check.
    // Then they are used in detail to track down the problem settings

    private Map<String, AttributeSet> categoriesMime;
    // Following only needed if font sizes don't match, except to get Default
    private Map<String, AttributeSet> categoriesAllLanguages;
    private AttributeSet defaultCategory;

    // following used while create a font
    private FontParams curFontParams;
    private ParamData curParamSource;

    private FontTracking(Component component, String mimeType)
    {
        this.componentFont = component.getFont();
        this.graphics = component.getGraphics();
        this.mimeType = mimeType;
    }

    private void init()
    {
        EditorSettings es = EditorSettings.getDefault();
        String currentProfile = es.getCurrentKeyMapProfile();

        fcs = getFCS(mimeType);
        // Get the categories for the mimetype(s) of the JEP
        categoriesMime = createAttributeMap(mimeType, currentProfile);
        categoriesAllLanguages = createAttributeMap("", currentProfile);

        AttributeSet defaultDefaults = AttributesUtilities.createImmutable(
                Style.Family.key, "Monospaced", // DEFAULT_COLORING overrides
                Style.Size.key, getDefaultSize(), // DEFAULT_COLORING overrides
                Style.Bold.key, Boolean.FALSE,
                Style.Italic.key, Boolean.FALSE); // NOI18N
        defaultCategory = AttributesUtilities.createImmutable(
                fcs.getFontColors(FontColorNames.DEFAULT_COLORING),
                defaultDefaults);

        if(G.dbgFonts().getBoolean(Level.CONFIG)) {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Font Tracking Init: ").append(mimeType).append('\n');
            sb.append("AllLanguages default\n");
            dump(sb, getCategory(CATS.ALL, "default"));
            sb.append("calculated default\n");
            dump(sb, defaultCategory);
            AttributeSet fontColors =
                    fcs.getFontColors(FontColorNames.DEFAULT_COLORING);
            sb.append("FontColorNames.DEFAULT_COLORING:\n");
            dump(sb, fontColors);
            FontSize fs = new FontSize(name(fontColors), componentFont);
            sb.append("Component font: \n    ").append(fs.name).append("  ");
            dumpSize(sb, componentFont, fs.wh).append('\n');
            G.dbgFonts().println(sb.toString());
        }
    }

    private Map<String, AttributeSet> createAttributeMap(String mimeType,
                                                         String profile)
    {
        Map<String, AttributeSet> map = new HashMap<String, AttributeSet>();
        FontColorSettingsFactory fcsf = EditorSettings.getDefault()
                .getFontColorSettings(new String[] { mimeType });
        for(AttributeSet c : fcsf.getAllFontColors(profile)) {
            map.put(name(c), c);
        }
        if(map.isEmpty())
            LOG.log(Level.WARNING,
                    "fcsf.getAllFontColors(profile) empty for {0}",
                    getLang(mimeType));
        return map;
    }

    private void fontCheck()
    {
        StringBuilder sb = new StringBuilder();
        try {
            fontCheckInternal(sb);
        } finally {
            graphics.dispose();
            if(sb.length() != 0) {
                String emsg = sb.toString();
                G.dbgFonts().println(Level.CONFIG, emsg);
                ViOutputStream os = ViManager.createOutputStream(
                        null, ViOutputStream.OUTPUT,
                        "Font Size Problem",
                        ViOutputStream.PRI_LOW);
                os.println(emsg);
                os.close();
                if(!G.disableFontError()) {
                    NotifyDescriptor d = new NotifyDescriptor.Message(
                            emsg, NotifyDescriptor.WARNING_MESSAGE);
                    d.setTitle("jVi Warning");
                    DialogDisplayer.getDefault().notifyLater(d);
                }
            }
        }
    }

    private void fontCheckInternal(StringBuilder sb)
    {
        if(G.dbgFonts().getBoolean(Level.FINEST)) {
            dump(getLang(mimeType), categoriesMime.values());
            dump("All languages", categoriesAllLanguages.values());
            dumpSource(getLang(mimeType) + "resolve", categoriesMime.values());
            dumpSource("All Languages resolve", categoriesMime.values());
            List<AttributeSet> l01 = new ArrayList<AttributeSet>();
            for(String name : categoriesMime.keySet()) {
                AttributeSet c01 = fcs.getTokenFontColors(name);
                l01.add(c01);
            }
            dump("FCS:" + getLang(mimeType), l01);
        }

        {
            int style = TRUE.equals(defaultCategory.getAttribute(Style.Bold.key))
                        ? Font.BOLD : Font.PLAIN;
            if(TRUE.equals(defaultCategory.getAttribute(Style.Italic.key)))
                        style += Font.ITALIC;
            FontSize fsDefault = new FontSize(name(defaultCategory), new Font(
                    (String)defaultCategory.getAttribute(Style.Family.key),
                    style,
                    (Integer)defaultCategory.getAttribute(Style.Size.key)));
            defaultWH = fsDefault.wh;
        }

        for(Entry<String, AttributeSet> entry : categoriesMime.entrySet()) {
            Font f = getFontFCS(entry.getKey());
            FontSize fs = new FontSize(name(entry.getValue()), f);
            if(!fs.isFixed) {
                vari.add(entry.getValue());
                continue;
            }
            if(!sizeMap.containsKey(fs.wh)) {
                sizeMap.put(fs.wh, new HashSet<FontSize>());
            }
            Set<FontSize> s = sizeMap.get(fs.wh);
            s.add(fs);
        }

        if(vari.isEmpty() && sizeMap.size() == 1)
            return;

        // if there is anything in "vari" or there's more
        // than one size, then there's a problem.
        //
        // Where there are problems, determine the base of the resolve tree
        // and weed out duplicates
        //
        FontParams fp;
        Font f;
        if(!vari.isEmpty() || sizeMap.size() > 1) {
            sb.append("jVi works best with fixed size fonts.\n")
              .append("\nFont size problem for Language: ")
                    .append(getLang(mimeType)).append("\n");
        }
        if(!vari.isEmpty()) {
            Set<ParamSource> roots = new HashSet<ParamSource>();
            for(AttributeSet c : vari) {
                fp = new FontParams();
                f = getFont(c, fp);
                roots.add(fp.get(Style.Family).ps);
            }
            sb.append("\nThe following specify a variable size font:\n");
            for(ParamSource ps : roots) {
                sb.append("    ");
                dump(sb, ps).append('\n');
            }
        }
        if(sizeMap.size() > 1) {
            WH wh01 = defaultWH;
            Set<FontSizeParams> roots = new HashSet<FontSizeParams>();
            FontSize fs;
            Set<FontSize> fsSet;
            fsSet = sizeMap.get(wh01);
            if(fsSet == null) {
                sb.append("\nNo font has default font dimensions: ")
                  .append(wh01.w).append('x').append(wh01.h)
                  .append(". Impossible?");
                return;
            }
            fs = fsSet.iterator().next();
            fp = new FontParams();
            f = getFont(getCategory(CATS.MIME, fs.name), fp);
            sb.append("\nThe default font is size ");
            dumpSize(sb, f, wh01).append('\n');
            roots.clear();
            for(WH wh : sizeMap.keySet()) {
                if(wh.equals(wh01))
                    continue;
                fsSet = sizeMap.get(wh);
                for(FontSize fs01 : fsSet) {
                    fp = new FontParams();
                    f = getFont(getCategory(CATS.MIME, fs01.name), fp);
                    roots.add(new FontSizeParams(fp.get(Style.Family),
                                                 fp.get(Style.Size)));
                }
            }
            sb.append("\nThe following generate a different size font:\n");
            for(FontSizeParams fsp : roots) {
                sb.append("\n  = ");
                sb.append("Family ");
                sb.append(String.format("%-20s", fsp.family.val));
                dump(sb, fsp.family.ps).append(" ");
                dumpSize(sb, fsp.family.ps, wh01).append('\n');
                sb.append("    ");
                sb.append("  Size ");
                sb.append(String.format("%-20s", fsp.size.val));
                dump(sb, fsp.size.ps).append(" ");
                dumpSize(sb, fsp.size.ps, wh01).append('\n');
            }
        }
    }

    /**
     * This version of getFont() uses public methods to determine the font;
     * it lets the API do the parent resolution.
     * It is used to check if there are font size problems.
     */
    private Font getFontFCS (String token) {
        AttributeSet category = fcs.getTokenFontColors(token);
        String name = (String)category.getAttribute (Style.Family.key);
        if (name == null) {
            name = (String)defaultCategory.getAttribute(Style.Family.key);
        }
        Integer size = (Integer)category.getAttribute (Style.Size.key);
        if (size == null) {
            size = (Integer)defaultCategory.getAttribute(Style.Size.key);
        }
        Boolean bold = (Boolean)category.getAttribute (Style.Bold.key);
        if (bold == null) {
            bold = (Boolean)defaultCategory.getAttribute(Style.Bold.key);
        }
        Boolean italic = (Boolean)category.getAttribute (Style.Italic.key);
        if (italic == null) {
            italic = (Boolean)defaultCategory.getAttribute(Style.Italic.key);
        }
        int style = bold.booleanValue () ? Font.BOLD : Font.PLAIN;
        if (italic.booleanValue ()) style += Font.ITALIC;
        return new Font (name, style, size.intValue ());
    }

    private void dump(String tag, Collection<AttributeSet> attrs)
    {
        G.dbgFonts().println("========== " + tag + " ==========");
        StringBuilder sb = new StringBuilder();
        for(AttributeSet c : attrs) {
            sb.setLength(0);
            dump(sb, c);
            G.dbgFonts().println(sb.toString());
        }
    }

    private void dump(StringBuilder sb, AttributeSet c)
    {
        sb.append("    ")
          .append(name(c))
          .append("  ").append(getFont(c)).append('\n');
        Enumeration<?> attributeNames = c.getAttributeNames();
        while(attributeNames.hasMoreElements()) {
            Object o = attributeNames.nextElement();
            sb.append("        ")
              .append(o).append(":").append(c.getAttribute(o)).append('\n');
        }
    }

    private void dumpSource(String tag, Collection<AttributeSet> attrs)
    {
        G.dbgFonts().println("========== " + tag + " ==========");
        StringBuilder sb = new StringBuilder();
        for(AttributeSet c : attrs) {
            sb.setLength(0);
            dumpSource(sb, c);
            G.dbgFonts().println(sb.toString());
        }
    }

    private void dumpSource(StringBuilder sb, AttributeSet c)
    {
        FontParams fp = new FontParams();
        Font f = getFont(c, fp);
        FontSize fs = new FontSize(name(c), f);
        sb.append(String.format("%-35s %s %s",
                name(c),
                dumpSize(sb, f, fs.wh).toString(), f))
          .append('\n');
        dump(sb, fp);
    }

    private void dump(StringBuilder sb, FontParams fp)
    {
        for(Style style : Style.values()) {
            dump(sb, fp, style);
        }
    }

    private StringBuilder dump(StringBuilder sb, ParamSource ps)
    {
        String name;
        String lang;
        if(ps.cats == null && ps.categoryName == null) {
            name = "none";
            lang = "none";
        } else {
            name = (String)getCategory(ps.cats, ps.categoryName)
                    .getAttribute(EditorStyleConstants.DisplayName);
            lang = getLang(ps.cats == CATS.MIME ? mimeType : "");
        }
        sb.append(String.format("%30s/%s", name, lang));
        return sb;
    }

    private StringBuilder dumpSize(StringBuilder sb, ParamSource ps)
    {
        return dumpSize(sb, ps, null);
    }

    private StringBuilder dumpSize(StringBuilder sb, Font f, WH wh)
    {
        return dumpSize(sb, f, wh, null);
    }

    private StringBuilder dumpSize(StringBuilder sb, ParamSource ps, WH expect)
    {
        AttributeSet c = getCategory(ps.cats, ps.categoryName);
        Font f = getFont(c);
        FontSize fs = new FontSize(name(c), f);
        return dumpSize(sb, f, fs.wh, expect);
    }

    private StringBuilder dumpSize(StringBuilder sb, Font f, WH wh, WH expect)
    {
        sb.append(f.getSize()).append(" (fm=")
          .append(wh.w).append('x').append(wh.h);
        if(G.dbgFonts().getBoolean()) {
            sb.append(", nWidth ").append(wh.nWidth);
            if(wh.c01 != 0 || wh.w01 != 0 && wh.c02 != 0 || wh.w02 != 0) {
                sb.append(", ").append("'")
                  .append(TextUtil.debugString(String.valueOf(wh.c01)))
                  .append("' ").append(wh.w01)
                  .append(", ").append("'")
                  .append(TextUtil.debugString(String.valueOf(wh.c02)))
                  .append("' ").append(wh.w02);
            }
        }
        sb.append(')');
        if(expect != null && !wh.equals(expect))
            sb.append(" ***");
        return sb;
    }

    private void dump(StringBuilder sb, FontParams fp, Style s)
    {
        ParamData pd = fp.get(s);
        String sKey = s.toString();

        sb.append("    ");
        sb.append(String.format("%-10s %-10s ", sKey, pd.val));
        dump(sb, pd.ps);
        if(pd.name != null) {
            // resolved by getDefaults Defaults
            sb.append(" (getDefaults ").append(pd.name).append(')');
        }
        sb.append('\n');
    }

    private Font getFont (AttributeSet category, FontParams fp) {
        curFontParams = fp;
        try {
            return getFont(category);
        } finally {
            curFontParams = null;
        }
    }

    private Font getFont (AttributeSet category) {
        String name = (String) getValue (CATS.MIME, category, Style.Family);
        if (name == null) {
            name = (String)defaultCategory.getAttribute(Style.Family.key);
            addFontParam(Style.Family, null, null, name);
        }                        // NOI18N
        Integer size = (Integer) getValue (CATS.MIME, category, Style.Size);
        if (size == null) {
            size = (Integer)defaultCategory.getAttribute(Style.Size.key);
            addFontParam(Style.Size, null, null, size);
        }
        Boolean bold = (Boolean) getValue (CATS.MIME, category, Style.Bold);
        if (bold == null) {
            bold = (Boolean)defaultCategory.getAttribute(Style.Bold.key);
            addFontParam(Style.Bold, null, null, bold);
        }
        Boolean italic = (Boolean) getValue (CATS.MIME, category, Style.Italic);
        if (italic == null) {
            italic = (Boolean)defaultCategory.getAttribute(Style.Italic.key);
            addFontParam(Style.Italic, null, null, italic);
        }
        int style = bold.booleanValue () ? Font.BOLD : Font.PLAIN;
        if (italic.booleanValue ()) style += Font.ITALIC;
        return new Font (name, style, size.intValue ());
    }

    private Object getValue (CATS cats, AttributeSet category, Style s) {
        if (category.isDefined (s.key)) {
            Object value = category.getAttribute (s.key);
            addFontParam(s, cats, category, value);
            return value;
        }
        return getDefault (cats, category, s);
    }

    private Object getDefault (CATS cats, AttributeSet category, Style s) {
	String name = (String) category.getAttribute (EditorStyleConstants.Default);
	if (name == null) name = "default";

	// 1) search current language
        if (!name.equals (name(category))) {
            AttributeSet defaultAS = getCategory(cats, name);
            if (defaultAS != null)
                return getValue (cats, defaultAS, s);
        }

        // 2) search default language
        if(cats != CATS.ALL) {
            AttributeSet defaultAS = getCategory(CATS.ALL, name);
            if (defaultAS != null)
                return getValue(CATS.ALL, defaultAS, s);
        }

        Object value = null;
        if (s == Style.Family) {
            value = (String)defaultCategory.getAttribute(Style.Family.key);
        } else if (s == Style.Size) {
            value = (Integer)defaultCategory.getAttribute(Style.Size.key);
        }
        if(value != null)
            addFontParam(s, cats, category, value, name);
        return value;
    }

    // only called once, and not really used
    private static Integer getDefaultSize () {
        Integer defaultFontSize;
        defaultFontSize = (Integer) UIManager.get("customFontSize"); // NOI18N
        if (defaultFontSize == null) {
            int s = UIManager.getFont ("TextField.font").getSize (); // NOI18N
            // Options Dialog, Font&Coloring uses 12,
            // but CompositeFCS.getHardcodedDefaultColoring uses 13
            // Bug 200450 - how to determine what fonts are in use for editor
            if (s < 13) s = 13;
            defaultFontSize = new Integer (s);
        }
        return defaultFontSize;
    }

    private Map<String, AttributeSet> getCategories(CATS cats)
    {
        return cats == CATS.MIME ? categoriesMime : categoriesAllLanguages;
    }

    // NEEDSWORK: make map
    private AttributeSet getCategory (CATS cats, String name) {
        return getCategories(cats).get(name);
    }

    private void addFontParam(Style s, CATS cats,
                              AttributeSet cat, Object val)
    {
        if(curFontParams == null)
            return;
        curFontParams.put(s, new ParamData(cats, cat, val, null));
    }

    private void addFontParam(Style s, CATS cats,
                              AttributeSet cat, Object val, String name)
    {
        if(curFontParams == null)
            return;
        curFontParams.put(s, new ParamData(cats, cat, val, name));
    }

    private static class ParamSource {
        final CATS cats;           // either mime or AllLang or null if default
        final String categoryName; // like indent, number, keyword, ...

        public ParamSource(CATS cats, String categoryName)
        {
            this.cats = cats;
            this.categoryName = categoryName;
        }

        //<editor-fold defaultstate="collapsed" desc="equals() & hashCode()">
        @Override
        public boolean equals(Object obj)
        {
            if(obj == null) {
                return false;
            }
            if(getClass() != obj.getClass()) {
                return false;
            }
            final ParamSource other = (ParamSource)obj;
            if(this.cats != other.cats) {
                return false;
            }
            if(this.categoryName != other.categoryName &&
                    (this.categoryName == null ||
                    !this.categoryName.equals(other.categoryName))) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode()
        {
            int hash = 3;
            hash = 59 * hash + (this.cats != null ? this.cats.hashCode() : 0);
            hash =
                    59 * hash +
                    (this.categoryName != null ? this.categoryName.hashCode() : 0);
            return hash;
        }
        //</editor-fold>

        @Override
        public String toString()
        {
            return "ParamSource{" + "cats=" + cats + ", categoryName=" +
                    categoryName + '}';
        }

    }

    private static class ParamData {
        final ParamSource ps; // mime-allLang, categoryName
        final Object val;
        final String name;         // if getDefault

        public ParamData(CATS cats, AttributeSet cat, Object val, String name)
        {
            // this.cats = cats;
            // this.categoryName = cat != null
            //             ? cat.getAttribute(StyleConstants.NameAttribute) : null;
            this.ps = new ParamSource(cats,
                    cat != null ? name(cat) : null);
            this.val = val;
            this.name = null;
        }

        //<editor-fold defaultstate="collapsed" desc="equals() & hashCode()">
        @Override
        public boolean equals(Object obj)
        {
            if(obj == null) {
                return false;
            }
            if(getClass() != obj.getClass()) {
                return false;
            }
            final ParamData other = (ParamData)obj;
            if(this.ps != other.ps &&
                    (this.ps == null || !this.ps.equals(other.ps))) {
                return false;
            }
            if(this.val != other.val &&
                    (this.val == null || !this.val.equals(other.val))) {
                return false;
            }
            if((this.name == null) ? (other.name != null)
                    : !this.name.equals(other.name)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode()
        {
            int hash = 5;
            hash = 59 * hash + (this.ps != null ? this.ps.hashCode() : 0);
            hash = 59 * hash + (this.val != null ? this.val.hashCode() : 0);
            hash = 59 * hash + (this.name != null ? this.name.hashCode() : 0);
            return hash;
        }
        //</editor-fold>

        @Override
        public String toString()
        {
            return "ParamData{" + "ps=" + ps + ", val=" + val + ", name=" + name +
                    '}';
        }

    }

    private static class FontSizeParams {
        private final ParamData family;
        private final ParamData size;

        FontSizeParams(ParamData family, ParamData size)
        {
            this.family = family;
            this.size = size;
        }

        //<editor-fold defaultstate="collapsed" desc="equals() & hashCode()">
        @Override
        public boolean equals(Object obj)
        {
            if(obj == null) {
                return false;
            }
            if(getClass() != obj.getClass()) {
                return false;
            }
            final FontSizeParams other = (FontSizeParams)obj;
            if(this.family != other.family &&
                    (this.family == null || !this.family.equals(other.family))) {
                return false;
            }
            if(this.size != other.size &&
                    (this.size == null || !this.size.equals(other.size))) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode()
        {
            int hash = 7;
            hash =
                    53 * hash +
                    (this.family != null ? this.family.hashCode() : 0);
            hash = 53 * hash + (this.size != null ? this.size.hashCode() : 0);
            return hash;
        }
        //</editor-fold>

    }

    private static class WH {
        private final int w;
        private final int h;
        // NOTE, following not part of equals/hashcode
        private final int nWidth;
        private final char c01;
        private final int w01;
        private final char c02;
        private final int w02;

        public WH(int w, int h, int nWidth)
        {
            this(w, h, nWidth, '\0', 0, '\0', 0);
        }

        public WH(int w, int h, int nWidth, char c01, int w01, char c02, int w02)
        {
            this.w = w;
            this.h = h;
            this.nWidth = nWidth;
            this.c01 = c01;
            this.w01 = w01;
            this.c02 = c02;
            this.w02 = w02;
        }

        //<editor-fold defaultstate="collapsed" desc="equals() & hashCode()">
        @Override
        public boolean equals(Object obj)
        {
            if(obj == null) {
                return false;
            }
            if(getClass() != obj.getClass()) {
                return false;
            }
            final WH other = (WH)obj;
            if(this.w != other.w) {
                return false;
            }
            if(this.h != other.h) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode()
        {
            int hash = 7;
            hash = 29 * hash + this.w;
            hash = 29 * hash + this.h;
            return hash;
        }
        //</editor-fold>

        @Override
        public String toString()
        {
            return "WH{" + "w=" + w + ", h=" + h + '}';
        }

    }

    private static Map<FontMetrics, FontMetricsData> fontMetricsDataCache
            = new HashMap<FontMetrics, FontMetricsData>();

    private static class FontMetricsData {
        private final WH wh;
        private final boolean isFixed;

        public FontMetricsData(WH wh, boolean isFixed)
        {
            this.wh = wh;
            this.isFixed = isFixed;
        }
    }

    private class FontSize {
        private final WH wh;
        private final String name;
        private final boolean isFixed;

        public FontSize(String name, Font f)
        {
            this.name = name;
            FontMetrics fm = FontMetricsCache.getFontMetrics(f, graphics);
            FontMetricsData fmd = fontMetricsDataCache.get(fm);
            if(fmd != null) {
                wh = fmd.wh;
                isFixed = fmd.isFixed;
            } else {
                // can't use font family to determine fixed width
                // so if first 'n' characters are the same size (except 0)
                // then assume fixed width
                boolean flag = true;
                char c01 = 0;
                int w01 = 0;
                char c02 = 0;
                int w02 = 0;
                int[] widths = fm.getWidths();
                for(int i = 0; i < widths.length; i++) {
                    int w = widths[i];
                    if(w == 0)
                        continue;
                    if(w01 == 0) {
                        c01 = (char)i;
                        w01 = w;
                    }
                    if(w01 != w) {
                        c02 = (char)i;
                        w02 = w;
                        flag = false;
                        break;
                    }
                }
                isFixed = flag;
                wh = isFixed ? new WH(w01, fm.getHeight(), widths.length)
                             : new WH(0,0, widths.length, c01, w01, c02, w02);
                fontMetricsDataCache.put(fm, new FontMetricsData(wh, isFixed));
            }
        }

        //<editor-fold defaultstate="collapsed" desc="equals() & hashCode()">
        @Override
        public boolean equals(Object obj)
        {
            if(obj == null) {
                return false;
            }
            if(getClass() != obj.getClass()) {
                return false;
            }
            final FontSize other = (FontSize)obj;
            if(this.wh != other.wh &&
                    (this.wh == null || !this.wh.equals(other.wh))) {
                return false;
            }
            if(this.name != other.name &&
                    (this.name == null ||
                    !this.name.equals(other.name))) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode()
        {
            int hash = 7;
            hash = 97 * hash + (this.wh != null ? this.wh.hashCode() : 0);
            hash =
                    97 * hash +
                    (this.name != null ? this.name.hashCode() : 0);
            return hash;
        }
        //</editor-fold>

    }

    //<editor-fold defaultstate="collapsed" desc="class FontParams implements Map<Style, ParamData>">
    private static final class FontParams implements Map<Style, ParamData> {
        private final Map<Style, ParamData> map = new EnumMap<Style, ParamData>(Style.class);

        @Override
        public String toString()
        {
            return map.toString();
        }

        @Override
        public Collection<ParamData> values()
        {
            return map.values();
        }

        @Override
        public int size()
        {
            return map.size();
        }

        @Override
        public ParamData remove(Object key)
        {
            return map.remove(key);
        }

        @Override
        public void putAll(Map<? extends Style, ? extends ParamData> m)
        {
            map.putAll(m);
        }

        @Override
        public ParamData put(Style key, ParamData value)
        {
            return map.put(key, value);
        }

        @Override
        public Set<Style> keySet()
        {
            return map.keySet();
        }

        @Override
        public boolean isEmpty()
        {
            return map.isEmpty();
        }

        @Override
        public int hashCode()
        {
            return map.hashCode();
        }

        @Override
        public ParamData get(Object key)
        {
            return map.get(key);
        }

        @Override
        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        public boolean equals(Object o)
        {
            return map.equals(o);
        }

        @Override
        public Set<Entry<Style, ParamData>> entrySet()
        {
            return map.entrySet();
        }

        @Override
        public boolean containsValue(Object value)
        {
            return map.containsValue(value);
        }

        @Override
        public boolean containsKey(Object key)
        {
            return map.containsKey(key);
        }

        @Override
        public void clear()
        {
            map.clear();
        }

    }
    //</editor-fold>

}
