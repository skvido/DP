package thesis.cwa;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.*;
import org.protege.editor.core.ui.util.ComponentFactory;
import org.protege.editor.owl.model.cache.OWLExpressionUserCache;
import org.protege.editor.owl.model.classexpression.OWLExpressionParserException;
import org.protege.editor.owl.model.entity.OWLEntityCreationSet;
import org.protege.editor.owl.model.event.OWLModelManagerListener;
import org.protege.editor.owl.model.inference.OWLReasonerManager;
import org.protege.editor.owl.model.inference.ReasonerUtilities;
import org.protege.editor.owl.ui.CreateDefinedClassPanel;
import org.protege.editor.owl.ui.clsdescriptioneditor.ExpressionEditor;
import org.protege.editor.owl.ui.clsdescriptioneditor.OWLExpressionChecker;
import org.protege.editor.owl.ui.view.AbstractOWLViewComponent;
import org.semanticweb.owlapi.expression.OWLEntityChecker;
import org.semanticweb.owlapi.expression.ShortFormEntityChecker;
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxClassExpressionParser;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLDifferentIndividualsAxiom;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.util.BidirectionalShortFormProvider;
import org.semanticweb.owlapi.util.BidirectionalShortFormProviderAdapter;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;


public class CwaViewComponent extends AbstractOWLViewComponent {


    private static final Marker marker = MarkerFactory.getMarker("DL Query");

    private static final Logger logger = LoggerFactory.getLogger(CwaViewComponent.class);


    private ExpressionEditor<OWLClassExpression> owlDescriptionEditor;
    
    private final JButton addClosureButton = new JButton("Add closure to ontology");


    private final OWLModelManagerListener listener = event -> {

    };

    private boolean requiresRefresh = false;



    protected void initialiseOWLView() throws Exception {
        setLayout(new BorderLayout(10, 10));

        
        JComponent editorPanel = createQueryPanel();

        add(editorPanel, BorderLayout.CENTER);

        getOWLModelManager().addListener(listener);


    }


