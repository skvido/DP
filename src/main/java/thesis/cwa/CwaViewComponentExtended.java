package thesis.cwa;

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.protege.editor.core.prefs.Preferences;
import org.protege.editor.core.prefs.PreferencesManager;
import org.protege.editor.core.ui.util.ComponentFactory;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.cache.OWLExpressionUserCache;
import org.protege.editor.owl.model.classexpression.OWLExpressionParserException;
import org.protege.editor.owl.model.entity.OWLEntityCreationSet;
import org.protege.editor.owl.model.entity.OWLEntityFactory;
import org.protege.editor.owl.model.event.EventType;
import org.protege.editor.owl.model.event.OWLModelManagerListener;
import org.protege.editor.owl.model.inference.OWLReasonerManager;
import org.protege.editor.owl.model.inference.ReasonerUtilities;
import org.protege.editor.owl.model.parser.OWLParseException;
import org.protege.editor.owl.ui.CreateDefinedClassPanel;
import org.protege.editor.owl.ui.clsdescriptioneditor.ExpressionEditor;
import org.protege.editor.owl.ui.clsdescriptioneditor.OWLExpressionChecker;
import org.protege.editor.owl.ui.editor.OWLClassDescriptionEditor;
import org.protege.editor.owl.ui.view.AbstractOWLViewComponent;
import org.semanticweb.owlapi.expression.OWLEntityChecker;
import org.semanticweb.owlapi.expression.ShortFormEntityChecker;
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxClassExpressionParser;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import org.coode.dlquery.*;
//import sun.tools.jps.Jps;

import static org.coode.dlquery.ResultsSection.*;

/**
 * Author: Matthew Horridge<br>
 * The University Of Manchester<br>
 * Medical Informatics Group<br>
 * Date: 22-Aug-2006<br><br>
 *
 * matthew.horridge@cs.man.ac.uk<br>
 * www.cs.man.ac.uk/~horridgm<br><br>
 */
public class CwaViewComponentExtended extends AbstractOWLViewComponent {


    private static final Marker marker = MarkerFactory.getMarker("DL Query");

    private static final Logger logger = LoggerFactory.getLogger(CwaViewComponent.class);



    public static final String SHOW_OWL_THING_IN_RESULTS_KEY = "showOWLThingInResults";

    public static final String SHOW_OWL_NOTHING_IN_RESULTS_KEY = "showOWLNothingInResults";


    private final JCheckBox showOWLThingInResults = new JCheckBox("<html><body>Display owl:Thing<br><span style=\"color: #808080; font-size: 0.8em;\">(in superclass results)</span></body></html>");

    private final JCheckBox showOWLNothingInResults = new JCheckBox("<html><body>Display owl:Nothing<br><span style=\"color: #808080; font-size: 0.8em;\">(in subclass results)</span></body></html>");

    private final JTextField nameFilterField = new JTextField(8);


    private ExpressionEditor<OWLClassExpression> owlDescriptionEditor;
    private ExpressionEditor<OWLClassExpression> tmpOwlDescriptionEditor;

    private ResultsList resultsList;

    private final JCheckBox showDirectSuperClassesCheckBox = new JCheckBox(DIRECT_SUPER_CLASSES.getDisplayName());

    private final JCheckBox showSuperClassesCheckBox = new JCheckBox(SUPER_CLASSES.getDisplayName());

    private final JCheckBox showEquivalentClassesCheckBox = new JCheckBox(EQUIVALENT_CLASSES.getDisplayName());

    private final JCheckBox showDirectSubClassesCheckBox = new JCheckBox(DIRECT_SUB_CLASSES.getDisplayName());

    private final JCheckBox showSubClassesCheckBox = new JCheckBox(SUB_CLASSES.getDisplayName());

    private final JCheckBox showIndividualsCheckBox = new JCheckBox(INSTANCES.getDisplayName());

    private final JButton executeButton = new JButton("Execute");

    private final JButton addButton = new JButton("Add to ontology");

    private final JButton addClosureButton = new JButton("Add closure to ontology");

    private final JButton convertToCWAButton = new JButton("Convert to CWA");

    private JTextArea cwaInputArea;

