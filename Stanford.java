import java.util.List;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.StringReader;

import spark.Spark;

import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

public class Stanford {

  public static void main(String[] args) {

    Spark.port(Integer.valueOf(System.getenv("STANFORD_PORT")));
    Spark.staticFileLocation("/public");

    String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
    LexicalizedParser parser = LexicalizedParser.loadModel(parserModel);
    String taggerPath = "edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger";
    MaxentTagger tagger = new MaxentTagger(taggerPath);

    Spark.get("/tree/:text", (request, response) -> {
//        String sentence = request.params(":text").toLowerCase();
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

        //Just log for now, but future use as alternate parse strategies
        DocumentPreprocessor tokenizer = new DocumentPreprocessor(new StringReader(sentence));
        for (List<HasWord> tokenized : tokenizer) {
            System.out.println("tokenizer words " + tokenized);

            ParserQuery parserQuery = parser.parserQuery();
            parserQuery.parse(tokenized);
            System.out.println("best parse " + parserQuery.getBestParse());
            System.out.println("alternative parses " + parserQuery.getBestPCFGParses());
            System.out.println("best PCFG parse " + parserQuery.getBestPCFGParse());

            List<TaggedWord> tagged = tagger.tagSentence(tokenized);
            System.out.println("tagged words " + tagged);
            parserQuery.parse(tagged);
            System.out.println("tagged parse " + parserQuery.getBestParse());
        }

        return (json);
    });

  }

}