    private JComponent createQueryPanel() {
        JPanel editorPanel = new JPanel(new BorderLayout());

        final OWLExpressionChecker<OWLClassExpression> checker = getOWLModelManager().getOWLExpressionCheckerFactory().getOWLClassExpressionChecker();
        owlDescriptionEditor = new ExpressionEditor<>(getOWLEditorKit(), checker);
        owlDescriptionEditor.addStatusChangedListener(newState -> {
            addClosureButton.setEnabled(newState);

        });
        editorPanel.setPreferredSize(new Dimension(50, 50));

        owlDescriptionEditor.setPreferredSize(new Dimension(100, 50));

        editorPanel.add(ComponentFactory.createScrollPane(owlDescriptionEditor), BorderLayout.CENTER);
        JPanel buttonHolder = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addClosureButton.addActionListener(e -> doAddClosure());

        buttonHolder.add(addClosureButton);


        editorPanel.add(buttonHolder, BorderLayout.SOUTH);
        editorPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createEmptyBorder(),
                        "Query (class expression)"),
                BorderFactory.createEmptyBorder(3, 3, 3, 3)));
        return editorPanel;
    }

    protected void disposeOWLView() {
        getOWLModelManager().removeListener(listener);
    }
 
    
    private void doAddClosure() {
    	if (isShowing()) {
            try {
                OWLReasonerManager reasonerManager = getOWLModelManager().getOWLReasonerManager();
                ReasonerUtilities.warnUserIfReasonerIsNotConfigured(this, reasonerManager);

                OWLClassExpression desc = owlDescriptionEditor.createObject();
                if (desc != null) {
                    OWLExpressionUserCache.getInstance(getOWLModelManager()).add(desc, owlDescriptionEditor.getText());
                    OWLEntityCreationSet<OWLClass> creationSet = CreateDefinedClassPanel.showDialog(desc, getOWLEditorKit());
                    if (creationSet != null) {

                        List<OWLOntologyChange> changes = new ArrayList<>(creationSet.getOntologyChanges());
                        ShortFormProvider shortFormProvider = new SimpleShortFormProvider();
                        OWLDataFactory df = getOWLModelManager().getOWLDataFactory();
                        OWLOntologyManager manager = getOWLModelManager().getActiveOntology().getOWLOntologyManager();
            			Set<OWLOntology> importsClosure = getOWLModelManager().getActiveOntology().getImportsClosure();
            			BidirectionalShortFormProvider bidiShortFormProvider = new BidirectionalShortFormProviderAdapter(manager, importsClosure, shortFormProvider);
            			OWLEntityChecker entityChecker = new ShortFormEntityChecker(bidiShortFormProvider);
            			ManchesterOWLSyntaxClassExpressionParser parser = new ManchesterOWLSyntaxClassExpressionParser(df, entityChecker);
                        
                        Set<OWLNamedIndividual> instances_of_KClass = reasonerManager.getCurrentReasoner().getInstances(desc, false).getFlattened();
                        
            			OWLDeclarationAxiom declarationAxiomK = df.getOWLDeclarationAxiom(creationSet.getOWLEntity());
                        
                        changes.add(new AddAxiom(getOWLModelManager().getActiveOntology(), declarationAxiomK));
            			getOWLModelManager().applyChanges(changes);
            			
            			for (OWLNamedIndividual i : instances_of_KClass) {
            				OWLClassAssertionAxiom classAssertionK = df.getOWLClassAssertionAxiom(creationSet.getOWLEntity(), i);
            				AddAxiom addAxiom = new AddAxiom(getOWLModelManager().getActiveOntology(), classAssertionK);
            				getOWLModelManager().applyChange(addAxiom);
            			}
            			
            			OWLDifferentIndividualsAxiom allIndivDiffax = df.getOWLDifferentIndividualsAxiom(getOWLModelManager().getActiveOntology().getIndividualsInSignature(Imports.EXCLUDED));
            			AddAxiom addAllIndivDiffax = new AddAxiom(getOWLModelManager().getActiveOntology(), allIndivDiffax);
            			getOWLModelManager().applyChange(addAllIndivDiffax);
            			
            			String subclassString = "{";
            			for (OWLNamedIndividual i : instances_of_KClass) {
            				subclassString = subclassString + i.getIRI().getShortForm() + ",";
            			}
            			subclassString = subclassString.substring(0, subclassString.length()-1);
            			subclassString = subclassString + "}";
            			System.out.println(subclassString);
            			
            			OWLAnnotation commentAnno = df.getOWLAnnotation(df.getRDFSComment(), df.getOWLLiteral("Created from expression: " + owlDescriptionEditor.getText()));
            			System.out.println("owlDescriptionEditortext = " + owlDescriptionEditor.getText());
            			OWLAxiom ax1 = df.getOWLAnnotationAssertionAxiom(creationSet.getOWLEntity().getIRI(), commentAnno);
            			System.out.println("entity IRI = " + creationSet.getOWLEntity().getIRI());
            			getOWLModelManager().applyChange(new AddAxiom(getOWLModelManager().getActiveOntology(), ax1));
            			
            			if (!subclassString.equals("}")) {
            				OWLClassExpression subclassExpression = parser.parse(subclassString);
            				OWLAxiom axiomKsubclass = df.getOWLSubClassOfAxiom(creationSet.getOWLEntity(), subclassExpression);
            				AddAxiom addAxiomsubclass = new AddAxiom(getOWLModelManager().getActiveOntology(), axiomKsubclass);
            				getOWLModelManager().applyChange(addAxiomsubclass);
            			}
            			else {
            				OWLAxiom axiomKsubclass = df.getOWLSubClassOfAxiom(creationSet.getOWLEntity(), df.getOWLNothing());
            				AddAxiom addAxiomsubclass = new AddAxiom(getOWLModelManager().getActiveOntology(), axiomKsubclass);
            				getOWLModelManager().applyChange(addAxiomsubclass);
            			}
                        
            			if (isSynchronizing()) {
                            getOWLEditorKit().getOWLWorkspace().getOWLSelectionModel().setSelectedEntity(creationSet.getOWLEntity());
                        }
                    }
                }
            }
            catch (OWLExpressionParserException e) {
                JOptionPane.showMessageDialog(this,
                                              e.getMessage(),
                                              "Invalid expression",
                                              JOptionPane.ERROR_MESSAGE);
            }
            catch (OWLException e) {
                logger.error(marker, "An error occurred whilst executing the DL query: {}", e.getMessage(), e);
            }
            requiresRefresh = false;
        }
        else {
            requiresRefresh = true;
        }
    }
}