    private String finalCwa = "";
    private String originalQ = "";
    private List<String> lastQKClasses = new ArrayList<>();

    private List<RemoveAxiom> axiomsToRemove = new ArrayList<>();


    private final OWLModelManagerListener listener = event -> {
        if (event.isType(EventType.ONTOLOGY_CLASSIFIED)) {
            convertQuery();;
        }
    };

    private boolean requiresRefresh = false;

    private final Predicate<OWLClass> supersFilter = cls -> !cls.isOWLThing() || showOWLThingInResults.isSelected();

    private final Predicate<OWLClass> subsFilter = cls -> !cls.isOWLNothing() || showOWLNothingInResults.isSelected();

    private final Timer timer = new Timer(400, e -> SwingUtilities.invokeLater(this::doQuery));


    protected void initialiseOWLView() throws Exception {
        setLayout(new BorderLayout(10, 10));

        showOWLThingInResults.setVerticalTextPosition(SwingConstants.TOP);
        showOWLNothingInResults.setVerticalTextPosition(SwingConstants.TOP);

        timer.setRepeats(false);

        JComponent editorPanel = createQueryPanel();
        JComponent resultsPanel = createResultsPanel();
        JComponent optionsBox = createOptionsBox();
        JPanel optionsBoxHolder = new JPanel(new BorderLayout());
        optionsBoxHolder.add(optionsBox, BorderLayout.NORTH);
        resultsPanel.add(optionsBoxHolder, BorderLayout.EAST);

        JSplitPane splitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorPanel, resultsPanel);
        splitter.setDividerLocation(0.3);

        add(splitter, BorderLayout.CENTER);

        updateGUI();

        getOWLModelManager().addListener(listener);

