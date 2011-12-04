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

package org.netbeans.modules.jvi.impl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.text.Document;
import javax.swing.text.Segment;

import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.jvi.Module;
import org.openide.awt.HtmlBrowser;
import org.openide.text.Line;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputEvent;
import org.openide.windows.OutputListener;
import org.openide.windows.OutputWriter;
import org.openide.windows.TopComponent;

import com.raelity.jvi.ViOutputStream;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.lib.OutputStreamAdaptor;

/**
 * NbOutputStream is used to report the results of jVi commands such as
 * ":g/pattern/p" or ":1,$s/old/new/p".
 * <br/>
 * Typical usage is:
 * <pre>
 *      ViOutputStream vios = ViManager.createOutputStream(...
 *      vios.print ...
 *      vios.close
 * </pre>
 * in particular, the vios is not kept open for long periods of time.
 *
 * NEEDSWORK: NbOutputStream use NbEditorUtilities.getLine() to track links?
 *            Might as well give each line its own listener then.
 * Use Normal.nv_goto if textview still active/available.
 * NbEdUt.isDocumentActive(). Reopen file if needed.
 * @author erra
 */
public class NbOutputStream extends OutputStreamAdaptor {
    private static final
            Logger LOG = Logger.getLogger(NbOutputStream.class.getName());
    String type;
    ViTextView tv;
    InputOutput io;
    OutputWriter ow;
    String fnTag;
    StringBuilder sb = new StringBuilder();
    boolean fHyperlink = true;
    boolean savedSetFocusTaken;
    ///// NewOutputListenerImpl outputListener;

    private static int nOpen;
    private static final boolean checkOpenClose = true;
    
    /** Creates a new instance of NbOutputStream.
     * Type of ViOutputStream.OUTPUT is plain command output, the other
     * types will output lines from a file.
     * 
     * Priority in range 0 - 10 when 0 is lowest; only apply to OUTPUT.
     */
    public NbOutputStream(ViTextView tv,
                          String type,
                          String info,
                          int priority) {
        this.tv = tv;
        this.type = type;
        boolean bringUpWindow = priority > ViOutputStream.PRI_LOW;
        if(type.equals(ViOutputStream.OUTPUT)) {
            // plain output, no hyper text stuff or files or line numbers
            String tabTag = "jVi Output";
            getIO(tabTag, false, bringUpWindow); // reuse tab
            ow.println("-----------------------------------------------------");
            if(info != null)
                ow.println(info);
        } else {
            String sep = type.equals(ViOutputStream.SEARCH) ? "/" : "";
            String tabTag = "jVi " + sep +  (info != null ? info : "") + sep;
            getIO(tabTag, true, true); // make a new tab, always raise win
            fnTag = tv == null
                    ? "" : (tv.getBuffer().getDisplayFileName() + ":");
        }
        checkOpen();
    }
    
    private void getIO(String tabTag, boolean fNew, boolean fRaise) {
        io = IOProvider.getDefault().getIO(tabTag, fNew);

        if(io.isClosed() && fRaise) {
            // See NetBeans Issue 101445
            try {
                io.getOut().reset();//io = IOProvider.getDefault().getIO(tabTag, true);
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
                io = IOProvider.getDefault().getIO(tabTag, true);
            }
        }
        TopComponent tc = Module.getOutput();
        boolean fShowing = tc == null || tc.isShowing() ? true : false;
        savedSetFocusTaken = io.isFocusTaken();
        if(fRaise) {
            if(!fShowing)
                io.setFocusTaken(true); // so ESC will hide it
            io.select();
        } else {
            io.setFocusTaken(false);
        }
        ow = io.getOut();
    }

