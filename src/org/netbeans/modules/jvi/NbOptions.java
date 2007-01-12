package org.netbeans.modules.jvi;

import com.raelity.jvi.Option;
import com.raelity.jvi.Options;
import com.raelity.jvi.ViManager;
import java.beans.BeanDescriptor;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Simple class so there is some way to change vi options in the IDE.
 * The bean is its own beanInfo.
 */
public class NbOptions extends SimpleBeanInfo {
    
    public BeanDescriptor getBeanDescriptor() {
        return new BeanDescriptor(NbOptions.class);
    }

    public PropertyDescriptor[] getPropertyDescriptors() {
        List<String> opts = Options.getOptionNamesList();
	PropertyDescriptor[] descriptors = new PropertyDescriptor[opts.size()];
	int i = 0;

	for(String name : opts) {
	    Option opt = Options.getOption(name);
            PropertyDescriptor d;
            try {
                d = new PropertyDescriptor(opt.getName(), NbOptions.class);
            } catch (IntrospectionException ex) {
                ex.printStackTrace();
                continue;
            }
	    d.setDisplayName(opt.getDisplayName());
	    d.setExpert(opt.isExpert());
	    d.setShortDescription(opt.getDesc());
	    descriptors[i++] = d;
	}
        opts = null;
	return descriptors;
    }
    
    //
    // The bean
    //      The bean getter/setter, interface to preferences.
    //
    private Preferences prefs = ViManager.getViFactory().getPreferences();

    private Map map = new HashMap();

    private void put(String name, String val) {
	Option opt = Options.getOption(name);
	prefs.put(name, val);
    }

    private void put(String name, int val) {
	Option opt = Options.getOption(name);
	prefs.putInt(name, val);
    }

    private void put(String name, boolean val) {
	Option opt = Options.getOption(name);
	prefs.putBoolean(name, val);
    }

    private String getString(String name) {
	Option opt = Options.getOption(name);
	return prefs.get(name, opt.getDefault());
    }

    private int getint(String name) {
	Option opt = Options.getOption(name);
	return prefs.getInt(name, Integer.parseInt(opt.getDefault()));
    }

    private boolean getboolean(String name) {
	Option opt = Options.getOption(name);
	return prefs.getBoolean(name, Boolean.parseBoolean(opt.getDefault()));
    }

    public void setViCommandEntryFrameOption(boolean arg) {
        put("viCommandEntryFrameOption", arg);
    }

    public boolean getViCommandEntryFrameOption() {
	return getboolean("viCommandEntryFrameOption");
    }

    public void setViBackspaceWrapPrevious(boolean arg) {
        put("viBackspaceWrapPrevious", arg);
    }

    public boolean getViBackspaceWrapPrevious() {
	return getboolean("viBackspaceWrapPrevious");
    }

    public void setViHWrapPrevious(boolean arg) {
        put("viHWrapPrevious", arg);
    }

    public boolean getViHWrapPrevious() {
	return getboolean("viHWrapPrevious");
    }

    public void setViLeftWrapPrevious(boolean arg) {
        put("viLeftWrapPrevious", arg);
    }

    public boolean getViLeftWrapPrevious() {
	return getboolean("viLeftWrapPrevious");
    }

    public void setViSpaceWrapNext(boolean arg) {
        put("viSpaceWrapNext", arg);
    }

    public boolean getViSpaceWrapNext() {
	return getboolean("viSpaceWrapNext");
    }

    public void setViLWrapNext(boolean arg) {
        put("viLWrapNext", arg);
    }

    public boolean getViLWrapNext() {
	return getboolean("viLWrapNext");
    }

    public void setViRightWrapNext(boolean arg) {
        put("viRightWrapNext", arg);
    }

    public boolean getViRightWrapNext() {
	return getboolean("viRightWrapNext");
    }

    public void setViTildeWrapNext(boolean arg) {
        put("viTildeWrapNext", arg);
    }

