package thesis.cwa;

import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.OWLModelManager;
import org.protege.editor.owl.model.OWLWorkspace;
import org.protege.editor.owl.model.inference.OWLReasonerManager;
import org.protege.editor.owl.ui.view.AbstractOWLViewComponent;

public class B extends AbstractOWLViewComponent {
    public OWLModelManager get(){
        return getOWLModelManager();
    }

   public OWLEditorKit getEditorKit(){
        return getOWLEditorKit();
   }

    @Override
    protected void initialiseOWLView() throws Exception {

    }

    @Override
    protected void disposeOWLView() {

    }
}