    @Override
    public void println(int line, int col, int length) {
        if(type.equals(ViOutputStream.OUTPUT))
            return;
        Segment seg = tv.getBuffer().getLineSegment(line);
        sb.setLength(0);
        sb.append(fnTag).append(line).append(":")
            .append(seg.array, seg.offset, seg.count - 1);
        try {
            if(fHyperlink) {
                ow.println(sb.toString(),
                           getOutputListener(tv, line, col, length));
            } else {
                ow.println(sb.toString());
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void println(String s) {
        ow.println(s);
    }

    @Override
    public void printlnLink(String link, String text) {
        try {
            ow.println(text, new BrowserHook(link));
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void close() {
        io.setFocusTaken(savedSetFocusTaken);
        ow.close();
        ow = null;
        io = null;
        tv = null;
        ///// outputListener = null;
        checkClose();
    }

    private synchronized static void checkOpen() {
        if(nOpen > 0) {
            System.err.println("UNBALANCED OPEN/CLOSE, RESETTING.");
            nOpen = 0;
        }
        nOpen++;
    }
    
    private synchronized static void checkClose() {
        nOpen--;
    }

    private OutputListener getOutputListener(
            ViTextView tv, int line, int col, int length) {
        Line nbLine = NbEditorUtilities.getLine(
                ((Document)tv.getBuffer().getDocument()),
                tv.getBuffer().getLineStartOffset(line),
                false);
        return new OutputListenerImpl(nbLine, col, length);
    }

    private static class OutputListenerImpl implements OutputListener {
        Line nbLine;
        int col;
        int len;

        OutputListenerImpl(Line nbLine, int col, int len) {
            this.nbLine = nbLine;
            this.col = col;
            this.len = len;
        }

        @Override
        public void outputLineAction(OutputEvent outputEvent) {
            nbLine.show(Line.ShowOpenType.OPEN,
                        Line.ShowVisibilityType.FOCUS,
                        col);
            // highlights takes care of this
            // This is flakey for the first time click on hyperlink
            //final JTextComponent tc = EditorRegistry.focusedComponent();
            //if(tc != null) {
            //    EventQueue.invokeLater(new Runnable() {
            //        public void run() {
            //            int off = tc.getCaretPosition();
            //            tc.setCaretPosition(off + len);
            //            tc.moveCaretPosition(off);
            //        }
            //    });
            //}
        }

        @Override
        public void outputLineCleared(OutputEvent outputEvent) {
        }

        @Override
        public void outputLineSelected(OutputEvent outputEvent) {
        }
    }

    ///// private OutputListener getNewOutputListener(
    /////         ViTextView tv, int line, int col, int length) {
    /////     if(outputListener == null || outputListener.tv != tv) {
    /////         outputListener = new NewOutputListenerImpl(tv);
    /////     }
    /////     return outputListener;
    ///// }
    
    //
    // current way, with one listener per line, allows the hyperlink to
    // position you directly on the matched string, the col within the line.
    //
    ///// private static class NewOutputListenerImpl implements OutputListener {
    /////     ViTextView tv;
    /////     //int col;
    /////
    /////     NewOutputListenerImpl(ViTextView tv/*, int col*/) {
    /////         this.tv = tv;
    /////     }

    /////     public void outputLineAction(OutputEvent outputEvent) {
    /////         int line = 0;
    /////         // line = parse outputEvent.getLine();
    /////         Line nbLine = NbEditorUtilities.getLine(
    /////                 ((Document)tv.getBuffer().getDocument()),
    /////                 tv.getBuffer().getLineStartOffset(line),
    /////                 false);
    /////         nbLine.show(Line.ShowOpenType.OPEN,
    /////                     Line.ShowVisibilityType.FRONT,
    /////                     0);
    /////         // Adjust to first non-blank on line
    /////     }

    /////     public void outputLineCleared(OutputEvent outputEvent) {
    /////     }

    /////     public void outputLineSelected(OutputEvent outputEvent) {
    /////     }
    ///// }

    private static class BrowserHook implements OutputListener {
        private String link;

        BrowserHook(String link) {
            this.link = link;
        }

        @Override
        public void outputLineAction(OutputEvent ev) {
            // invoke a browser on the link
            URL url;
            try {
                url = new URL(link);
                HtmlBrowser.URLDisplayer.getDefault().showURL(url);
            } catch (MalformedURLException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }

        @Override
        public void outputLineCleared(OutputEvent ev) {
        }

        @Override
        public void outputLineSelected(OutputEvent ev) {
        }
    }
}
