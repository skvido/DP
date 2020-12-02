package thesis.cwa;

import java.util.HashSet;
import java.util.Set;

import org.protege.editor.owl.model.OWLModelManager;
import org.semanticweb.owlapi.expression.OWLEntityChecker;
import org.semanticweb.owlapi.expression.ShortFormEntityChecker;
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxClassExpressionParser;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLDifferentIndividualsAxiom;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.model.UnknownOWLOntologyException;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.ClassExpressionNotInProfileException;
import org.semanticweb.owlapi.reasoner.FreshEntitiesException;
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.ReasonerInterruptedException;
import org.semanticweb.owlapi.reasoner.TimeOutException;
import org.semanticweb.owlapi.util.BidirectionalShortFormProvider;
import org.semanticweb.owlapi.util.BidirectionalShortFormProviderAdapter;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;

public class CWAManager {

	private final OWLModelManager modelManager;
	private final OWLOntology activeOntology;
	private final OWLDataFactory df;
	private final OWLReasoner reasoner;

	public CWAManager(OWLModelManager modelManager, OWLOntology activeOntology, OWLDataFactory df,
			OWLReasoner reasoner) {
		this.modelManager = modelManager;
		this.activeOntology = activeOntology;
		this.df = df;
		this.reasoner = reasoner;
	}

	public void removeKAxioms(OWLOntology activeOntology, OWLClass owlclass, OWLModelManager modelManager) {
		Set<OWLAxiom> all_axioms = activeOntology.getAxioms();

		for (OWLAxiom ax : all_axioms) {
			try {
				if (ax.getSignature().contains(owlclass)) {
					RemoveAxiom removeAxiom = new RemoveAxiom(activeOntology, ax);
					modelManager.applyChange(removeAxiom);
				}
			} catch (ClassCastException e) {
				System.out.println("ERROR - " + e.getMessage());
			} catch (NullPointerException e) {
				System.out.println("ERROR - " + e.getMessage());
			}
		}
	}

