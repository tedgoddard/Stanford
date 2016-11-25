import java.util.List;
import java.net.URI;
import java.net.URISyntaxException;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;

import spark.Spark;

public class Main {

  public static void main(String[] args) {

    Spark.port(Integer.valueOf(System.getenv("STANFORD_PORT")));
    Spark.staticFileLocation("/public");

    String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
    LexicalizedParser parser = LexicalizedParser.loadModel(parserModel);

    Spark.get("/tree/:text", (request, response) -> {
        String sentence = request.params(":text");
        System.out.println("will parse: " + sentence);

        Tree tree = parser.parse(sentence);
        String treeString = tree.toString();
        TreebankLanguagePack treeLanguagePack = new PennTreebankLanguagePack();
        GrammaticalStructureFactory structureFactory = treeLanguagePack.grammaticalStructureFactory();
        GrammaticalStructure grammaticalStructure = structureFactory.newGrammaticalStructure(tree);
        List<TypedDependency> typedDependencies = grammaticalStructure.typedDependenciesCCprocessed();
        String dependenciesString = typedDependencies.toString();
        String json = "{" +
            "\"tree\": " + "\"" + treeString + "\", " +
            "\"dependencies\":" + "\"" + dependenciesString + "\"" +
        "}";
        return (json);
    });

  }

}
