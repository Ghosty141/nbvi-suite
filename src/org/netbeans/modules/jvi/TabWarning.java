/*
 * TabWarning.java
 *
 * Created on September 12, 2007, 7:14 PM
 */

package org.netbeans.modules.jvi;

import com.raelity.jvi.MutableBoolean;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.SimpleValueNames;
import org.netbeans.modules.editor.NbEditorUtilities;
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
public class TabWarning extends JDialog {
    
    // NEEDSWORK: put text into a non-edit text area, or html area
    // to allow copy of web site.

    private String getWarningMessage() {
        return "ShiftWidth, ExpandTabs and TabStop defaults"
             + " for jVi are set through\n"
             + "Tools>Options>AdvancedOptions>\n"
             + "jViOptionsAndConfiguration>FileModificationOptions.\n"
             + "Use \":set ...\" after file is opened.\n\n"
             + "See http://jvi.sourceforge.net/ReadmeNetBeans.html#options\n\n"
             + "jVi reprograms the indent/tab options per document,\n"
             + "so changes made in standard settings are lost and not used.\n\n"
             + "See also \"modeline\" in the vim documentation.";
    }
    
    /** Creates new form TabWarning */
    private TabWarning(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                quitDialog(false);
            }
        });
        
        // Using a JOptionPane to get proper look and feel,
        // but want it to be non-modal so need to find the "OK"
        // button and hook it up.
        JButton b = findButton(getContentPane());
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                quitDialog(true);
            }
        });
    }

    private void quitDialog(boolean doDispose) {
        if(tabWarning != null) {
            JDialog dialog = tabWarning;
            tabWarning = null;
            if(doDispose) {
                dialog.setVisible(false);
                dialog.dispose();
            }
        }
    }

    private static TabWarning tabWarning;
    private static Date lastShowTime = new Date(0);
    private static PreferenceChangeListener scl;
    private final static Set<MimePath> mimePaths = new HashSet();

    private static final MutableBoolean
            isInternalSetting = new MutableBoolean();

    static void monitorMimeType(JEditorPane ep)
    {
        synchronized(mimePaths) {
            if(scl == null)
                scl = new TabSetListener();
            String mimeType = NbEditorUtilities.getMimeType(ep);
            Preferences prefs = MimeLookup.getLookup(
                    MimePath.parse(mimeType)).lookup(Preferences.class);
            prefs.addPreferenceChangeListener(scl);
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

    /**
     * If the preference is one we care about, and its not changed by jVi
     * and its been 5 minutes since we've warned, then show the warning.
     */
    private static class TabSetListener implements PreferenceChangeListener {
        public void preferenceChange(PreferenceChangeEvent evt) {
            String settingName = evt == null ? null : evt.getKey();
            long i = new Date().getTime();
            i = i - lastShowTime.getTime();
            System.err.println("T: " + i);
            if(!isInternalSetting.getValue()
               && new Date().getTime() - lastShowTime.getTime() > 5 * 60 * 1000
               && (settingName == null
                   || SimpleValueNames.SPACES_PER_TAB.equals(settingName)
                   || SimpleValueNames.INDENT_SHIFT_WIDTH.equals(settingName)
                   || SimpleValueNames.EXPAND_TABS.equals(settingName)
                   || SimpleValueNames.TAB_SIZE.equals(settingName))
               ) {

                // Note there may be lots of events, but only one dialog
                if(tabWarning == null) {
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            if(tabWarning == null) {
                                lastShowTime = new Date();
                                Frame f = WindowManager.getDefault()
                                                       .getMainWindow();
                                tabWarning = new TabWarning(f, false);
                                tabWarning.setLocationRelativeTo(f);
                                tabWarning.setVisible(true);
                            }
                        }
                    });
                }
            }
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
    static void setInternalAction(boolean enable) {
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
    
    private static JButton findButton(Container top) {
        Container c01 = (Container)findButtonArea(top);
        JButton b = null;
        if(c01 != null) {
            for(int i = 0; i < c01.getComponentCount(); i++) {
                Component c = c01.getComponent(i);
                if(c instanceof JButton) {
                    JButton j01 = (JButton)c;
                    b = j01;
                }
            }
        }
        return b;
    }

    private static Component findButtonArea(Container container) {
        for(Component c : container.getComponents()) {
            if("OptionPane.buttonArea".equals(c.getName())) {
                return c;
            }
            if(c instanceof Container) {
                Component c01 = findButtonArea((Container)c);
                if(c01 != null)
                    return c01;
            }
        }
        return null;
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jOptionPane1 = new javax.swing.JOptionPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(org.openide.util.NbBundle.getMessage(TabWarning.class, "TabWarning.title")); // NOI18N

        jOptionPane1.setMessage(getWarningMessage());
        jOptionPane1.setMessageType(2);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jOptionPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jOptionPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 148, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                TabWarning dialog = new TabWarning(new javax.swing.JFrame(), true);
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    public @Override void windowClosing(java.awt.event.WindowEvent e) {
                        System.exit(0);
                    }
                });
                dialog.setVisible(true);
            }
        });
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JOptionPane jOptionPane1;
    // End of variables declaration//GEN-END:variables
    
}