	public Set<OWLNamedIndividual> convertNodeSetToSet(NodeSet<OWLNamedIndividual> instances) {
		Set<OWLNamedIndividual> set_instances = new HashSet<OWLNamedIndividual>();

		try {
			for (Node<OWLNamedIndividual> i : instances) {
				set_instances.add(i.getRepresentativeElement());
			}
		} catch (ClassCastException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (NullPointerException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (UnsupportedOperationException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (IllegalArgumentException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (RuntimeException e) {
			System.out.println("ERROR - " + e.getMessage());
		}

		return set_instances;
	}

	public OWLClass addClass(String name) {
		OWLClass Kclass = df.getOWLClass(IRI.create(name));
		removeKAxioms(activeOntology, Kclass, modelManager);
		OWLDeclarationAxiom declarationAxiomK = df.getOWLDeclarationAxiom(Kclass);
		AddAxiom addDecAxiomK = new AddAxiom(activeOntology, declarationAxiomK);
		modelManager.applyChange(addDecAxiomK);
		return Kclass;
	}

	public void addAssertions(Set<OWLNamedIndividual> instances, OWLClass Kclass) {
		for (OWLNamedIndividual i : instances) {
			OWLClassAssertionAxiom classAssertionK = df.getOWLClassAssertionAxiom(Kclass, i);
			AddAxiom addAxiom = new AddAxiom(activeOntology, classAssertionK);
			modelManager.applyChange(addAxiom);
		}
	}

	public void addClosure(Set<OWLNamedIndividual> instances, ManchesterOWLSyntaxClassExpressionParser parser,
			OWLClass Kclass) {
		String subclassString = "{";

		for (OWLNamedIndividual i : instances) {
			subclassString = subclassString + i.getIRI().getShortForm() + ",";
		}

		try {
			subclassString = subclassString.substring(0, subclassString.length() - 1);
			subclassString = subclassString + "}";

			System.out.println(subclassString);

			if (!subclassString.equals("}")) {
				OWLClassExpression subclassExpression = parser.parse(subclassString);
				OWLAxiom axiomKsubclass = df.getOWLSubClassOfAxiom(Kclass, subclassExpression);
				AddAxiom addAxiomsubclass = new AddAxiom(activeOntology, axiomKsubclass);
				modelManager.applyChange(addAxiomsubclass);
			} else {
				OWLAxiom axiomKsubclass = df.getOWLSubClassOfAxiom(Kclass, df.getOWLNothing());
				AddAxiom addAxiomsubclass = new AddAxiom(activeOntology, axiomKsubclass);
				modelManager.applyChange(addAxiomsubclass);
			}
		} catch (IndexOutOfBoundsException e) {
			System.out.println("ERROR - " + e.getMessage());
		}
	}

	public void createNot(OWLClass lastSelectedClass) {
		try {
			reasoner.flush();
			reasoner.precomputeInferences();
			reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);

			NodeSet<OWLClass> superclasses = reasoner.getSuperClasses(lastSelectedClass, true);
			String lastSelectedClassPrefix = lastSelectedClass.getIRI().getNamespace();

			ShortFormProvider shortFormProvider = new SimpleShortFormProvider();
			Set<OWLOntology> importsClosure = activeOntology.getImportsClosure();
			BidirectionalShortFormProvider bidiShortFormProvider = new BidirectionalShortFormProviderAdapter(
					activeOntology.getOWLOntologyManager(), importsClosure, shortFormProvider);
			OWLEntityChecker entityChecker = new ShortFormEntityChecker(bidiShortFormProvider);
			ManchesterOWLSyntaxClassExpressionParser parser = new ManchesterOWLSyntaxClassExpressionParser(df,
					entityChecker);

			String classExpressionString = "not " + lastSelectedClass.getIRI().getShortForm();
			OWLClassExpression classExpression = parser.parse(classExpressionString);

			Set<OWLNamedIndividual> instances_of_KNotClass = convertNodeSetToSet(
					reasoner.getInstances(classExpression, false));

			OWLClass KNotClass = addClass(lastSelectedClassPrefix + "KNot" + lastSelectedClass.getIRI().getShortForm());

			for (Node<OWLClass> c : superclasses) {
				if (c.getRepresentativeElement().getIRI().getShortForm().equals("Thing"))
					continue;
				OWLAxiom axiomKNot = df.getOWLSubClassOfAxiom(KNotClass, c.getRepresentativeElement());
				AddAxiom addAxiomKNot = new AddAxiom(activeOntology, axiomKNot);
				modelManager.applyChange(addAxiomKNot);
			}

			addAssertions(instances_of_KNotClass, KNotClass);

			OWLDifferentIndividualsAxiom allIndivDiffax = df
					.getOWLDifferentIndividualsAxiom(activeOntology.getIndividualsInSignature(Imports.EXCLUDED));
			AddAxiom addAllIndivDiffax = new AddAxiom(activeOntology, allIndivDiffax);
			modelManager.applyChange(addAllIndivDiffax);

			addClosure(instances_of_KNotClass, parser, KNotClass);
		} catch (InconsistentOntologyException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (TimeOutException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (ReasonerInterruptedException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (ClassExpressionNotInProfileException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (FreshEntitiesException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (UnknownOWLOntologyException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (RuntimeException e) {
			System.out.println("ERROR - " + e.getMessage());
		}

	}

	public void create(OWLClass lastSelectedClass) {
		try {
			reasoner.flush();
			reasoner.precomputeInferences();
			reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);

			NodeSet<OWLClass> superclasses = reasoner.getSuperClasses(lastSelectedClass, true);
			String lastSelectedClassPrefix = lastSelectedClass.getIRI().getNamespace();

			ShortFormProvider shortFormProvider = new SimpleShortFormProvider();
			Set<OWLOntology> importsClosure = activeOntology.getImportsClosure();
			BidirectionalShortFormProvider bidiShortFormProvider = new BidirectionalShortFormProviderAdapter(
					activeOntology.getOWLOntologyManager(), importsClosure, shortFormProvider);
			OWLEntityChecker entityChecker = new ShortFormEntityChecker(bidiShortFormProvider);
			ManchesterOWLSyntaxClassExpressionParser parser = new ManchesterOWLSyntaxClassExpressionParser(df,
					entityChecker);

			Set<OWLNamedIndividual> instances_of_KClass = reasoner.getInstances(lastSelectedClass, false)
					.getFlattened();

			OWLClass Kclass = addClass(lastSelectedClassPrefix + "K" + lastSelectedClass.getIRI().getShortForm());

			for (Node<OWLClass> c : superclasses) {
				if (c.getRepresentativeElement().getIRI().getShortForm().equals("Thing"))
					continue;
				OWLAxiom axiomK = df.getOWLSubClassOfAxiom(Kclass, c.getRepresentativeElement());
				AddAxiom addAxiomK = new AddAxiom(activeOntology, axiomK);
				modelManager.applyChange(addAxiomK);
			}

			addAssertions(instances_of_KClass, Kclass);

			OWLDifferentIndividualsAxiom allIndivDiffax = df
					.getOWLDifferentIndividualsAxiom(activeOntology.getIndividualsInSignature(Imports.EXCLUDED));
			AddAxiom addAllIndivDiffax = new AddAxiom(activeOntology, allIndivDiffax);
			modelManager.applyChange(addAllIndivDiffax);

			addClosure(instances_of_KClass, parser, Kclass);

			reasoner.flush();
		} catch (InconsistentOntologyException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (TimeOutException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (ReasonerInterruptedException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (ClassExpressionNotInProfileException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (FreshEntitiesException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (UnknownOWLOntologyException e) {
			System.out.println("ERROR - " + e.getMessage());
		} catch (RuntimeException e) {
			System.out.println("ERROR - " + e.getMessage());
		}
	}
}
