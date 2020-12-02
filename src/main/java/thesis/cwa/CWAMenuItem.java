package thesis.cwa;

import java.awt.event.ActionEvent;

import javax.swing.JOptionPane;

import org.protege.editor.owl.model.OWLModelManager;
import org.protege.editor.owl.ui.action.ProtegeOWLAction;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class CWAMenuItem extends ProtegeOWLAction {

	public void initialise() throws Exception {
	}

	public void dispose() throws Exception {
	}

	public void actionPerformed(ActionEvent event) {
		OWLClass lastSelectedClass = getOWLWorkspace().getOWLSelectionModel().getLastSelectedClass();

		if (lastSelectedClass == null) {
			StringBuilder message = new StringBuilder("You have to select class first!\n");
			JOptionPane.showMessageDialog(getOWLWorkspace(), message.toString());
		}

		else {
			OWLModelManager modelManager = getOWLModelManager();
			OWLOntology activeOntology = modelManager.getActiveOntology();
			OWLDataFactory df = modelManager.getOWLDataFactory();
			OWLReasoner reasoner = modelManager.getReasoner();
			CWAManager cwa = new CWAManager(modelManager, activeOntology, df, reasoner);
			cwa.create(lastSelectedClass);
			cwa.createNot(lastSelectedClass);
		}
	}
}
