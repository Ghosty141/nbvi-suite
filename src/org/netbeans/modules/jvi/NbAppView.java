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

import com.raelity.jvi.ViAppView;
import javax.swing.JEditorPane;
import javax.swing.text.Document;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.windows.TopComponent;

/**
 *
 * @author Ernie Rael <err at raelity.com>
 */
public class NbAppView implements ViAppView
{
    private TopComponent tc;
    private JEditorPane ep;

    public NbAppView(TopComponent tc, JEditorPane ep)
    {
        this.tc = tc;
        this.ep = ep;
    }

    public TopComponent getTopComponent()
    {
        return tc;
    }

    public JEditorPane getEditor()
    {
        return ep;
    }

    public boolean isNomad()
    {
        if(tc != null)
            return false;
        Document doc = ep.getDocument();
        return doc == null || NbEditorUtilities.getFileObject(doc) == null;
    }

    public int getWNum()
    {
        Integer wnum = (Integer)
                getTopComponent().getClientProperty(Module.PROP_W_NUM);
        return wnum != null ? wnum : -9;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // auto gen'd, based only on tc
    //

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NbAppView other = (NbAppView)obj;
        if (this.tc != other.tc &&
                (this.tc == null || !this.tc.equals(other.tc))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
        hash = 83 * hash + (this.tc != null ? this.tc.hashCode() : 0);
        return hash;
    }

}