        addHierarchyListener(event -> {
            if (requiresRefresh && isShowing()) {
                convertQuery();
            }
        });
    }


    private JComponent createQueryPanel() {
        JPanel editorPanel = new JPanel(new BorderLayout());
        JPanel part1 = new JPanel(new BorderLayout());
        JPanel part2 = new JPanel(new BorderLayout());

        final OWLExpressionChecker<OWLClassExpression> checker = getOWLModelManager().getOWLExpressionCheckerFactory().getOWLClassExpressionChecker();
        owlDescriptionEditor = new ExpressionEditor<>(getOWLEditorKit(), checker);
        tmpOwlDescriptionEditor = new ExpressionEditor<>(getOWLEditorKit(), checker);

        owlDescriptionEditor.addStatusChangedListener(newState -> {
            executeButton.setEnabled(newState);
            addButton.setEnabled(newState);
            addClosureButton.setEnabled(newState);
            convertToCWAButton.setEnabled(newState);
        });
        owlDescriptionEditor.setPreferredSize(new Dimension(100, 50));
        owlDescriptionEditor.setText("");

        part1.add(ComponentFactory.createScrollPane(owlDescriptionEditor), BorderLayout.CENTER);
        JPanel buttonHolder = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel buttonHolder1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        executeButton.addActionListener(e -> convertQuery());

        addButton.addActionListener(e -> doAdd());
        //addClosureButton.addActionListener(e -> doAddClosure());


        buttonHolder.add(executeButton);
        buttonHolder.add(addButton);
        //buttonHolder.add(addClosureButton);

        convertToCWAButton.addActionListener(e-> convertToCWA(owlDescriptionEditor.getText()));
        buttonHolder1.add(convertToCWAButton);
        part1.add(buttonHolder1, BorderLayout.SOUTH);

        cwaInputArea = new JTextArea(3, 58);
        cwaInputArea.setEditable(true);
        cwaInputArea.setBorder(null);
        JScrollPane scroll = new JScrollPane(cwaInputArea);
        //scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        part2.add(scroll , BorderLayout.CENTER);
        part2.add(buttonHolder , BorderLayout.SOUTH);

        editorPanel.add(part1, BorderLayout.NORTH);
        editorPanel.add(part2, BorderLayout.SOUTH);

        editorPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createEmptyBorder(),
                        "Query (class expression)"),
                BorderFactory.createEmptyBorder(3, 3, 3, 3)));
        return editorPanel;
    }


    private JComponent createResultsPanel() {
        JComponent resultsPanel = new JPanel(new BorderLayout(10, 10));
        resultsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), "Query results"),
                BorderFactory.createEmptyBorder(3, 3, 3, 3)));
        resultsList = new ResultsList(getOWLEditorKit());
        resultsPanel.add(ComponentFactory.createScrollPane(resultsList));
        return resultsPanel;
    }


    private JComponent createOptionsBox() {
        Box optionsBox = new Box(BoxLayout.Y_AXIS);
        optionsBox.setMinimumSize(new Dimension(50, 50));

        JLabel queryForLabel = new JLabel("Query for");
        queryForLabel.setFont(queryForLabel.getFont().deriveFont(Font.BOLD));
        optionsBox.add(queryForLabel);
        optionsBox.add(Box.createVerticalStrut(10));

        showDirectSuperClassesCheckBox.addActionListener(e -> {
            resultsList.setResultsSectionVisible(DIRECT_SUPER_CLASSES, showDirectSuperClassesCheckBox.isSelected());
            convertQuery();
        });
        optionsBox.add(showDirectSuperClassesCheckBox);
        optionsBox.add(Box.createVerticalStrut(3));

        showSuperClassesCheckBox.addActionListener(e -> {
            resultsList.setResultsSectionVisible(SUPER_CLASSES, showSuperClassesCheckBox.isSelected());
            convertQuery();
        });
        showSuperClassesCheckBox.setSelected(false);
        optionsBox.add(showSuperClassesCheckBox);
        optionsBox.add(Box.createVerticalStrut(3));

        showEquivalentClassesCheckBox.addActionListener(e -> {
            resultsList.setResultsSectionVisible(EQUIVALENT_CLASSES, showEquivalentClassesCheckBox.isSelected());
            convertQuery();
        });
        optionsBox.add(showEquivalentClassesCheckBox);
        optionsBox.add(Box.createVerticalStrut(3));

        showDirectSubClassesCheckBox.addActionListener(e -> {
            resultsList.setResultsSectionVisible(DIRECT_SUB_CLASSES, showDirectSubClassesCheckBox.isSelected());
            convertQuery();
        });
        optionsBox.add(showDirectSubClassesCheckBox);
        optionsBox.add(Box.createVerticalStrut(3));

        showSubClassesCheckBox.addActionListener(e -> {
            resultsList.setResultsSectionVisible(SUB_CLASSES, showSubClassesCheckBox.isSelected());
            convertQuery();
        });
        showSubClassesCheckBox.setSelected(false);
        optionsBox.add(showSubClassesCheckBox);
        optionsBox.add(Box.createVerticalStrut(3));

        showIndividualsCheckBox.addActionListener(e -> {
            resultsList.setResultsSectionVisible(INSTANCES, showIndividualsCheckBox.isSelected());
            convertQuery();
        });

        optionsBox.add(showIndividualsCheckBox);
        optionsBox.add(Box.createVerticalStrut(20));
        optionsBox.add(new JSeparator());
        optionsBox.add(Box.createVerticalStrut(20));


        JLabel filtersLabel = new JLabel("Result filters");
        filtersLabel.setFont(filtersLabel.getFont().deriveFont(Font.BOLD));
        optionsBox.add(filtersLabel);

        optionsBox.add(Box.createVerticalStrut(10));

        JLabel nameFilterLabel = new JLabel("Name contains");
        optionsBox.add(Box.createVerticalStrut(3));
        optionsBox.add(nameFilterLabel);
        optionsBox.add(nameFilterField);
        nameFilterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleQuery();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleQuery();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });

        optionsBox.add(Box.createVerticalStrut(20));

        optionsBox.add(showOWLThingInResults);
        Preferences preferences = PreferencesManager.getInstance().getApplicationPreferences("DLQuery");

        showOWLThingInResults.setSelected(preferences.getBoolean(SHOW_OWL_THING_IN_RESULTS_KEY, true));
        showOWLThingInResults.setBorder(BorderFactory.createEmptyBorder(0, 0, 7, 0));
        showOWLThingInResults.addActionListener(e -> {
            preferences.putBoolean(SHOW_OWL_THING_IN_RESULTS_KEY, showOWLThingInResults.isSelected());
            convertQuery();
        });
        showOWLThingInResults.setHorizontalAlignment(SwingConstants.LEFT);
        optionsBox.add(Box.createVerticalStrut(3));
        optionsBox.add(showOWLNothingInResults);
        showOWLNothingInResults.setSelected(preferences.getBoolean(SHOW_OWL_NOTHING_IN_RESULTS_KEY, true));
        showOWLNothingInResults.setBorder(BorderFactory.createEmptyBorder());
        showOWLNothingInResults.addActionListener(e -> {
            preferences.putBoolean(SHOW_OWL_NOTHING_IN_RESULTS_KEY, showOWLNothingInResults.isSelected());
            convertQuery();
        });



        return optionsBox;
    }


    protected void disposeOWLView() {
        getOWLModelManager().removeListener(listener);
    }


    private void updateGUI() {
        showDirectSuperClassesCheckBox.setSelected(resultsList.isResultsSectionVisible(DIRECT_SUPER_CLASSES));
        showSuperClassesCheckBox.setSelected(resultsList.isResultsSectionVisible(SUPER_CLASSES));
        showEquivalentClassesCheckBox.setSelected(resultsList.isResultsSectionVisible(EQUIVALENT_CLASSES));
        showDirectSubClassesCheckBox.setSelected(resultsList.isResultsSectionVisible(DIRECT_SUB_CLASSES));
        showSubClassesCheckBox.setSelected(resultsList.isResultsSectionVisible(SUB_CLASSES));
        showIndividualsCheckBox.setSelected(resultsList.isResultsSectionVisible(INSTANCES));
    }

    private void scheduleQuery() {
        timer.restart();
    }

    private void doQuery() {
        if (isShowing()) {
            try {
                OWLReasonerManager reasonerManager = getOWLModelManager().getOWLReasonerManager();
                ReasonerUtilities.warnUserIfReasonerIsNotConfigured(this, reasonerManager);

                tmpOwlDescriptionEditor.setText(finalCwa);
                OWLClassExpression desc = tmpOwlDescriptionEditor.createObject();
                if (desc != null) {
                    OWLExpressionUserCache.getInstance(getOWLModelManager()).add(desc, tmpOwlDescriptionEditor.getText());
                    resultsList.setSuperClassesResultFilter(supersFilter);
                    resultsList.setDirectSuperClassesResultFilter(supersFilter);

                    resultsList.setDirectSubClassesResultFilter(subsFilter);
                    resultsList.setSubClassesResultFilter(subsFilter);

                    resultsList.setNameFilter(nameFilterField.getText().trim());

                    resultsList.setOWLClassExpression(desc);

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


    private void doAdd() {
        try {
            OWLClassExpression desc = tmpOwlDescriptionEditor.createObject();
            OWLEntityCreationSet<OWLClass> creationSet = CreateDefinedClassPanel.showDialog(desc, getOWLEditorKit());
            if (creationSet != null) {
                List<OWLOntologyChange> changes = new ArrayList<>(creationSet.getOntologyChanges());
                OWLDataFactory factory = getOWLModelManager().getOWLDataFactory();
                OWLAxiom equiv = factory.getOWLEquivalentClassesAxiom(creationSet.getOWLEntity(), desc);
                changes.add(new AddAxiom(getOWLModelManager().getActiveOntology(), equiv));
                getOWLModelManager().applyChanges(changes);
                if (isSynchronizing()) {
                    getOWLEditorKit().getOWLWorkspace().getOWLSelectionModel().setSelectedEntity(creationSet.getOWLEntity());
                }
            }
        } catch (OWLException e) {
            logger.error(marker, "An error occurred whilst adding the class definition: {}", e.getMessage(), e);
        }
    }

    private void doAddClosure(String s, String name) {
        tmpOwlDescriptionEditor.setText(s);
        lastQKClasses.add(name);
        String iri = getOWLModelManager().getActiveOntology().getOntologyID().getOntologyIRI().toString().split("\\(")[1].replace(")","");
    	if (isShowing()) {
            try {
                OWLReasonerManager reasonerManager = getOWLModelManager().getOWLReasonerManager();
                reasonerManager.getCurrentReasoner().flush();
                ReasonerUtilities.warnUserIfReasonerIsNotConfigured(this, reasonerManager);
                //OWLEntityRemover remover = new OWLEntityRemover(Collections.singleton(getOWLModelManager().getActiveOntology()));

                OWLClassExpression desc = tmpOwlDescriptionEditor.createObject();
                if (desc != null) {
                    OWLExpressionUserCache.getInstance(getOWLModelManager()).add(desc, tmpOwlDescriptionEditor.getText());
                    OWLEntityCreationSet<OWLClass> creationSet = getOWLEditorKit().getModelManager().getOWLEntityFactory().createOWLClass(name,null);
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
            			//System.out.println(subclassString);

            			OWLAnnotation commentAnno = df.getOWLAnnotation(df.getRDFSComment(), df.getOWLLiteral("Created from expression: " + tmpOwlDescriptionEditor.getText()));
            			//System.out.println("owlDescriptionEditortext = " + owlDescriptionEditor.getText());
            			OWLAxiom ax1 = df.getOWLAnnotationAssertionAxiom(creationSet.getOWLEntity().getIRI(), commentAnno);
            			//System.out.println("entity IRI = " + creationSet.getOWLEntity().getIRI());
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
                reasonerManager.getCurrentReasoner().flush();
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

    private void addExactlyValues(String property)  {
        tmpOwlDescriptionEditor.setText(property + " min 1");
        System.out.println("Adding 'exactly' values for property: " + property);
        OWLClassExpression desc = null;
        try {
            desc = tmpOwlDescriptionEditor.createObject();
        } catch (OWLException e) {
            e.printStackTrace();
        }

        OWLExpressionUserCache.getInstance(getOWLModelManager()).add(desc, tmpOwlDescriptionEditor.getText());

        String iri = getOWLModelManager().getActiveOntology().getOntologyID().getOntologyIRI().toString().split("\\(")[1].replace(")","");

        OWLReasonerManager reasonerManager = getOWLModelManager().getOWLReasonerManager();
        reasonerManager.getCurrentReasoner().flush();
        ReasonerUtilities.warnUserIfReasonerIsNotConfigured(this, reasonerManager);

        OWLDataFactory df = getOWLModelManager().getOWLDataFactory();
        OWLOntologyManager manager = getOWLModelManager().getActiveOntology().getOWLOntologyManager();


        OWLObjectProperty objectProperty = manager.getOWLDataFactory().getOWLObjectProperty(IRI.create(iri + "#" + property));

        Set<OWLNamedIndividual> individuals = reasonerManager.getCurrentReasoner().getInstances(desc, false).getFlattened();

        for (OWLNamedIndividual i : individuals){
            Set<OWLNamedIndividual> in = reasonerManager.getCurrentReasoner().getObjectPropertyValues(i,objectProperty).getFlattened();
            ShortFormProvider shortFormProvider = new SimpleShortFormProvider();
            Set<OWLOntology> importsClosure = getOWLModelManager().getActiveOntology().getImportsClosure();
            BidirectionalShortFormProvider bidiShortFormProvider = new BidirectionalShortFormProviderAdapter(manager, importsClosure, shortFormProvider);
            OWLEntityChecker entityChecker = new ShortFormEntityChecker(bidiShortFormProvider);
            ManchesterOWLSyntaxClassExpressionParser parser = new ManchesterOWLSyntaxClassExpressionParser(df, entityChecker);

            OWLClassExpression expression = parser.parse(property + " exactly " + in.size() );

            OWLAxiom axiom = df.getOWLClassAssertionAxiom(expression, i);
            AddAxiom addAxiom = new AddAxiom(getOWLModelManager().getActiveOntology(), axiom);

            axiomsToRemove.add(new RemoveAxiom(getOWLModelManager().getActiveOntology(), axiom));

            getOWLModelManager().applyChange(addAxiom);

        }
    }

    private void removeExactlyValues(){
        for (RemoveAxiom r : axiomsToRemove){
            getOWLModelManager().applyChange(r);
        }
        axiomsToRemove.clear();
        getOWLModelManager().getOWLReasonerManager().getCurrentReasoner().flush();
    }

    private void parseCardinality(String q){
        String[] split = q.split(" ");
        String property = "";

        for (int i = 0 ; i < split.length ; i++){
            if (split[i].toLowerCase().equals("exactly") || split[i].toLowerCase().equals("max")){
                property = split[i-1].replace("(" , "");
                addExactlyValues(property);
            }
        }
    }

    private int checkCWAInput(String s){
        int left = 0;
        int right = 0;
        s = s.trim().replaceAll("\\s{2,}", " ");
        s = s.replace("K(" , "K (");
        s = s.replace("k(" , "k (");
        int err = 0;
        for (int i = 0; i < s.length() ; i++){
            if (s.charAt(i) == '(')
                left++;
            else if (s.charAt(i) == ')')
                right++;
            else if (s.charAt(i) == 'K' || s.charAt(i) == 'k'){
                if (i == 0){
                    if (s.charAt(i+1) == ' '){
                        if (s.charAt(i+2) != '('){
                            return i;
                        }
                    }
                }else{
                    if (s.charAt(i+1) == ' ' && s.charAt(i-1) == ' '){
                        if (s.charAt(i+2) != '('){
                            return i;
                        }
                    }
                }
            }
        }
        if (left == right)
            return -1;
        else
            return -2;
    }

    private void convertQuery(){
        String cwaInput = cwaInputArea.getText();
        String q;
        int check = checkCWAInput(cwaInput);
        if (cwaInput.length() > 1){
            if (check == -2){
                JOptionPane.showMessageDialog(this,
                        "Some brackets are missing pairs",
                        "Invalid expression",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (check != -1){
                JOptionPane.showMessageDialog(this,
                        "Expected '(' after 'K' at index " + check,
                        "Invalid expression",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            tmpOwlDescriptionEditor.setText(cwaInput);
            q = cwaInput;
        }else {
            q = owlDescriptionEditor.getText();
            cwaInput = convertToCWA(q);
        }

        parseCardinality(q);

        cwaInput = cwaInput.replace(" )" , ")");
        //cwaInput = cwaInput.replace(") " , ")");
        cwaInput = cwaInput.replace("( " , "(");
        cwaInput = cwaInput.replace("(" , " (");
        cwaInput = cwaInput.trim().replaceAll("\\s{2,}", " ");

        //String cwaInput = convertToCWA(q);
        List<String> ls = new ArrayList<>(parseKConcepts(cwaInput));
        String newQ = createKConcepts(ls , cwaInput);

        finalCwa = newQ;
        getOWLModelManager().getOWLReasonerManager().getCurrentReasoner().flush();
        doQuery();
        getOWLModelManager().getOWLReasonerManager().getCurrentReasoner().flush();

        removeKClasses();
        removeExactlyValues();
    }

    private void removeKClasses(){
        if (lastQKClasses.size() > 0) {
            OWLEntityRemover remover = new OWLEntityRemover(Collections.singleton(getOWLModelManager().getActiveOntology()));
            String className = "";
            String iri = getOWLModelManager().getActiveOntology().getOntologyID().getOntologyIRI().toString().split("\\(")[1].replace(")","");
            for (int i = 0; i < lastQKClasses.size(); i++) {
                className = lastQKClasses.get(i);
                OWLClass owlClass = getOWLModelManager().getOWLDataFactory().getOWLClass(IRI.create(iri + "#" + className));
                System.out.println("Removing: " + iri + "#" + className);
                owlClass.accept(remover);
            }

            getOWLModelManager().applyChanges(remover.getChanges());
            remover.reset();
            lastQKClasses.clear();
        }
    }

    private String convertOnlyToSome(String s){
        String result = "";
        List<String> split = new ArrayList<>(Arrays.asList(s.split(" ")));
        int value = 0;

        for (int i = 0; i < split.size(); i++){
            if (split.get(i).toLowerCase().equals("only")){
                split.set(i, "some");
                split.set(i-1, "("+split.get(i-1));
                split.add(i-1, "(not");
                i++;
                if (split.get(i+1).charAt(0) == '('){
                    value++;
                    split.set(i+1 , "(not " + split.get(i+1));
                    for (int j = i+2 ;j < split.size() ; j++){
                        if (split.get(j).charAt(0) == '(')
                            value++;
                        if (split.get(j).charAt(split.get(j).length()-1) == ')'){
                            value--;
                        }
                        if (value == 0){
                            split.set(j, split.get(j) + ")))");
                            break;
                        }
                    }
                }else{
                    split.set(i+1 , "(not (" + split.get(i+1) + "))))");
                }
            }
        }

        for (String str : split){
            result = result + str + " ";
        }

        return result;
    }

    private String addOperatorK (String s){
        String result = "";
        int value = 0;
        List<String> split = new ArrayList<>(Arrays.asList(s.split(" ")));

        for (int i = 0 ; i < split.size() ; i++){
            if (split.get(i).equals("not") || split.get(i).equals("(not")){
                split.set(i, split.get(i) +" K");
                if (split.get(i+1).charAt(0) != '('){
                    split.set(i+1, "(" +split.get(i+1) + ")");
                }
            }
        }

        for (String str : split){
            result = result + str + " ";
        }
        result = result.replace("( ","(");

        return result;
    }

    private String convertToCWA(String s){
        s = s.replace("("," (");
        s = s.replace(")",") ");
        s = s.trim().replaceAll("\\s{2,}", " ");
        s = s.replace("( ","(");
        s = s.replace(" )",")");

        s = convertOnlyToSome(s);
        s = addOperatorK(s);
        System.out.println("CWA: "+s);
        cwaInputArea.setText(s);
        return s;
    }

    private List<String> parseKConcepts(String input){
        List<String> kConcepts = new ArrayList<>();
        List<Integer> leftBracketsIndexes = new ArrayList<>();

        String subStr;
        int bracketIndex;

        for (int i = 0; i < input.length(); i++){
            if (input.charAt(i) == '(')
                leftBracketsIndexes.add(i);
            else if (input.charAt(i) == ')'){
                bracketIndex = leftBracketsIndexes.get(leftBracketsIndexes.size()-1);
                if (bracketIndex >= 2) {
                    subStr = input.substring(bracketIndex - 2, i + 1);
                    if (bracketIndex - 3 < 0){
                        if ((input.charAt(bracketIndex - 2) == 'K' || input.charAt(bracketIndex - 2) == 'k') && input.charAt(bracketIndex - 1) == ' ')
                            kConcepts.add(subStr);
                    }else {
                        if ((input.charAt(bracketIndex - 2) == 'K' || input.charAt(bracketIndex - 2) == 'k') && input.charAt(bracketIndex - 3) == ' ' && input.charAt(bracketIndex - 1) == ' ')
                            kConcepts.add(subStr);
                    }
                }
                leftBracketsIndexes.remove(leftBracketsIndexes.size()-1);
            }
        }
        return kConcepts;
    }

    private String createKConcepts(List<String> ls , String s){
        String result = s;
        String tmp;
        String tmp1;
        for (int i = 0; i < ls.size(); i++){
            tmp1 = ls.get(i);
            tmp = ls.get(i);
            tmp = tmp.replace(" ("," ");
            tmp = tmp.replace(") "," ");
            tmp = tmp.replace(")"," ");
            tmp = tmp.replace("("," ");
            tmp = tmp.trim().replaceAll("\\s{2,}", " ");
            tmp = tmp.replace(" ","_");
            tmp = tmp.replace("{","");
            tmp = tmp.replace("}","");
            tmp = tmp.replace(",","_");

            result = result.replace(ls.get(i) , tmp);
            System.out.println("Step "+(i*2+1)+": Creating "+ tmp);
            System.out.println("Step "+(i*2+2)+": "+result);

            for (int j = 0 ; j < ls.size() ; j++){
                ls.set(j, ls.get(j).replace(tmp1, tmp));
            }

            doAddClosure(tmp1.substring(2) , tmp);
        }
        System.out.println("Final result:"+result);
        return result;
    }
}