package org.netbeans.modules.jvi;

import com.raelity.jvi.ViAppView;
import com.raelity.jvi.core.Filemark;
import com.raelity.jvi.core.Msg;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.ViBuffer;
import com.raelity.jvi.ViFPOS;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.lib.abstractFS;
import java.io.File;
import java.io.IOException;
import javax.swing.text.Document;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.actions.SaveAllAction;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.SaveCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.text.Line;
import org.openide.util.actions.SystemAction;

public class NbFS extends abstractFS
{
    private DataObject getDataObject(ViBuffer buf) {
	Document doc = (Document)buf.getDocument();
        DataObject dobj = null;
	if(doc != null) {
	    FileObject fo = NbEditorUtilities.getFileObject(doc);
	    if(fo == null) {
		Msg.emsg("Internal Error: null FileObject??");
                return null;
	    }
	    dobj = NbEditorUtilities.getDataObject(doc);
        }
        return dobj;
    }

    private static final String NULL_FO = "noname-null-FileObject";

    @Override
    public String getDisplayFileName(ViAppView _av) {
        NbAppView av = (NbAppView)_av;
        String s = null;
        if(av != null) {
            if(av.getTopComponent() != null) {
                s = av.getTopComponent().getDisplayName();
            }
            if(s == null && av.getEditor() != null) {
                ViTextView tv = ViManager.getViFactory().getTextView(av);
                if(tv != null)
                    s = findName(tv.getBuffer());
                if(s == null)
                    s = findName(av.getEditor().getDocument());
            }
            if(s == null && av.getTopComponent() != null)
                s = av.getTopComponent().getClass().getSimpleName();
        }
        if(s == null)
            s = "screwy-AppView-missing-fields";
        return s;
    }

    private String findName(ViBuffer buf)
    {
        String s = null;
        if(buf != null)
            s = findName((Document)buf.getDocument());
        return s;
    }

    private String findName(Document doc)
    {
        String s = null;
        if(doc != null) {
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            if(fo != null)
                s = fo.getNameExt();
            if(s == null) {
                Object o = doc.getProperty(Document.TitleProperty);
                if(o != null)
                    s = o.toString();
            }
        }
        return s;
    }

    @Override
    public String getDisplayFileName(ViBuffer buf) {
        String s = null;
        if(buf != null)
            s = findName((Document)buf.getDocument());
        return s != null ? s : NULL_FO;
    }

    public boolean isModified(ViBuffer buf) {
        DataObject dobj = getDataObject(buf);
        return dobj != null ? dobj.isModified() : true;
    }

    public boolean isReadOnly(ViBuffer buf) {
        FileObject fo = NbEditorUtilities.getFileObject(
                (Document)buf.getDocument());
        return fo != null && !fo.canWrite();
    }

    private boolean write(ViTextView tv, boolean force) {
        boolean ok = true;
        ViBuffer buf = tv.getBuffer();
        DataObject dobj = getDataObject(buf);
        if(dobj != null) {
            //SaveAction s = (SaveAction)SystemAction.get(SaveAction.class);
            // NB6.0 s.createContextAwareInstance(dobj.Lookup());
            //s.performAction((Node[])null);
            SaveCookie sc = dobj.getCookie(SaveCookie.class);
            if(sc != null) {
                try {
                    sc.save();
                    Msg.smsg(getDisplayFileNameAndSize(buf) + " written");
                } catch (IOException ex) {
                    Msg.emsg("error writing " + buf.getDisplayFileName());
                    ok = false;
                }
            } else {
                Msg.wmsg(getDisplayFileName(buf) + " NOT written");
            }
        }
        return ok;
    }

    private boolean write(ViTextView tv, String fName, boolean force) {
	// Will probably never implement
	Msg.emsg("WRITE new_name NOT IMPLEMENTED, " + force);
        return false;
    }

    public boolean write(
            ViTextView tv, boolean force, Object writeTarget, Integer[] range)
    {
        if(range == null || range.length == 0) {
            if(writeTarget == null) {
                return write(tv, force);
            } else if(writeTarget instanceof String) {
                return write(tv, (String) writeTarget, force);
            } else {
                Msg.emsg("WRITE TO " + writeTarget.getClass().getName()
                        + " NOT IMPLEMENTED");
                return false;
            }
        }
	Msg.emsg("WRITE RANGE NOT IMPLEMENTED, ");
        return false;
    }

    public boolean writeAll(boolean force) {
	SaveAllAction sa = SystemAction.get(SaveAllAction.class);
        sa.performAction();
        return true;
    }

    public void edit(ViAppView _av, boolean force)
    {
        NbAppView av = (NbAppView)_av;
        if(av.getTopComponent() != null) {
            av.getTopComponent().requestActive();
            if(av.getEditor() != null)
                av.getEditor().requestFocusInWindow();
        } else
            Msg.emsg("Can not edit " + getDisplayFileName(av));
    }

    /** Edit a File */
    public void edit(File f, boolean force, ViFPOS fpos)
    {
        String msg = null;
        try {
            // get a netbens FileObject
            if(!f.isAbsolute()) {
                f = f.getAbsoluteFile();
            }
            FileObject fo;
            if(f.exists()) {
                fo = FileUtil.toFileObject(f);
            } else if(force) {
                fo = FileUtil.createData(f);
            } else {
                msg = "'!' required when file does not exist";
                return;
            }
            DataObject dobj = DataObject.find(fo);
            EditorCookie ec = dobj.getCookie(EditorCookie.class);

            // Start bringing it up in the editor
            ec.open();
            // Wait for the document to be available
            Document doc = ec.openDocument();
            //System.err.println("Document Ready");
            if(fpos != null) {
                // finishTagPush ??

                // currently active or not
                // if active use offset

                // Q up goto line for next switch, Q: tc,run

                Line l = NbEditorUtilities.getLine(doc, fpos.getOffset(), false);
                l.show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
            }
        } catch (DataObjectNotFoundException ex) {
            msg = ex.getLocalizedMessage();
        } catch (IOException ex) {
            msg = ex.getLocalizedMessage();
            //Logger.getLogger(NbFS.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if(msg != null) {
                Msg.emsg("edit failed: " + msg);
                Util.vim_beep();
            }
        }
    }
}
