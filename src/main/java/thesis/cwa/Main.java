package thesis.cwa;

import org.coode.dlquery.ResultsList;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.OWLEditorKitFactory;
import org.protege.editor.owl.model.OWLModelManager;
import org.protege.editor.owl.model.inference.*;
import org.protege.editor.owl.ui.clsdescriptioneditor.ExpressionEditor;
import org.protege.editor.owl.ui.clsdescriptioneditor.OWLExpressionChecker;
import org.protege.editor.owl.ui.view.AbstractOWLViewComponent;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.expression.OWLEntityChecker;
import org.semanticweb.owlapi.expression.ShortFormEntityChecker;
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxClassExpressionParser;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.BidirectionalShortFormProvider;
import org.semanticweb.owlapi.util.BidirectionalShortFormProviderAdapter;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class Main extends AbstractOWLViewComponent {



    public static void main(String[] args) throws OWLException {
        String input = "Osoba";
        B b = new B();
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLOntology o = m.loadOntologyFromOntologyDocument(IRI.create("file:/C:/Users/skope/OneDrive/Dokumenty/dp-test-ontology.owl"));
        OWLDataFactory dfactory = m.getOWLDataFactory();


        OWLReasonerFactory rfactory = new NoOpReasonerFactory();
        OWLReasoner reasoner = rfactory.createReasoner(o);

        ShortFormProvider shortFormProvider = new SimpleShortFormProvider();
        Set<OWLOntology> importsClosure = o.getImportsClosure();
        BidirectionalShortFormProvider bidiShortFormProvider = new BidirectionalShortFormProviderAdapter(m, importsClosure, shortFormProvider);
        OWLEntityChecker entityChecker = new ShortFormEntityChecker(bidiShortFormProvider);
        ManchesterOWLSyntaxClassExpressionParser parser = new ManchesterOWLSyntaxClassExpressionParser(dfactory, entityChecker);

        final OWLExpressionChecker<OWLClassExpression> checker = b.get().getOWLExpressionCheckerFactory().getOWLClassExpressionChecker();
        //ExpressionEditor<OWLClassExpression> owlDescriptionEditor = new ExpressionEditor<>(getOWLEditorKit(), checker);;

        OWLClassExpression ex = parser.parse(input);

        Set<OWLNamedIndividual> result = reasoner.getInstances(ex, false).getFlattened();


        input = convertToCWA(input);     ///////////// zobrazit na CWA query *************

        createKConcepts(parseKConcepts(input),input);
    }

    private static String convertOnlyToSome(String s){
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

    private static String addOperatorK (String s){
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

    private static String convertToCWA(String s){
        s = s.replace("("," (");
        s = s.replace(")",") ");
        s = s.trim().replaceAll("\\s{2,}", " ");
        s = s.replace("( ","(");
        s = s.replace(" )",")");

        s = convertOnlyToSome(s);
        s = addOperatorK(s);

        return s;
    }

    private static List<String> parseKConcepts(String input){
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
                    if (input.charAt(bracketIndex - 2) == 'K' && input.charAt(bracketIndex - 3) == ' ' && input.charAt(bracketIndex - 1) == ' ')
                        kConcepts.add(subStr);
                }
                leftBracketsIndexes.remove(leftBracketsIndexes.size()-1);
            }
        }
        return kConcepts;
    }

    private static String createKConcepts(List<String> ls , String s){
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

            result = result.replace(ls.get(i) , tmp);

            for (int j = 0 ; j < ls.size() ; j++){
                ls.set(j, ls.get(j).replace(tmp1, tmp));
            }
            //doAddClosure(tmp1.substring(2) , tmp);
        }

        return result;
    }

    @Override
    protected void initialiseOWLView() throws Exception {

    }

    @Override
    protected void disposeOWLView() {

    }
}