    public boolean getViTildeWrapNext() {
	return getboolean("viTildeWrapNext");
    }

    public void setViUnnamedClipboard(boolean arg) {
        put("viUnnamedClipboard", arg);
    }

    public boolean getViUnnamedClipboard() {
	return getboolean("viUnnamedClipboard");
    }

    public void setViJoinSpaces(boolean arg) {
        put("viJoinSpaces", arg);
    }

    public boolean getViJoinSpaces() {
	return getboolean("viJoinSpaces");
    }

    public void setViShiftRound(boolean arg) {
        put("viShiftRound", arg);
    }

    public boolean getViShiftRound() {
	return getboolean("viShiftRound");
    }

    public void setViNotStartOfLine(boolean arg) {
        put("viNotStartOfLine", arg);
    }

    public boolean getViNotStartOfLine() {
	return getboolean("viNotStartOfLine");
    }

    public void setViChangeWordBlanks(boolean arg) {
        put("viChangeWordBlanks", arg);
    }

    public boolean getViChangeWordBlanks() {
	return getboolean("viChangeWordBlanks");
    }

    public void setViTildeOperator(boolean arg) {
        put("viTildeOperator", arg);
    }

    public boolean getViTildeOperator() {
	return getboolean("viTildeOperator");
    }

    public void setViSearchFromEnd(boolean arg) {
        put("viSearchFromEnd", arg);
    }

    public boolean getViSearchFromEnd() {
	return getboolean("viSearchFromEnd");
    }

    public void setViWrapScan(boolean arg) {
        put("viWrapScan", arg);
    }

    public boolean getViWrapScan() {
	return getboolean("viWrapScan");
    }

    public void setViMetaEquals(boolean arg) {
        put("viMetaEquals", arg);
    }

    public boolean getViMetaEquals() {
	return getboolean("viMetaEquals");
    }

    public void setViMetaEscape(String arg) {
        put("viMetaEscape", arg);
    }

    public String getViMetaEscape() {
	return getString("viMetaEscape");
    }

    public void setViIgnoreCase(boolean arg) {
        put("viIgnoreCase", arg);
    }

    public boolean getViIgnoreCase() {
	return getboolean("viIgnoreCase");
    }

    public void setViExpandTabs(boolean arg) {
        put("viExpandTabs", arg);
    }

    public boolean getViExpandTabs() {
	return getboolean("viExpandTabs");
    }

    public void setViReport(int arg) {
        put("viReport", arg);
    }

    public int getViReport() {
	return getint("viReport");
    }

    public void setViBackspace(int arg) {
        put("viBackspace", arg);
    }

    public int getViBackspace() {
	return getint("viBackspace");
    }

    public void setViScrollOff(int arg) {
        put("viScrollOff", arg);
    }

    public int getViScrollOff() {
	return getint("viScrollOff");
    }

    public void setViShiftWidth(int arg) {
        put("viShiftWidth", arg);
    }

    public int getViShiftWidth() {
	return getint("viShiftWidth");
    }

    public void setViTabStop(int arg) {
        put("viTabStop", arg);
    }

    public int getViTabStop() {
	return getint("viTabStop");
    }

    public void setViReadOnlyHack(boolean arg) {
        put("viReadOnlyHack", arg);
    }

    public boolean getViReadOnlyHack() {
	return getboolean("viReadOnlyHack");
    }

    public void setViClassicUndo(boolean arg) {
        put("viClassicUndo", arg);
    }

    public boolean getViClassicUndo() {
	return getboolean("viClassicUndo");
    }

    public void setViDbgKeyStrokes(boolean arg) {
        put("viDbgKeyStrokes", arg);
    }

    public boolean getViDbgKeyStrokes() {
	return getboolean("viDbgKeyStrokes");
    }

    public void setViDbgCache(boolean arg) {
        put("viDbgCache", arg);
    }

    public boolean getViDbgCache() {
	return getboolean("viDbgCache");
    }
}
