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
 * Copyright (C) 2000 Ernie Rael.  All Rights Reserved.
 *
 * Contributor(s): Ernie Rael <err@raelity.com>
 */

package org.netbeans.modules.jvi;

import com.raelity.jvi.lib.MutableBoolean;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.JEditorPane;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.SimpleValueNames;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.windows.WindowManager;

/**
 * This class monitors particular preference changes, and gives a warning
 * if they are changed outside of jVi.
 * <p>
 * Use monitorMimeType to add a mimeType. And use clear to stop monitoring,
 * all resources are cleared.
 * The monitoring can be temporarily suspended using setInternalAction.
 * </p>
 * @author  err
 */
public class JViOptionWarning {

    // NEEDSWORK: put text into a non-edit text area, or html area
    // to allow copy of web site.

    static private String getWarningMessage() {
        return "ShiftWidth, ExpandTabs, TabStop, LineWrap\n"
             + "and other options for jVi are set through\n"
             + "Tools>Options>jVi Config>Buffer Modifications.\n"
             + "and Tools>Options>jVi Config>General.\n"
             + "Also, can use \":set ...\" after file is opened.\n"
             + "Do \":set all\" to see options for \":set\"\n\n"
             + "See http://jvi.sourceforge.net/ReadmeNetBeans.html#options\n\n"
             + "jVi reprograms some options, including indent/tab/linewrap,\n"
             + "per document (not globally); changes made in standard\n"
             + "NetBeans settings are lost and not used.\n\n"
             + "See also \"modeline\" in the vim documentation\n"
             + "to persist per file settings.";
    }

    private static DialogDescriptor warning;
    private static Dialog dialog;
    private static Date lastShowTime = new Date(0);
    private static PreferenceChangeListener scl;
    private final static Set<MimePath> mimePaths = new HashSet<MimePath>();

    private static final MutableBoolean
            isInternalSetting = new MutableBoolean();

    static void monitorMimeType(JEditorPane ep)
    {
        synchronized(mimePaths) {
            MimePath mimePath
                    = MimePath.parse(NbEditorUtilities.getMimeType(ep));
            if(mimePaths.add(mimePath)) {
                if(scl == null)
                    scl = new TabSetListener();
                Preferences prefs = MimeLookup.getLookup(mimePath)
                        .lookup(Preferences.class);
                prefs.addPreferenceChangeListener(scl);
            }
        }
    }

    /**
     * Enable or disable the tab warning feature.
     * @param enableFlag
     */
    static void clear() {
        synchronized(mimePaths) {
            for (MimePath mimePath : mimePaths) {
                Preferences prefs = MimeLookup.getLookup(mimePath)
                                        .lookup(Preferences.class);
                prefs.removePreferenceChangeListener(scl);
            }
            scl = null;
        }
    }

    private static int noDrawTime = 1;//5 * 60 * 1000;
    /**
     * If the preference is one we care about, and its not changed by jVi
     * and its been 5 minutes since we've warned, then show the warning.
     */
    private static class TabSetListener implements PreferenceChangeListener {
        @Override
        public void preferenceChange(PreferenceChangeEvent evt) {
            // workaround for Issue 142723
            //      "preference change events when nothing changes"
            // if(evt == null || evt.getNewValue() == null)
            //     return;

            String settingName = evt == null ? null : evt.getKey();
            if(!isInternalSetting.getValue()
               && (settingName == null
                   || SimpleValueNames.SPACES_PER_TAB.equals(settingName)
                   || SimpleValueNames.INDENT_SHIFT_WIDTH.equals(settingName)
                   || SimpleValueNames.EXPAND_TABS.equals(settingName)
                   || SimpleValueNames.TAB_SIZE.equals(settingName)
                   || SimpleValueNames.CARET_BLINK_RATE.equals(settingName)
                   || SimpleValueNames.LINE_NUMBER_VISIBLE.equals(settingName)
                   || SimpleValueNames.NON_PRINTABLE_CHARACTERS_VISIBLE.equals(settingName)
                   || SimpleValueNames.TEXT_LINE_WRAP.equals(settingName)
                  )
               && new Date().getTime() - lastShowTime.getTime() > noDrawTime
               ) {

                // Note there may be lots of events, but only one dialog
                if(warning == null) {
                    EventQueue.invokeLater(new CreateDisplayTabWarning());
                }
            }
        }
    }

    private static class CreateDisplayTabWarning implements Runnable
    {
        @Override
        public void run()
        {
            if (warning == null) {
                lastShowTime = new Date();
                warning = new DialogDescriptor(
                        getWarningMessage(),
                        "jVi Warning",
                        false,
                        new ActionListener() {
                                @Override
                                public void actionPerformed(ActionEvent e)
                                {
                                    closeDialog(true);
                                }
                            });
                warning.setMessageType(NotifyDescriptor.WARNING_MESSAGE);
                warning.setOptions(new Object[] {DialogDescriptor.OK_OPTION});

                // DialogDisplayer.getDefault().notifyLater(warning);

                dialog = DialogDisplayer.getDefault().createDialog(warning);
                dialog.addWindowListener(new WindowAdapter() {
                            @Override
                            public void windowClosing(WindowEvent e)
                            {
                                closeDialog(false);
                            }
                
                        });
                Frame f = WindowManager.getDefault().getMainWindow();
                dialog.setLocationRelativeTo(f);
                dialog.setVisible(true);
            }
        }
    }

    private static void closeDialog(boolean doDispose) {
        if(warning != null) {
            if(doDispose) {
                if(dialog != null) {
                    dialog.setVisible(false);
                    dialog.dispose();
                }
            }
            warning = null;
            dialog = null;
        }
    }

    static Timer timer;
    static TabSetTimerTask timerTask;
    private static final int DELAY = 1000; // one second

    /** This is used to signal that jVi is performing a setting and so
     * the dialog should not be put up. Simplest idea is to set flag while
     * jVi is calling the NB code, but this misses cases when there is delayed
     * actions, so add some hysteresis with a timer.
     * <br/>
     * Not using swing timer to insure no event thread interactions.
     */
    public static void setInternalAction(boolean enable) {
        synchronized(isInternalSetting) {
            if(enable) {
                isInternalSetting.setValue(true);
            } else {
                //if(false) {
                //    isInternalSetting.setValue(false);
                //    return;
                //}
                if(timer == null)
                    timer = new Timer();
                if(timerTask != null) {
                    timerTask.cancel();
                }
                timerTask = new TabSetTimerTask();
                timer.schedule(timerTask, DELAY); // do it once
            }
        }
    }

    private static class TabSetTimerTask extends TimerTask {
        private boolean cancelled;

        @Override
        public void run() {
            synchronized(isInternalSetting) {
                if(!isCancelled()) {
                    isInternalSetting.setValue(false);
                    timerTask = null;
                    timer.cancel();
                    timer = null;
                    //System.err.println("FIRE");
                }
            }
        }

        @Override
        public boolean cancel() {
            synchronized(isInternalSetting) {
                cancelled = true;
                return super.cancel();
            }
        }

        // should be holding lock
        boolean isCancelled() {
            return cancelled;
        }
    }

    private JViOptionWarning()
    {
    }
}
