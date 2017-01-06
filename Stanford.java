import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.StringReader;

import spark.Spark;
import com.google.gson.Gson;

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

    static String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
    static LexicalizedParser parser = LexicalizedParser.loadModel(parserModel);
    static String taggerPath = "edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger";
    static MaxentTagger tagger = new MaxentTagger(taggerPath);

    public static void main(String[] args) {

        Spark.port(Integer.valueOf(System.getenv("STANFORD_PORT")));
        Spark.staticFileLocation("/public");

        Spark.get("/tree/:text", (request, response) -> {
            Gson gson = new Gson();
            String sentence = request.params(":text");
            ParseResult parseResult = parseText(sentence);

            String json = gson.toJson(parseResult);
            System.out.println("parse result " + json);

            return (json);
        });

        Spark.post("/tree/", (request, response) -> {
            Gson gson = new Gson();
            String body = request.body();
            ParseRequest parseRequest = gson.fromJson(body, ParseRequest.class);
            System.out.println(parseRequest);

            String sentence = parseRequest.text;
            ParseResult parseResult = parseText(sentence);
            parseResult.strategy = "PCFG";
            ParseResult lowerResult = parseText(sentence.toLowerCase());
            parseResult.strategy = "PCFG.lowercase";
            ParseResult[] alternates = parseTextAlternates(sentence);

            ParseResponse parseResponse = new ParseResponse();
            parseResponse.tree = parseResult.tree;
            parseResponse.dependencies = parseResult.dependencies;

            ParseResult[] simpleAlternates = new ParseResult[] { parseResult, lowerResult };
            ParseResult[] otherAlternates = parseTextAlternates(sentence);
            parseResponse.alternates = Stream.concat(
                    Arrays.stream(simpleAlternates),
                    Arrays.stream(otherAlternates)).toArray(ParseResult[]::new);
            String json = gson.toJson(parseResponse);
            System.out.println(json);

            return (json);
        });

    }

    public static ParseResult parseText(String text) {
        ParseResult parseResult = new ParseResult();
        System.out.println("parseText will parse: " + text);

        Tree tree = parser.parse(text);
        String treeString = tree.toString();
        TreebankLanguagePack treeLanguagePack = new PennTreebankLanguagePack();
        GrammaticalStructureFactory structureFactory = treeLanguagePack.grammaticalStructureFactory();
        GrammaticalStructure grammaticalStructure = structureFactory.newGrammaticalStructure(tree);
        List<TypedDependency> typedDependencies = grammaticalStructure.typedDependenciesCCprocessed();
        String dependenciesString = typedDependencies.toString();
        parseResult.tree = treeString;
        parseResult.dependencies = dependenciesString;

        return parseResult;
    }

    public static ParseResult[] parseTextAlternates(String text) {
        ParseResult parseResult = new ParseResult();
        DocumentPreprocessor tokenizer = new DocumentPreprocessor(new StringReader(text));
        //TODO: handle multiple sentences via this loop
        for (List<HasWord> tokenized: tokenizer) {
            System.out.println("tokenizer words " + tokenized);

            ParserQuery parserQuery = parser.parserQuery();
            parserQuery.parse(tokenized);
            //Need to determine how to flatten these alternate parse trees before returning them
            System.out.println("best parse " + parserQuery.getBestParse());
            System.out.println("alternative parses " + parserQuery.getBestPCFGParses());
            System.out.println("best PCFG parse " + parserQuery.getBestPCFGParse());

            List<TaggedWord> tagged = tagger.tagSentence(tokenized);
            System.out.println("tagged words " + tagged);
            parserQuery.parse(tagged);
            System.out.println("tagged parse " + parserQuery.getBestParse());
            parseResult.tree = parserQuery.getBestParse().toString();
            parseResult.strategy = "PCFG.maxent";
        }

        return new ParseResult[] { parseResult };
    }
}

class ParseRequest {
    String text = "";

    ParseRequest() { }
}

class ParseResult {
    String tree = "";
    String dependencies = "";
    String strategy = "";

    ParseResult() { }
}

class ParseResponse {
    String tree = "";
    String dependencies = "";
    ParseResult[] alternates = {};

    ParseResponse() { }
}
